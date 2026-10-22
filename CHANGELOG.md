# Changelog

All notable changes to this project will be documented here.

## Unreleased

### Added

- Cross-platform GitHub CI with Java 17/21/25 checks, native package smoke tests, dependency review and CodeQL
- JaCoCo coverage reports and per-package regression gates, generated PDF fixtures and packaged CLI integration tests
- Full-window Swing smoke test with isolated preferences, plus release-tool regression tests
- Versioned draft GitHub Releases for Windows, Apple Silicon/Intel macOS and Linux, plus a portable JAR
- SHA-256 download checksums, CycloneDX dependency inventory, GitHub provenance attestations and categorized release notes
- Checksum-verified Maven Wrapper, release preparation/validation tooling and a coverage audit
- Bundled native command-line launcher and Linux application packaging

- New responsive desktop interface with searchable metadata and a text preview
- Drag-and-drop PDF opening, keyboard shortcuts, and remembered preferences
- Light and dark themes powered by FlatLaf
- Atomic structured JSON export with overwrite confirmation
- Background PDF analysis and user-facing error handling
- Continuous integration for Java 17
- Self-contained macOS `.app`, Mac App Store `.pkg`, Windows `.exe`, and Microsoft Store MSIX packaging scripts
- Generated native artwork and Store manifest/entitlement templates

### Changed

- Updated Jackson to 2.22.3 to address the repository's existing dependency security alerts
- CLI invalid paths now produce a controlled error and exit status instead of an uncaught exception
- Native packaging runs full verification before building and reuses verified app images on Windows

- Raised the runtime baseline from Java 8 to Java 17
- Updated Apache Tika, Jackson, logging, and test dependencies
- Replaced the IntelliJ form-designer runtime with a maintainable programmatic Swing UI
- Limited extracted text previews to protect application memory on unusually large files
- macOS file selection now uses native dialogs so user-selected files work correctly with App Sandbox

### Removed

- Placeholder JAR-signing credentials and machine-specific keystore path
- Silent `printStackTrace` error handling
