# Implementation Plan: Change Detection

## Overview

The Change Detection core components already exist in the codebase (StateComparator, CompareResult, Difference, StatusCommand, DiffCommand, and supporting models). This plan focuses on verifying correctness against requirements, extending existing unit tests, and implementing parameterized tests for the 19 correctness properties defined in the design document. Those tests use JUnit 5 `@ParameterizedTest`; the project has no property-based testing library.

## Tasks

- [x] 1. Verify and test StateComparator core comparison pipeline
  - [x] 1.1 Verify StateComparator initialization and preprocessing logic
    - Review `StateComparator.init()`, `makeLastStateComparable()`, and `filterOut()` against Requirements 15.1, 15.2, 15.3, 18.1
    - Add unit tests for: model version mismatch logging warning and treating as no previous state (Req 18.1), cross-platform attribute filtering — POSIX on Windows, DOS on Unix, SELinux when disabled (Req 15.1, 15.2, 15.3)
    - Extend `StateComparatorTest` as needed
    - _Requirements: 15.1, 15.2, 15.3, 18.1_

  - [x]* 1.2 Write property test: Identity comparison produces zero modifications (Property 1)
    - **Property 1: Identity comparison produces zero modifications**
    - Create `src/test/java/org/fim/internal/StateComparatorPropertyTest.java`
    - Generate random valid States, compare each with an identical copy, verify `somethingModified()` returns false and `modifiedCount()` returns 0
    - **Validates: Requirements 19.1**

  - [x]* 1.3 Write property test: File count conservation (Property 2)
    - **Property 2: File count conservation**
    - Generate random previous and current States, run comparison, verify not-modified + `modifiedCount()` equals `currentState.getFileCount()`
    - **Validates: Requirements 13.1, 19.2**

- [x] 2. Verify and test content, date, and attribute modification detection
  - [x] 2.1 Verify same-filename matching logic in StateComparator
    - Review `searchForSameFileNames()`, `handleModificationForSameName()`, and `handleCorruptionForSameName()` against Requirements 4.1–4.4, 5.1–5.2, 6.1–6.2
    - Add unit tests for: content modification with different hash (Req 4.1), content modification with different length (Req 4.1), date-only modification (Req 5.1), attribute-only modification (Req 6.1), consumed file name de-prioritized as origin in Phase 3 (Req 4.4, 4.5), consumed-only origin still yielding `copied` (Req 4.6, 8.3)
    - Extend `StateComparatorTest` as needed
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 5.1, 5.2, 6.1, 6.2_

  - [x]* 2.2 Write property test: Content modification detection (Property 3)
    - **Property 3: Content modification detection**
    - Generate States where a file has the same name but different FileHash or fileLength, verify classified as `contentModified` with matching fileNames in the Difference
    - **Validates: Requirements 4.1, 4.2, 19.3**

  - [x]* 2.3 Write property test: Date modification detection (Property 4)
    - **Property 4: Date modification detection**
    - Generate States where a file has the same name, same hash, same length but different fileTime, verify classified as `dateModified` with equal FileHash and fileLength in the Difference
    - **Validates: Requirements 5.1, 5.2, 19.5**

  - [x]* 2.4 Write property test: Attribute modification detection (Property 5)
    - **Property 5: Attribute modification detection**
    - Generate States where a file has the same name, content, and time but different fileAttributes, verify classified as `attributesModified`
    - **Validates: Requirements 6.1, 6.2**

  - [x]* 2.5 Write property test: Modification type structural invariants (Property 17)
    - **Property 17: Modification type structural invariants**
    - Generate random State pairs, run comparison, verify: contentModified Differences have matching fileNames; renamed Differences have matching FileHash; dateModified Differences have matching FileHash and fileLength
    - **Validates: Requirements 19.3, 19.4, 19.5**

- [x] 3. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Verify and test hash-based difference detection (rename, copy, duplicate, add)
  - [x] 4.1 Verify searchForDifferences logic in StateComparator
    - Review `searchForDifferences()`, `findBestOriginal()`, and `pickBestRenameOriginal()` against Requirements 7.1–7.5, 8.1–8.2, 9.1–9.2, 10.1–10.3
    - Add unit tests for: zero-length file classified as added not renamed (Req 7.3, 10.2), dontHash mode skips hash matching (Req 7.2, 10.3), rename origin selected from actually deleted files (Req 7.5), copy vs duplicate distinction based on newFileHash (Req 8.1, 9.1)
    - Extend `StateComparatorTest` as needed
    - _Requirements: 7.1, 7.2, 7.3, 7.5, 8.1, 8.2, 9.1, 9.2, 10.1, 10.2, 10.3_

  - [x]* 4.2 Write property test: Renamed file detection (Property 6)
    - **Property 6: Renamed file detection**
    - Generate States where a file has a different name but same FileHash as a previous file no longer at its original path (fileLength > 0, hashMode != dontHash), verify classified as `renamed` with equal FileHash in the Difference
    - **Validates: Requirements 7.1, 19.4**

  - [x]* 4.3 Write property test: Copied file detection (Property 7)
    - **Property 7: Copied file detection**
    - Generate States where a new file has the same FileHash as a previous file still present but whose content has changed (newFileHash differs), verify classified as `copied` and not as `added`
    - **Validates: Requirements 8.1, 8.2, 8.3**

  - [x]* 4.4 Write property test: Duplicated file detection (Property 8)
    - **Property 8: Duplicated file detection**
    - Generate States where a new file has the same FileHash as a previous file still present and whose content has not changed, verify classified as `duplicated`
    - **Validates: Requirements 9.1, 9.2**

  - [x]* 4.5 Write property test: Added file detection (Property 9)
    - **Property 9: Added file detection**
    - Generate States where a current file has no matching previous file by name or hash (or has zero length, or hashMode is dontHash), verify classified as `added`
    - **Validates: Requirements 10.1, 10.2, 10.3**

- [x] 5. Verify and test deletion and ignored file handling
  - [x] 5.1 Verify searchForDeleted and isFileIgnored logic
    - Review `searchForDeleted()` and `isFileIgnored()` against Requirements 11.1–11.3
    - Add unit tests for: unmatched previous file classified as deleted (Req 11.1), ignored file excluded from deletion by exact match (Req 11.2), ignored file excluded by directory prefix (Req 11.3)
    - Extend `StateComparatorTest` as needed
    - _Requirements: 11.1, 11.2, 11.3_

  - [x]* 5.2 Write property test: Deleted file detection (Property 10)
    - **Property 10: Deleted file detection**
    - Generate States where a previous file has no matching current file and is not ignored, verify classified as `deleted`
    - **Validates: Requirements 11.1**

  - [x]* 5.3 Write property test: Ignored files excluded from deletion (Property 11)
    - **Property 11: Ignored files excluded from deletion**
    - Generate States where a previous file's name matches an ignored entry (exact or directory prefix), verify it is NOT classified as `deleted`
    - **Validates: Requirements 11.2, 11.3**

- [x] 6. Verify and test special comparison modes
  - [x] 6.1 Verify null previous state and hardware corruption mode
    - Review `init()` null-state handling and `searchForHardwareCorruption()` mode against Requirements 12.1, 12.2, 18.2
    - Add unit tests for: null previous state classifies all files as added (Req 18.2), hardware corruption mode detects same-name same-time different-hash as corrupted (Req 12.1), hardware corruption mode skips rename/copy/duplicate/added/deleted phases (Req 12.2)
    - Extend `StateComparatorTest` as needed
    - _Requirements: 12.1, 12.2, 18.2_

  - [x]* 6.2 Write property test: Null previous state classifies all as added (Property 12)
    - **Property 12: Null previous state classifies all files as added**
    - Generate random current States, compare against null previous State, verify all files classified as `added` and added count equals file count
    - **Validates: Requirements 18.2**

  - [x]* 6.3 Write property test: Hardware corruption mode behavior (Property 13)
    - **Property 13: Hardware corruption mode behavior**
    - Generate State pairs, compare in hardware corruption mode, verify renamed/copied/duplicated/added/deleted lists are empty; files with same name and time but different hash are classified as `corrupted`
    - **Validates: Requirements 12.1, 12.2**

- [x] 7. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Verify and test ignore flags and subdirectory filtering
  - [x] 8.1 Verify ignore flag behavior in StateComparator
    - Review `makeLastStateComparable()` ignore flag handling against Requirements 14.1–14.4, 7.4
    - Add unit tests for: attributesIgnored suppresses attributesModified (Req 14.1), datesIgnored suppresses dateModified (Req 14.2), renamedIgnored treats renamed as not-modified (Req 14.3, 7.4), ignore flag parenthetical note in output (Req 14.4)
    - Extend `StateComparatorTest` and `CompareResultTest` as needed
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 7.4_

  - [x]* 8.2 Write property test: Ignore flags suppress modifications (Property 14)
    - **Property 14: Ignore flags suppress their respective modification types**
    - Generate State pairs with various ignore flag combinations, verify: attributesIgnored → zero attributesModified; datesIgnored → zero dateModified; renamedIgnored → zero renamed
    - **Validates: Requirements 14.1, 14.2, 14.3, 7.4**

  - [x]* 8.3 Write property test: Subdirectory filtering (Property 18)
    - **Property 18: Subdirectory filtering**
    - Generate random States and subdirectory paths, filter by subdirectory, verify only FileState entries with matching prefix are retained
    - **Validates: Requirements 1.7**

- [x] 9. Verify and test CompareResult display and output
  - [x] 9.1 Verify CompareResult display formatting and truncation
    - Review `displayChanges()`, `displayDifferences()`, `formatModifiedAttributes()`, `sortResults()`, and `getModificationCounts()` against Requirements 16.1–16.7, 17.1–17.4
    - Add unit tests for: timestamp/comment header display (Req 16.1), category labels (Req 16.2), rename arrow formatting (Req 16.3), copy/duplicate original name display (Req 16.4), attribute change formatting (Req 16.5), summary line (Req 16.7), truncateOutput=0 suppresses details (Req 17.2)
    - Extend `CompareResultTest` as needed
    - _Requirements: 16.1, 16.2, 16.3, 16.4, 16.5, 16.6, 16.7, 17.1, 17.2, 17.3, 17.4_

  - [x]* 9.2 Write property test: CompareResult sorting (Property 15)
    - **Property 15: CompareResult sorting**
    - Generate CompareResults with random Differences, call `sortResults()`, verify every Difference list is sorted ascending by `fileState.getFileName()`
    - **Validates: Requirements 16.6**

  - [x]* 9.3 Write property test: Output truncation (Property 16)
    - **Property 16: Output truncation**
    - Generate Difference lists exceeding truncateOutput, call `displayDifferences()`, verify truncation occurs when display index reaches truncateOutput AND remaining entries exceed one quarter of truncateOutput
    - **Validates: Requirements 17.1, 17.3**

  - [x]* 9.4 Write property test: getModificationCounts consistency (Property 19)
    - **Property 19: getModificationCounts consistency**
    - Generate CompareResults with random Difference lists, verify `getModificationCounts()` fields match the sizes of corresponding modification lists (noting corrupted field is not populated)
    - **Validates: Requirements 17.4**

- [x] 10. Verify and test StatusCommand and DiffCommand
  - [x] 10.1 Verify StatusCommand execution flow
    - Review `StatusCommand.execute()` against Requirements 1.1–1.8
    - Add unit tests for: status generates current state and loads last state (Req 1.1, 1.2), "Nothing modified" message (Req 1.3), hash mode options `-f`/`-s`/`-n` (Req 1.4, 1.5, 1.6), subdirectory filtering (Req 1.7), compatible hash mode info message (Req 1.8)
    - Create `src/test/java/org/fim/command/StatusCommandTest.java`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8_

  - [x] 10.2 Verify DiffCommand backward compatibility
    - Review `DiffCommand.execute()`, `getCmdName()`, `getShortCmdName()` against Requirements 2.1–2.3
    - Add unit tests for: deprecation message printed (Req 2.1), delegates to StatusCommand (Req 2.2), empty short command name (Req 2.3)
    - Create `src/test/java/org/fim/command/DiffCommandTest.java`
    - _Requirements: 2.1, 2.2, 2.3_

- [x] 11. Verify pipeline integrity checks
  - [x] 11.1 Verify checkAllFilesManagedCorrectly logic
    - Review `checkAllFilesManagedCorrectly()` against Requirements 13.1–13.4
    - Add unit tests for: IllegalStateException on file count mismatch (Req 13.2), IllegalStateException on non-empty addedOrModified (Req 13.4)
    - Extend `StateComparatorTest` as needed
    - _Requirements: 13.1, 13.2, 13.3, 13.4_

- [x] 12. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- All core components already exist in the codebase; tasks focus on verification and testing
- Property tests validate universal correctness properties from the design document
- Correctness properties are checked with JUnit 5 `@ParameterizedTest`; the project has no property-based testing library
- Each property test references its design document property number and validated requirements
- Property tests for StateComparator go in `StateComparatorPropertyTest.java`; property tests for CompareResult go in `CompareResultPropertyTest.java`
