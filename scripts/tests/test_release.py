import importlib.util
import hashlib
from datetime import date
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("release", Path(__file__).resolve().parents[1] / "release.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "pom.xml").write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0">
<version>1.0.0-SNAPSHOT</version><properties>
<project.build.outputTimestamp>2026-08-09T00:00:00Z</project.build.outputTimestamp>
</properties><dependency><version>9.9.9</version></dependency></project>''', encoding="utf-8")
        (self.root / "CHANGELOG.md").write_text("# Changelog\n\n## Unreleased\n\n### Added\n\n- Useful feature\n", encoding="utf-8")

    def test_platform_compatible_versions(self):
        for value in ("1.0.0", "255.255.65535", "2.12.45"):
            with self.subTest(value=value):
                release.validate_version(value)
        for value in ("0.1.0", "v1.0.0", "1.0", "01.0.0", "1.0.0-rc.1", "256.0.0", "1.256.0", "1.0.65536", "1.0.0\n", "1.0.0;echo bad"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                release.validate_version(value)

    def test_snapshot_package_version(self):
        self.assertEqual(release.package_version(self.root), "1.0.0")

    def test_prepare_updates_only_project_version_timestamp_and_changelog(self):
        release.prepare("1.0.0", self.root, date(2026, 10, 22))
        self.assertEqual(release.check("v1.0.0", self.root), "1.0.0")
        pom = (self.root / "pom.xml").read_text()
        self.assertIn("<version>9.9.9</version>", pom)
        self.assertIn("2026-10-22T00:00:00Z", pom)
        self.assertIn("## Unreleased\n\n## [1.0.0] - 2026-10-22", (self.root / "CHANGELOG.md").read_text())
        self.assertIn("Useful feature", release.changelog_notes("1.0.0", self.root))

    def test_prepare_rejects_missing_notes_without_mutating_pom(self):
        (self.root / "CHANGELOG.md").write_text("## Unreleased\n\n## [0.9.0] - 2026-01-01\n\n- Old notes\n")
        before = (self.root / "pom.xml").read_bytes()
        with self.assertRaises(ValueError):
            release.prepare("1.0.0", self.root)
        self.assertEqual((self.root / "pom.xml").read_bytes(), before)

    def test_prepare_rejects_duplicate_and_older_versions(self):
        release.prepare("2.0.0", self.root)
        for version in ("1.0.0", "2.0.0"):
            with self.subTest(version=version), self.assertRaises(ValueError):
                release.prepare(version, self.root)

    def test_check_rejects_mismatch_snapshot_and_invalid_tags(self):
        for tag in ("v1.0.0", "1.0.0", "v1.0.0-SNAPSHOT", "v0.1.0"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                release.check(tag, self.root)
        release.prepare("1.0.0", self.root)
        with self.assertRaises(ValueError):
            release.check("v1.0.1", self.root)

    def test_check_requires_nonempty_dated_notes(self):
        release.prepare("1.0.0", self.root)
        for content in ("## [1.0.0]\n- Notes", "## [1.0.0] - 2026-10-22\n\n", "## [1.0.0] - 2026-99-99\n- Notes"):
            (self.root / "CHANGELOG.md").write_text(content)
            with self.subTest(content=content), self.assertRaises(ValueError):
                release.check("v1.0.0", self.root)

    def test_prepare_preserves_prior_releases(self):
        changelog = self.root / "CHANGELOG.md"
        old = "## [0.9.0] - 2026-01-01\n\n- Old feature\n"
        changelog.write_text(changelog.read_text() + "\n" + old)
        release.prepare("1.0.0", self.root)
        self.assertTrue(changelog.read_text().endswith(old))

    def test_checksums_require_all_and_only_expected_assets(self):
        assets = self.root / "assets"
        assets.mkdir()
        with self.assertRaises(ValueError):
            release.checksums("1.0.0", assets)
        for name in release.expected_assets("1.0.0"):
            (assets / name).write_bytes(b"artifact bytes")
        release.checksums("1.0.0", assets)
        before = (assets / "SHA256SUMS.txt").read_bytes()
        digest = hashlib.sha256(b"artifact bytes").hexdigest()
        self.assertEqual(len(before.decode().splitlines()), 6)
        self.assertTrue(all(line.startswith(digest) for line in before.decode().splitlines()))
        release.checksums("1.0.0", assets)
        self.assertEqual((assets / "SHA256SUMS.txt").read_bytes(), before)
        (assets / "unexpected.zip").write_bytes(b"unexpected")
        with self.assertRaises(ValueError):
            release.checksums("1.0.0", assets)

    def test_checksums_reject_empty_assets(self):
        for name in release.expected_assets("1.0.0"):
            (self.root / name).touch()
        # Use a clean asset directory rather than the POM/changelog root.
        assets = self.root / "assets"
        assets.mkdir()
        for name in release.expected_assets("1.0.0"):
            (assets / name).touch()
        with self.assertRaisesRegex(ValueError, "empty"):
            release.checksums("1.0.0", assets)

    def test_notes_list_assets_and_distribution_limitations(self):
        release.prepare("1.0.0", self.root)
        notes = release.notes("1.0.0", self.root)
        for suffix in ("windows-x64.exe", "macos-arm64.zip", "macos-x64.zip", "linux-x64.tar.gz", ".jar"):
            self.assertIn(suffix, notes)
        self.assertIn("not notarized", notes)
        self.assertIn("unsigned", notes)
        self.assertIn("Useful feature", notes)


if __name__ == "__main__":
    unittest.main()
