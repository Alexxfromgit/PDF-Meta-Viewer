# Tika 4 migration

## Scope and branch boundary

Implement Dependabot #18's exact target, Tika 4.1.0, on `feature/tika-4-migration`, based on master `358fdd5a1bf6596a1d7c0ac64a77cdc873a861c2`. Keep #18 open and leave #21 (`feature/desktop-ci-cd`, initially `a78c1856ac2f414213bdf4f6f3b798b641aacd24`) unchanged. Do not merge either branch as part of this work.

## Implementation plan

1. Establish Tika 3.3.2 behavior with generated PDF fixtures: document metadata/text, blank pages, password protection, damaged input, embedded PDF text, preview truncation, and independent concurrent extractions.
2. Upgrade both Tika modules together. Open a file-backed `TikaInputStream` in try-with-resources and explicitly supply parser/detector context for embedded documents. Keep Java 17, per-extraction state, the two-million-character preview bound, existing error handling and the JSON envelope.
3. Verify Tika 4's canonical metadata names. Record observable key changes rather than silently inventing compatibility aliases. Keep Dublin Core fields and `Content-Type` stable; verify JSON with and without content.
4. Run the standalone suite, packaged CLI checks, dependency inspection and native macOS packaging. Obtain CI results for the feature independently of #21.
5. Export the completed implementation patch and apply it only to a temporary copy of #21. Check patch conflicts, run its expanded unit/integration/GUI suite, coverage gates and native runtime smoke test. Report overlapping files, observed incompatibilities, remaining platform checks, estimated integration work and alternatives. Wait for the user's integration decision before changing #21 or merging #18.

## Acceptance criteria

- Generated fixtures establish that metadata, text, embedded PDFs, truncation and friendly failures work with Tika 4.1.0.
- Parser and stream state do not leak between files or concurrent extractions.
- The shaded JAR parses a real PDF using only bundled dependencies and preserves the JSON envelope.
- Dependency resolution contains a consistent Tika version and no extra OCR/cloud service integration.
- Standalone CI succeeds and the #21 evaluation has explicit results and limits.

## Metadata compatibility

The application displays and exports the parser's metadata keys. Tika 4 changes keys such as `pdf:PDFVersion` to `pdf:pdf-version`, `pdf:hasXMP` to `pdf:has-xmp`, `access_permission:*` to `access-permission:*`, and `X-TIKA:*` to `tk:*`. Dublin Core names and the canonical `Content-Type` string remain stable. The JSON envelope (`source`, `metadata`, and optional `content`/`contentTruncated`) remains unchanged. Consumers matching old parser-specific keys must adapt; compatibility aliases are a separate product choice.

## Sources pinned to 4.1.0

- [Migration guide](https://github.com/apache/tika/blob/4.1.0/docs/modules/ROOT/pages/migration-to-4x/migrating-to-4x.adoc)
- [Metadata changes](https://github.com/apache/tika/blob/4.1.0/docs/modules/ROOT/pages/migration-to-4x/metadata-changes-4x.adoc)

## Results

Implementation and integration evaluation pending.
