# Changelog

All notable changes to this project will be documented in this file.

## [0.2.0] - 2026-03-12

### Changed
- **Java**: 17 → 21 (required at runtime)
- **Kotlin**: 1.9.22 → 2.0.21
- **Gradle**: 8.5 → 8.10
- **JUnit**: 5.9.0 → 5.11.3
- **Klaxon**: 5.5 → 5.6
- `slash()` frequency analysis now runs each word length in a dedicated **Java 21 virtual thread**, with results merged via `ConcurrentHashMap`. Falls back to sequential for small inputs (< 200 chars or ≤ 2 word lengths).
- `calculateDictFromDir()` reads files in parallel using virtual threads when multiple files are present.

### Added
- 4 new tests: `testVirtualThreadsUsedForLargePayload`, `testParallelConsistency`, `testLargePayloadCompression`, `testEmptyAndEdgeCases`

### Backward Compatibility
- Public API is **100% source and binary compatible** with v0.1.x
- All method signatures, return types, and data classes are unchanged
- Existing serialized dictionaries (JSON) work without modification
- **Only breaking change**: runtime now requires Java 21+ (was 17+)

## [0.1.15] - Previous release

### Summary
- Initial stable release with single-threaded compression engine
- Bayesian/permutation heuristics for dictionary calculation
- Support for US-ASCII, ISO-8859-1, and UTF-8 charsets
- JSON-based dictionary import/export via Klaxon
- File and directory-based dictionary calculation
