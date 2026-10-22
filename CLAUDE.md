# CLAUDE.md

## Build and run

```bash
./mvnw clean verify
java -jar target/pdf-meta-viewer.jar

./mvnw test
./mvnw test -Dgroups=smoke
./mvnw clean verify
```

The project requires Java 17+; the wrapper pins Maven 3.9.16. The shaded executable JAR uses `gui.MetaViewerForm` as its main class.

## Architecture

This is a Swing desktop application that extracts and displays local PDF metadata with Apache Tika and exports structured JSON with Jackson.

1. `MetaViewerForm` accepts a chosen or dropped PDF and starts a `SwingWorker`.
2. `PDFExtractor` validates and parses the file into an immutable `DocumentRecord`.
3. `MetadataTableModel` presents searchable, sortable metadata.
4. `DocumentRepository` pretty-prints and atomically replaces JSON exports.

Important details:

- Keep all long-running file work off the Swing event dispatch thread.
- Create parser state per extraction; Tika parser handlers are not reused.
- The text preview is intentionally bounded at two million characters.
- UI preferences use `java.util.prefs.Preferences`.
- The interface is programmatic Swing; there is no IntelliJ `.form` runtime.
- Never add signing credentials or machine-specific keystore paths to the POM.

## Tests

TestNG and AssertJ cover configuration, extraction, validation, JSON formatting, and file export. The sample PDF is under `src/test/resources`.

Coverage audit and remaining gaps are in `docs/TESTING.md`. `./mvnw -Pdesktop-smoke clean verify` adds a GUI smoke test when a display is available. Release automation and versioning are documented in `RELEASING.md`; release tags must match a stable POM version and dated changelog section.
