# PDF Meta Viewer

[![CI](https://github.com/Alexxfromgit/PDF-Meta-Viewer/actions/workflows/build.yml/badge.svg)](https://github.com/Alexxfromgit/PDF-Meta-Viewer/actions/workflows/build.yml) [![CodeQL](https://github.com/Alexxfromgit/PDF-Meta-Viewer/actions/workflows/codeql.yml/badge.svg)](https://github.com/Alexxfromgit/PDF-Meta-Viewer/actions/workflows/codeql.yml)

A focused, cross-platform desktop utility for inspecting PDF metadata and exporting it as clean JSON.

PDF Meta Viewer keeps document processing local. Open or drop a PDF, search its metadata, preview extracted text, and export a structured metadata file without uploading the document anywhere.

## Highlights

- Automatic background analysis — the interface stays responsive while a PDF is parsed
- Searchable, sortable metadata table with multi-row copy
- Extracted text preview with a safe two-million-character display limit
- Drag and drop, macOS-native file dialogs, overwrite protection, and sensible export names
- Light and dark appearance with the preference remembered between launches
- Keyboard shortcuts and accessible component descriptions
- Friendly validation and error messages with structured diagnostic logging
- Atomic, pretty-printed JSON exports

## Downloads

Download published builds from [GitHub Releases](https://github.com/Alexxfromgit/PDF-Meta-Viewer/releases). Releases provide a Windows x64 installer, separate Apple Silicon and Intel Mac ZIPs, a Linux x64 archive, and a portable JAR. Native packages include Java; the JAR requires Java 17+. Choose the file matching your OS and processor, and read the release's installation and signing notes.

Each release includes SHA-256 checksums, a CycloneDX dependency inventory (SBOM), and GitHub build provenance. CI prepares releases as drafts for the maintainer to review before publishing. Until signing credentials are configured, Windows installers are unsigned and Mac apps are ad-hoc signed without notarization.

## Requirements for building

- A full JDK 17 or newer
- The included Maven Wrapper downloads the pinned Maven version automatically

## Run from source

```bash
./mvnw clean verify
java -jar target/pdf-meta-viewer.jar
```

The first command runs unit and packaged CLI integration tests, checks coverage, and creates an executable JAR containing the app dependencies. On Windows, use `mvnw.cmd` instead of `./mvnw`.
Pass a PDF path after the JAR name to open it immediately.

## Native packages

The release scripts build self-contained applications with a bundled Java runtime:

```bash
# Run on macOS
./scripts/build-macos.sh app

# Run on Linux (also requires Python 3.11+)
./scripts/build-linux.sh
```

```powershell
# Run on Windows; requires WiX Toolset 3.x
.\scripts\build-windows.ps1 -Target exe
```

Mac App Store `.pkg` and Microsoft Store `.msix`/`.msixupload` modes are also included. Native packages must be built on their target operating system. See [RELEASING.md](RELEASING.md) for signing assets, Partner Center/App Store Connect identity values, exact commands, validation, and submission steps.

## Use the app

1. Select **Open PDF**, press `Ctrl/Command + O`, or drop one PDF onto the window.
2. Search and sort the metadata, or switch to **Text preview**.
3. Select one or more rows and choose **Copy selected** when you need values on the clipboard.
4. Choose **Export JSON** or press `Ctrl/Command + E` to save the current metadata.

`Ctrl/Command + F` focuses metadata search. `Escape` clears the current search.

An export has a stable, explicit shape:

```json
{
  "source": "example.pdf",
  "metadata": {
    "Content-Type": "application/pdf",
    "dc:title": "Example"
  }
}
```

## Command-line use

The packaged app opens the graphical interface. For scripting, build the project and run the CLI entry point with the shaded JAR on the classpath:

```bash
java -cp target/pdf-meta-viewer.jar program.Main <input.pdf> [output.json]
```

With no output path it prints tab-separated metadata. With an output path it writes the same structured JSON produced by the app.

## Development

```bash
./mvnw test
./mvnw test -Dgroups=smoke
./mvnw clean verify
python3 -m unittest discover -s scripts/tests -v
```

CI verifies Java 17 on Linux, Windows, and both Mac architectures, plus Java 21 and 25 on Linux. It builds and smoke-tests native packages, uploads test/coverage reports, and reviews dependency changes. CodeQL analyzes Java on PRs, branch pushes and a weekly schedule. Run `./mvnw -Pdesktop-smoke clean verify` with a display to include the full-window Swing smoke test (Linux CI uses Xvfb).

See [docs/TESTING.md](docs/TESTING.md) for the initial coverage audit, enforced thresholds, results, and remaining gaps. Open `target/site/jacoco/index.html` after verification for local coverage.

### Architecture

```text
MetaViewerForm
  ├─ PDFExtractor          validates and parses a PDF with Apache Tika
  ├─ DocumentRecord        immutable extraction result
  ├─ MetadataTableModel    searchable table presentation
  └─ DocumentRepository   atomically writes structured JSON
```

The UI is built with standard Swing and FlatLaf. PDF parsing uses Apache Tika's dedicated PDF module; JSON serialization uses Jackson.

## Release

See [RELEASING.md](RELEASING.md) for owner, packaging, signing, validation, and Store submission steps.
