# Implementation Plan: File Ignore System

## Overview

The File Ignore System core components already exist in the codebase (FilePattern, FimIgnore, FimIgnoreManager, StateGenerator integration, DisplayIgnoredFilesCommand, ResetFileAttributesCommand, Context pattern storage, and State ignoredFiles persistence). This plan focuses on verifying correctness against requirements, establishing the correctness-property test approach, and implementing parameterized tests for the 10 correctness properties defined in the design document. Those tests use JUnit 5 `@ParameterizedTest`; the project has no property-based testing library.

## Tasks

- [x] 1. Establish the correctness-property test approach and verify project setup
  - [x] 1.1 Confirm the JUnit 5 parameterized test setup in build.gradle.kts
    - `junit-jupiter-params` is on the test classpath via the `org.junit:junit-bom` platform, so no
      additional dependency is needed. Declare `junit-platform-launcher` as `testRuntimeOnly` to keep the
      platform engine and launcher on aligned versions
    - Deliberately do NOT add a property-based testing library: correctness properties are expressed as
      `@ParameterizedTest` methods instead
    - _Requirements: Design testing strategy_

  - [x] 1.2 Add the shared seeded input generator
    - Create `src/test/java/org/fim/tooling/RandomInputs.java` with a fixed `SEED`, for the property tests
      whose input domain is too wide to enumerate
    - Small domains must be enumerated exhaustively instead, with `IntStream.rangeClosed(..)`,
      `@EnumSource`, or a full cross product via `Stream<Arguments>`
    - _Requirements: Design testing strategy_

- [x] 2. Verify and test FilePattern glob matching
  - [x] 2.1 Verify FilePattern glob-to-regex conversion and matching logic
    - Review `org.fim.model.FilePattern` constructor, `match()`, `matchPatterns()`, `equals()`, `hashCode()`, and `clone()` against Requirements 3.1–3.7
    - Add unit tests for edge cases: empty pattern, pattern with only special chars, null compiled field in equals/hashCode
    - Extend `FilePatternTest` with tests for `matchPatterns()` with null lists returning default values
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_

  - [x]* 2.2 Write property test: Glob-to-regex compilation correctness (Property 6)
    - **Property 6: Glob-to-regex compilation correctness**
    - Generate random glob pattern strings, verify compiled regex has special chars escaped, multiple `*` collapsed, and anchored with `^` and `$`
    - **Validates: Requirements 3.1, 3.2, 3.3**

  - [x]* 2.3 Write property test: FilePattern identity matching (Property 7)
    - **Property 7: FilePattern identity matching**
    - Generate random strings without `*`, verify `new FilePattern(s).match(s)` returns true
    - **Validates: Requirements 3.6**

- [x] 3. Verify and test FimIgnore loading and parsing
  - [x] 3.1 Verify FimIgnoreManager loading, parsing, and merging logic
    - Review `FimIgnoreManager.loadFimIgnore()`, `loadInitialFimIgnore()`, `loadLocalIgnore()`, and `addParentFimIgnore()` against Requirements 1.1–1.6
    - Add unit tests for: I/O error handling (Req 1.5), subdirectory parent traversal merging (Req 1.6), empty `.fimignore` file
    - Extend `FimIgnoreManagerTest` as needed
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_

  - [x]* 3.2 Write property test: Fimignore pattern classification (Property 1)
    - **Property 1: Fimignore pattern classification**
    - Generate random lines with and without `**/` prefix, write to a temp `.fimignore`, load via `loadFimIgnore()`, verify correct classification into local vs all-directories sets
    - **Validates: Requirements 1.2, 1.3**

  - [x]* 3.3 Write property test: All-directories pattern inheritance via merge (Property 2)
    - **Property 2: All-directories pattern inheritance via merge**
    - Generate random parent `FimIgnore` with non-empty `filesToIgnoreInAllDirectories`, call `loadLocalIgnore()` for a child directory, verify child contains all parent all-directories patterns
    - **Validates: Requirements 1.4**

- [x] 4. Verify and test file/directory ignore evaluation
  - [x] 4.1 Verify FimIgnoreManager.isIgnored() and ignoreThisFiles() logic
    - Review `isIgnored()` evaluation order (hardcoded dirs → all-directories → local) against Requirements 2.1–2.6
    - Add unit tests for: hardcoded directory names with non-directory attributes (should not be ignored), `ignoreThisFiles()` trailing slash for directories vs files
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

  - [x]* 4.2 Write property test: Hardcoded directories are always ignored (Property 3)
    - **Property 3: Hardcoded directories are always ignored**
    - For each directory name in `{".fim", ".git", ".svn", ".cvs"}` and any random `FimIgnore` instance, verify `isIgnored()` returns true with directory attributes
    - **Validates: Requirements 2.1**

  - [x]* 4.3 Write property test: Pattern-based ignore evaluation (Property 4)
    - **Property 4: Pattern-based ignore evaluation**
    - Generate random file names and `FimIgnore` instances where at least one pattern matches, verify `isIgnored()` returns true
    - **Validates: Requirements 2.3**

  - [x]* 4.4 Write property test: Ignored directory paths have trailing slash (Property 5)
    - **Property 5: Ignored directory paths have trailing slash**
    - Generate random paths with mocked directory/file `BasicFileAttributes`, call `ignoreThisFiles()`, verify trailing `/` presence for directories and absence for files
    - **Validates: Requirements 2.4**

- [x] 5. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Verify and test CLI include/exclude pattern filtering
  - [x] 6.1 Verify Context include/exclude pattern storage and StateGenerator filtering
    - Review `Fim.parseFilePatterns()` for colon-separated parsing, `Context` include/exclude getters/setters, and `StateGenerator.scanFileTree()` filtering logic against Requirements 4.1–4.7
    - Add unit tests for: colon-separated pattern parsing, `matchPatterns()` with null include (default true) and null exclude (default false), combined include+exclude logic
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_

  - [x]* 6.2 Write property test: Include/exclude filtering logic (Property 8)
    - **Property 8: Include/exclude filtering logic**
    - Generate random file names, include pattern lists, and exclude pattern lists; verify file is accepted iff it matches at least one include AND does not match any exclude; verify null lists return correct defaults
    - **Validates: Requirements 4.3, 4.4, 4.5, 4.6, 4.7**

- [x] 7. Verify and test DisplayIgnoredFilesCommand
  - [x] 7.1 Verify DisplayIgnoredFilesCommand behavior
    - Review `DisplayIgnoredFilesCommand.execute()` against Requirements 5.1–5.6
    - Add unit tests for: no state found (Req 5.6), empty/null ignored files set displaying message (Req 5.3), state metadata display (Req 5.4), comment display (Req 5.5)
    - Create `src/test/java/org/fim/command/DisplayIgnoredFilesCommandTest.java`
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

- [x] 8. Verify and test ResetFileAttributesCommand
  - [x] 8.1 Verify ResetFileAttributesCommand behavior
    - Review `ResetFileAttributesCommand.execute()` against Requirements 6.1–6.13
    - Verify existing `ResetFileAttrsCommandTest` covers: attribute reset, subdirectory filtering (Req 6.8), non-existent file skipping (Req 6.12), error handling (Req 6.13)
    - Add missing test cases if needed for: no attributes to reset message (Req 6.10), summary display (Req 6.11)
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8, 6.9, 6.10, 6.11, 6.12, 6.13_

- [x] 9. Verify and test State ignoredFiles persistence
  - [x] 9.1 Verify State ignoredFiles serialization and deserialization
    - Review `State` class for `ignoredFiles` field inclusion in `equals()`, `hashCode()`, `hashState()`, `clone()`, and JSON serialization
    - Add unit tests for: State with ignoredFiles round-trip via GZIP save/load, State with null/empty ignoredFiles
    - Extend `StateTest` as needed
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [x]* 9.2 Write property test: Subdirectory state filtering (Property 9)
    - **Property 9: Subdirectory state filtering**
    - Generate random `State` objects with file states and subdirectory paths, call `filterDirectory()` with `keepFilesInside=true`, verify only matching file states are returned
    - **Validates: Requirements 6.8**

  - [x]* 9.3 Write property test: State ignoredFiles round-trip (Property 10)
    - **Property 10: State ignoredFiles round-trip**
    - Generate random `State` objects with non-empty `ignoredFiles` sets, save to temp GZIP file, load back, verify `ignoredFiles` sets are equivalent
    - **Validates: Requirements 7.2, 7.3, 7.4**

- [x] 10. Verify StateGenerator ignore integration end-to-end
  - [x] 10.1 Verify StateGenerator wiring of ignore system
    - Review `StateGenerator` constructor creating `FimIgnoreManager`, `scanFileTree()` calling `loadLocalIgnore()`, `isIgnored()`, `ignoreThisFiles()`, and `state.setIgnoredFiles()` against Requirements 2.5, 2.6, 7.1
    - Add or extend integration tests verifying that ignored files appear in `state.getIgnoredFiles()` and non-ignored files are hashed
    - _Requirements: 2.5, 2.6, 7.1_

- [x] 11. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- All core components already exist in the codebase; tasks focus on verification and testing
- Property tests validate universal correctness properties from the design document
- Correctness properties are checked with JUnit 5 `@ParameterizedTest`; the project has no property-based testing library
- Each property test references its design document property number and validated requirements
