# Tika 4 migration

## Scope and branch boundary

Dependabot #18's exact target, Tika 4.1.0, was implemented independently on `feature/tika-4-migration`, based on master `358fdd5a1bf6596a1d7c0ac64a77cdc873a861c2`, and evaluated against #21 commit `a78c1856ac2f414213bdf4f6f3b798b641aacd24`. After review, the user approved integrating this feature into #21 (`feature/desktop-ci-cd`). The integration preserves the tested behavior and adopts canonical Tika 4 metadata names. #18 and standalone draft #22 remain open; master is unchanged.

## Completed implementation and evaluation plan

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

## Standalone results

Implementation commit: `faa5a7a97c84d3ddef7e3e6f5ef1b26c1d460956`, in [draft PR #22](https://github.com/Alexxfromgit/PDF-Meta-Viewer/pull/22).

- The six version-independent migration contracts passed first against Tika 3.3.2, including embedded PDF text.
- Java 17 `mvn clean verify` with Tika 4.1.0 passed 13 unit tests and 2 packaged-JVM integration tests. The latter launch the extractor with only its tiny probe class and the shaded JAR, check embedded text and CLI export, and check that both core and PDF parser index entries survive shading.
- The standalone macOS app built successfully and its ad-hoc signature verified.
- The [standalone Linux/Java 17 CI run](https://github.com/Alexxfromgit/PDF-Meta-Viewer/actions/runs/39747934793) passed, including packaged integration tests.
- All three runtime Tika modules resolve to 4.1.0; Jackson stays at 2.22.3 and SLF4J at 2.0.20.

## Evaluation of integration into #21

The evaluation used #21 commit `a78c1856ac2f414213bdf4f6f3b798b641aacd24` and feature implementation commit `faa5a7a97c84d3ddef7e3e6f5ef1b26c1d460956`. A Git merge simulation produced a tree that was extracted into a temporary directory. No #21 branch or remote ref was modified.

### Required reconciliation

1. Combine the changelog additions: this was the only textual merge conflict.
2. Remove the standalone feature's additional Failsafe plugin block. #21 already configures it with the shared `${surefire.version}`, packaged CLI version property, integration lifecycle and desktop profile. Keep that existing block. Without this reconciliation the simulated POM contains duplicate plugin declarations, despite Git merging it without a textual conflict.

The extractor, README, Tika version bump, parser-index transformer, and three new test files merged without a source conflict. There was no application-code adaptation required beyond the completed feature.

### Tests on the reconciled temporary tree

- `./mvnw -Pdesktop-smoke clean verify`: **35 unit tests and 6 integration/desktop tests passed**, with no skips or failures; all existing coverage gates passed.
- The GUI smoke test loaded the sample PDF, filtered/reset its metadata, displayed text and switched theme.
- **11 Python release tests**, workflow lint and shell syntax checks passed.
- The macOS app built; signature verification and the native CLI launcher/bundled-runtime extraction/export smoke test passed.
- CycloneDX SBOM generation passed and lists the three Tika modules at 4.1.0.
- The same sample PDF exported through the 3.3.2 and 4.1.0 JARs retains the `source`/`metadata` envelope. Metadata fields increase from 52 to 58. In addition to parser/permission key renames, the sample's custom `PTEX.Fullbanner` becomes `xmp-raw:pdfx:PTEX.Fullbanner`, and extra structural/XMP fields appear.

### Evaluation findings before integration approval

**Remaining integration effort: low, approximately 30–60 minutes plus one complete #21 CI cycle**, assuming no new platform-specific failures. The work is the two small reconciliations above, review of the resulting diff, and revalidation on the actual chosen branch. The temporary local integration is already proven on macOS/Java 17; the feature itself also passed Linux/Java 17 CI.

At evaluation time, the integrated Windows, Linux GUI, Intel macOS and Java 21/25 matrix, CodeQL and dependency review had not been rerun with Tika 4. These are required checks on the actual integrated #21 branch before it can merge; the earlier temporary local pass does not replace them.

The main product decision is whether to accept Tika 4's canonical metadata keys. Existing consumers matching old key strings can be affected even though the JSON envelope is unchanged. A legacy-key compatibility layer would be additional scope with its own tests; it is not included in the estimate above.

At the end of the evaluation, #18 remained open and #21 was unchanged at Tika 3.3.2. The user subsequently approved transferring the feature into #21. The actual integration combines both changelog additions and removes the duplicate standalone Failsafe block, preserving #21's shared version, packaged CLI configuration and desktop profile. No migration PR is merged into master as part of this integration.


## Validation of the actual #21 integration

After approval, the feature was merged into `feature/desktop-ci-cd` with the two reconciliations identified above. The actual checkout passed **35 unit tests and 6 packaged/desktop tests**, with no failures or skips, and all JaCoCo gates. Local macOS overall line coverage is **606/822 (73.7%)**; extraction line coverage is **52/58 (89.7%)**, with branch coverage unchanged at **12/14 (85.7%)**. The **11 Python release-tool tests**, workflow lint and shell syntax checks also passed.

The integrated branch retains one Failsafe build plugin at `${surefire.version}`, the packaged CLI version property and the desktop profile. The final cross-platform packaging/runtime, Java 17/21/25, CodeQL and dependency-review results are recorded in [PR #21's checks](https://github.com/Alexxfromgit/PDF-Meta-Viewer/pull/21/checks). These checks must pass on the integrated commit before it can merge into master.
