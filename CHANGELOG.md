# Changelog

All notable changes to this project will be documented here.

## Unreleased

### Added

- New responsive desktop interface with searchable metadata and a text preview
- Drag-and-drop PDF opening, keyboard shortcuts, and remembered preferences
- Light and dark themes powered by FlatLaf
- Atomic structured JSON export with overwrite confirmation
- Background PDF analysis and user-facing error handling
- Continuous integration for Java 17
- Self-contained macOS `.app`, Mac App Store `.pkg`, Windows `.exe`, and Microsoft Store MSIX packaging scripts
- Generated native artwork and Store manifest/entitlement templates

### Changed

- Migrated to Apache Tika 4.1.0, preserving embedded PDF text and adopting its canonical metadata key names; see `docs/TIKA-4-MIGRATION.md`
- Raised the runtime baseline from Java 8 to Java 17
- Updated Apache Tika, Jackson, logging, and test dependencies
- Replaced the IntelliJ form-designer runtime with a maintainable programmatic Swing UI
- Limited extracted text previews to protect application memory on unusually large files
- macOS file selection now uses native dialogs so user-selected files work correctly with App Sandbox

### Removed

- Placeholder JAR-signing credentials and machine-specific keystore path
- Silent `printStackTrace` error handling
