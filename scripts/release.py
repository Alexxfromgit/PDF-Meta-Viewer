#!/usr/bin/env python3
"""Version, changelog and release asset checks; Python standard library only."""
import argparse
from datetime import date
import hashlib
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
VERSION_PATTERN = r"([1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)"


def validate_version(value):
    match = re.fullmatch(VERSION_PATTERN, value)
    if not match:
        raise ValueError("Use a stable version MAJOR.MINOR.PATCH, with MAJOR >= 1 and no leading zeros.")
    parts = tuple(map(int, match.groups()))
    if parts[0] > 255 or parts[1] > 255 or parts[2] > 65535:
        raise ValueError("Windows installer versions require MAJOR/MINOR <= 255 and PATCH <= 65535.")
    return parts


def project_version(root=ROOT):
    return ET.parse(root / "pom.xml").getroot().find("m:version", NS).text


def package_version(root=ROOT):
    value = project_version(root).removesuffix("-SNAPSHOT")
    validate_version(value)
    return value


def changelog_notes(version, root=ROOT):
    text = (root / "CHANGELOG.md").read_text(encoding="utf-8")
    match = re.search(r"^## \[?" + re.escape(version) + r"\]? - (\d{4}-\d{2}-\d{2})\s*\n(.*?)(?=^## |\Z)", text, re.M | re.S)
    if not match or not match.group(2).strip():
        raise ValueError(f"CHANGELOG.md needs a nonempty dated section: ## [{version}] - YYYY-MM-DD")
    date.fromisoformat(match.group(1))
    return match.group(2).strip()


def check(tag, root=ROOT):
    version = tag.removeprefix("v")
    if tag != "v" + version:
        raise ValueError("Release tags must start with v.")
    validate_version(version)
    if project_version(root) != version:
        raise ValueError(f"Tag {tag} does not match pom.xml version {project_version(root)}.")
    changelog_notes(version, root)
    return version


def prepare(version, root=ROOT, today=None):
    desired = validate_version(version)
    current = project_version(root)
    current_parts = validate_version(current.removesuffix("-SNAPSHOT"))
    if desired < current_parts or (desired == current_parts and not current.endswith("-SNAPSHOT")):
        raise ValueError("The release version must advance the current release (or finalize its SNAPSHOT).")
    changelog = root / "CHANGELOG.md"
    text = changelog.read_text(encoding="utf-8")
    match = re.search(r"^## (?:\[Unreleased\]|Unreleased)\s*\n(.*?)(?=^## |\Z)", text, re.M | re.S)
    if not match or not match.group(1).strip():
        raise ValueError("Add release notes under ## Unreleased before preparing a release.")
    if re.search(r"^## \[?" + re.escape(version) + r"\]?(?:\s|$)", text, re.M):
        raise ValueError("That version already exists in CHANGELOG.md.")
    release_date = today or date.today()
    replacement = f"## Unreleased\n\n## [{version}] - {release_date.isoformat()}\n\n{match.group(1).strip()}\n\n"
    new_changelog = text[:match.start()] + replacement + text[match.end():]
    pom = root / "pom.xml"
    pom_text = pom.read_text(encoding="utf-8")
    new_pom = pom_text.replace(f"<version>{current}</version>", f"<version>{version}</version>", 1)
    new_pom = re.sub(r"<project.build.outputTimestamp>.*?</project.build.outputTimestamp>",
                     f"<project.build.outputTimestamp>{release_date.isoformat()}T00:00:00Z</project.build.outputTimestamp>", new_pom)
    pom.write_text(new_pom, encoding="utf-8")
    changelog.write_text(new_changelog, encoding="utf-8")
    return version


def expected_assets(version):
    prefix = f"PDF-Meta-Viewer-{version}"
    return {
        f"{prefix}.jar", f"{prefix}-sbom.cdx.json",
        f"{prefix}-macos-arm64.zip", f"{prefix}-macos-x64.zip",
        f"{prefix}-windows-x64.exe", f"{prefix}-linux-x64.tar.gz",
    }


def checksums(version, directory):
    validate_version(version)
    directory = Path(directory)
    actual = {path.name for path in directory.iterdir() if path.is_file() and path.name != "SHA256SUMS.txt"}
    expected = expected_assets(version)
    if actual != expected:
        raise ValueError(f"Unexpected release assets: missing={sorted(expected - actual)}, extra={sorted(actual - expected)}")
    lines = []
    for name in sorted(expected):
        path = directory / name
        if path.is_symlink() or path.stat().st_size == 0:
            raise ValueError(f"Release asset is empty or a symlink: {name}")
        with path.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        lines.append(f"{digest}  {name}\n")
    (directory / "SHA256SUMS.txt").write_text("".join(lines), encoding="utf-8")


def notes(version, root=ROOT):
    validate_version(version)
    prefix = f"PDF-Meta-Viewer-{version}"
    return f"""{changelog_notes(version, root)}

## Downloads

| Platform | Asset | Runtime |
| --- | --- | --- |
| Windows x64 | `{prefix}-windows-x64.exe` | Included |
| macOS Apple Silicon | `{prefix}-macos-arm64.zip` | Included |
| macOS Intel | `{prefix}-macos-x64.zip` | Included |
| Linux x64 | `{prefix}-linux-x64.tar.gz` | Included |
| Any OS with Java 17+ | `{prefix}.jar` | Install Java separately |

The Windows installer is unsigned. The Mac apps are ad-hoc signed and **not notarized**; downloaded apps may be blocked by Gatekeeper. These artifacts do not carry a verified publisher identity. Developer ID signing/notarization and Windows Authenticode signing require publisher certificates before broad distribution. Store submissions use the separate documented scripts.

Extract the Mac ZIP and move `PDF Meta Viewer.app` into Applications. Extract the Linux archive and run `PDF Meta Viewer/bin/PDF Meta Viewer`. For the JAR, use `java -jar {prefix}.jar`.

`SHA256SUMS.txt` contains hashes for all downloads; verify with `sha256sum --check SHA256SUMS.txt` (Linux) or `shasum -a 256 --check SHA256SUMS.txt` (macOS) after downloading all listed files. On Windows, compare `Get-FileHash <download> -Algorithm SHA256` with its entry. The CycloneDX SBOM lists bundled dependencies and their licenses.

GitHub build provenance can be checked with `gh attestation verify <download> --repo Alexxfromgit/PDF-Meta-Viewer`.
"""


def coverage_summary(path):
    report = ET.parse(path).getroot()
    lines = ["### Test coverage", "", "| Area | Lines | Branches |", "| --- | --- | --- |"]
    for element in [report, *report.findall("package")]:
        values = {}
        for counter in element.findall("counter"):
            covered, missed = int(counter.get("covered")), int(counter.get("missed"))
            total = covered + missed
            values[counter.get("type")] = f"{covered}/{total} ({covered / total:.1%})" if total else "N/A"
        lines.append(f"| {element.get('name')} | {values.get('LINE', 'N/A')} | {values.get('BRANCH', 'N/A')} |")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("version")
    sub.add_parser("package-version")
    sub.add_parser("prepare").add_argument("version")
    sub.add_parser("check").add_argument("tag")
    sub.add_parser("notes").add_argument("version")
    checksum_parser = sub.add_parser("checksums")
    checksum_parser.add_argument("version")
    checksum_parser.add_argument("directory", type=Path)
    sub.add_parser("coverage").add_argument("report", type=Path)
    args = parser.parse_args()
    try:
        if args.command == "version":
            print(project_version())
        elif args.command == "package-version":
            print(package_version())
        elif args.command == "prepare":
            print(prepare(args.version))
        elif args.command == "check":
            print(check(args.tag))
        elif args.command == "notes":
            print(notes(args.version))
        elif args.command == "checksums":
            checksums(args.version, args.directory)
        elif args.command == "coverage":
            print(coverage_summary(args.report), end="")
    except (ValueError, OSError, ET.ParseError) as error:
        parser.exit(1, f"Error: {error}\n")


if __name__ == "__main__":
    main()
