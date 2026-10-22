# Test coverage and quality plan

## Baseline (before this change)

Measured on Java 17 using JaCoCo 0.8.15, with the original six TestNG tests:

| Area | Lines covered | Branches covered | Main gaps |
| --- | --- | --- | --- |
| Entire application | 68 / 815 (8.3%) | 12 / 208 (5.8%) | Most desktop behavior and failure paths |
| PDF extraction | 28 / 54 (51.9%) | 9 / 14 (64.3%) | Invalid inputs, encrypted/damaged files, preview limit |
| Repository and immutable result | 34 / 47 (72.3%) | 3 / 6 (50%) | Content export, replacement failures, cleanup, defensive copies |
| JSON converter | 6 / 8 (75%) | No branches | Escaping, Unicode, null/error handling |
| CLI | 0 / 13 | 0 / 6 | Output, export, usage, exit codes |
| Swing UI and table model | 0 / 693 | 0 / 182 | Selection mapping, notifications, sorting/filtering, interaction |

There was no coverage report or regression gate. Two of the six tests only checked constants. JSON assertions checked substrings rather than parsing the export. Only one sample PDF exercised extraction. CI ran on Linux/Java 17 and uploaded a JAR; native packaging and releases were untested.

## Improvements

Tests now exercise generated blank, encrypted, malformed and large PDFs, immutable records, Unicode JSON round trips, content inclusion/omission, replacement and cleanup after export failures, CLI success/error behavior, and Swing table events/filtering/selection on the event dispatch thread. Process integration tests run the shaded JAR with only its bundled dependencies and check its entry point and version. An optional full-window smoke test opens the sample PDF in the background, waits for results, filters and resets the table, checks the text preview and switches themes; preferences are isolated in memory. Linux CI runs it under Xvfb. Each native build also performs an export through its packaged CLI launcher and bundled runtime.

`./mvnw clean verify` runs unit tests, builds the shaded JAR, runs packaged CLI integration tests, writes HTML/XML/CSV coverage to `target/site/jacoco/`, and enforces **85% line / 75% branch coverage per core package** (`extractor`, `repository`, `converters`, `program`) plus **100% line coverage for MetadataTableModel**. All UI classes remain in the report: the core gate is not an overall desktop coverage claim.

`./mvnw test` runs unit tests only. `./mvnw test -Dgroups=smoke` runs the small existing smoke subset; release validation always uses the full `verify` lifecycle.

## Results after this change

Local Java 17 verification passed **28 unit tests and 4 integration/UI tests**, plus **11 Python release-tool tests**. Default verification runs 3 integration tests; `-Pdesktop-smoke` adds the window test. With that profile on macOS, JaCoCo measured:

| Area | Line coverage | Branch coverage |
| --- | --- | --- |
| Entire application | 602 / 818 (73.6%) | 92 / 210 (43.8%) |
| PDF extraction | 48 / 54 (88.9%) | 12 / 14 (85.7%) |
| Repository and immutable result | 43 / 47 (91.5%) | 5 / 6 (83.3%) |
| JSON converter | 8 / 8 (100%) | No branches |
| CLI | 15 / 16 (93.8%) | 7 / 8 (87.5%) |
| Swing UI and table model | 488 / 693 (70.4%) | 68 / 182 (37.4%) |

Without a display/profile, overall line coverage is 15.5% because most of the code constructs the Swing window; core coverage remains the same. CI reports actual per-platform results rather than treating the core gate as overall coverage. GUI totals can vary with platform-specific paths. No report exclusions hide uncovered Swing code.

Run `./mvnw -Pdesktop-smoke clean verify` on a machine with a display, or `xvfb-run -a ./mvnw -Pdesktop-smoke clean verify` on Linux. Reports are in `target/surefire-reports/`, `target/failsafe-reports/`, and `target/site/jacoco/`. Release validation, asset completeness, checksums, version bounds and changelog updates are covered by `python3 -m unittest discover -s scripts/tests -v`.

## Remaining improvements

- Extend full-window interaction coverage to cancellation/races, overwrite confirmation, drag/drop, clipboard, persistent preferences and keyboard shortcuts. The window smoke test covers loading, filtering, preview and theme switching; it does not establish complete GUI coverage.
- Exercise inaccessible files under an unprivileged user, non-atomic filesystems and disk-full export failures. These require controlled filesystem environments; portable tests avoid changing host permissions.
- Test installation, uninstall and upgrades on clean Windows/macOS machines; signing and notarization need real publisher credentials.
- Add a curated PDF corpus for unusual metadata, scanned documents, malicious inputs and parser regressions. Generated fixtures and one sample cannot establish general PDF compatibility.
- Performance/memory budgets need separate representative workloads. The large-PDF test verifies the output bound, not a maximum parser memory allocation.
