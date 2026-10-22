# PDF Meta Viewer

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

## Requirements

- Java 17 or newer
- Maven 3.9 or newer (for building from source)

## Run from source

```bash
mvn clean package
java -jar target/pdf-meta-viewer.jar
```

The first command runs the full test suite and creates a self-contained executable JAR.
Pass a PDF path after the JAR name to open it immediately.

## Native packages

The release scripts build self-contained applications with a bundled Java runtime:

```bash
# Run on macOS
./scripts/build-macos.sh app
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

Metadata uses Apache Tika 4's native key names. Parser-specific keys can differ from older exports (for example, `pdf:PDFVersion` becomes `pdf:pdf-version`); the JSON structure stays the same. See [Tika migration notes](docs/TIKA-4-MIGRATION.md).

## Command-line use

The packaged app opens the graphical interface. For scripting, build the project and run the CLI entry point with the shaded JAR on the classpath:

```bash
java -cp target/pdf-meta-viewer.jar program.Main <input.pdf> [output.json]
```

With no output path it prints tab-separated metadata. With an output path it writes the same structured JSON produced by the app.

## Development

```bash
mvn test
mvn test -Dgroups=smoke
mvn clean verify
```

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
