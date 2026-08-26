# Implementation Plan: Corruption Detection

## Overview

The Corruption Detection core components already exist in the codebase (DetectCorruptionCommand, StateComparator with hardware corruption mode, CompareResult with corrupted list, Difference, FileTime, FileHash, and Modification.corrupted). This plan focuses on verifying correctness against requirements, extending existing unit tests, and implementing parameterized tests for the 8 correctness properties defined in the design document. Those tests use JUnit 5 `@ParameterizedTest`; the project has no property-based testing library.

## Tasks

- [x] 1. Verify and test DetectCorruptionCommand execution and hash mode validation
  - [x] 1.1 Verify DetectCorruptionCommand hash mode validation and execution flow
    - Review `DetectCorruptionCommand.execute()` against Requirements 1.1–1.7
    - Verify existing tests in `DetectCorruptionCommandTest` cover: dontHash rejection (Req 1.2), hashSmallBlock rejection (Req 1.2), hashMediumBlock rejection (Req 1.2), hashAll proceeds (Req 1.1, 1.3)
    - Add unit tests for: ALLOW_COMPATIBLE hash mode check (Req 1.3), StateGenerator invocation produces current State (Req 1.4), StateManager loads last committed State (Req 1.5), StateComparator created with hardware corruption mode (Req 1.6), "Nothing corrupted" message when no corruption found (Req 1.7)
    - Extend `DetectCorruptionCommandTest` as needed
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7_

  - [x]* 1.2 Write property test: Non-hashAll mode rejection (Property 1)
    - **Property 1: Non-hashAll mode rejection**
    - Create `src/test/java/org/fim/command/DetectCorruptionCommandPropertyTest.java`
    - For any HashMode that is not hashAll (dontHash, hashSmallBlock, hashMediumBlock), verify `DetectCorruptionCommand.execute()` throws `BadFimUsageException`
    - **Validates: Requirements 1.1, 1.2**

- [x] 2. Verify and test subdirectory invocation
  - [x] 2.1 Verify subdirectory filtering in DetectCorruptionCommand
    - Review `DetectCorruptionCommand.execute()` subdirectory handling against Requirements 2.1–2.3
    - Add unit tests for: subdirectory invocation filters last state to subtree (Req 2.1), root invocation uses full state without filtering (Req 2.2), filterDirectory called with correct parameters (Req 2.3)
    - Extend `DetectCorruptionCommandTest` as needed
    - _Requirements: 2.1, 2.2, 2.3_

  - [x]* 2.2 Write property test: Subdirectory filtering preserves only subtree files (Property 2)
    - **Property 2: Subdirectory filtering preserves only subtree files**
    - Generate random States and subdirectory paths, filter via `State.filterDirectory`, verify only FileState entries whose fileName starts with the subdirectory prefix are retained
    - **Validates: Requirements 2.1**

- [x] 3. Verify and test StateComparator hardware corruption detection mode
  - [x] 3.1 Verify StateComparator corruption mode logic
    - Review `searchForHardwareCorruption()`, `compare()`, `searchForSameFileNames()`, and `handleCorruptionForSameName()` against Requirements 3.1–3.9
    - Verify existing tests in `StateComparatorTest` and `DetectCorruptionCommandTest` cover the corruption detection flow
    - Add unit tests for: `searchForHardwareCorruption()` enables corruption mode and returns `this` for fluent chaining (Req 3.1), same-filename matching in corruption mode (Req 3.2), corruption classification for same fileName + same FileTime + different FileHash (Req 3.3), skipping rename/copy/duplicate/added/deleted phases (Req 3.4–3.8), result sorting (Req 3.9)
    - Extend `StateComparatorTest` as needed
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9_

  - [x]* 3.2 Write property test: Corruption classification correctness (Property 3)
    - **Property 3: Corruption classification correctness**
    - Create `src/test/java/org/fim/internal/StateComparatorCorruptionPropertyTest.java`
    - Generate previous and current States where files share the same fileName and FileTime but have different FileHash, compare in hardware corruption mode, verify each such file appears in the corrupted list with both previous and current FileState, modification set to `corrupted`, same fileName, same FileTime, and different FileHash
    - **Validates: Requirements 3.3, 4.1, 4.2, 6.1, 6.2, 6.3**

  - [x]* 3.3 Write property test: No false positives when hash is unchanged (Property 4)
    - **Property 4: No false positives when hash is unchanged**
    - Generate States where files have the same fileName and same FileHash, compare in hardware corruption mode, verify none appear in the corrupted list regardless of FileTime changes
    - **Validates: Requirements 4.3, 6.4**

  - [x]* 3.4 Write property test: No false positives when timestamp changed (Property 5)
    - **Property 5: No false positives when timestamp changed**
    - Generate States where files have the same fileName but different FileTime, compare in hardware corruption mode, verify none appear in the corrupted list regardless of FileHash changes
    - **Validates: Requirements 4.4, 6.5**

- [x] 4. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Verify and test corruption mode structural invariants
  - [x]* 5.1 Write property test: Non-corruption lists empty in corruption mode (Property 6)
    - **Property 6: Non-corruption lists empty in corruption mode**
    - Generate random previous and current States, compare in hardware corruption mode, verify the CompareResult has empty `added`, `deleted`, `renamed`, `copied`, and `duplicated` lists
    - **Validates: Requirements 3.4, 3.5, 3.6, 3.7, 3.8, 6.6**

  - [x]* 5.2 Write property test: Corrupted list sorted by fileName (Property 7)
    - **Property 7: Corrupted list sorted by fileName**
    - Generate States producing multiple corrupted entries, compare in hardware corruption mode, verify the corrupted list is sorted in ascending order by `fileState.getFileName()`
    - **Validates: Requirements 3.9, 5.5**

  - [x]* 5.3 Write property test: modifiedCount includes corrupted entries (Property 8)
    - **Property 8: modifiedCount includes corrupted entries**
    - Generate CompareResults with various corrupted list sizes, verify `modifiedCount()` includes `corrupted.size()` in the total
    - **Validates: Requirements 5.4**

- [x] 6. Verify and test CompareResult corruption display
  - [x] 6.1 Verify CompareResult corruption display formatting
    - Review `displayChanges()`, `displayCounts()`, `sortResults()`, and `modifiedCount()` corruption-specific behavior against Requirements 5.1–5.5
    - Add unit tests for: verbose output displays "Corrupted?:" label with file name and modified attributes (Req 5.1), summary line includes "<count> corrupted" format (Req 5.2), "Nothing corrupted" / notModifiedMessage displayed when no corruption and no other modifications (Req 5.3), corrupted list size included in modifiedCount (Req 5.4), corrupted list sorted by fileName before display (Req 5.5)
    - Extend `CompareResultTest` as needed
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [x] 7. Verify corruption detection invariants via existing integration test
  - [x] 7.1 Verify end-to-end corruption detection in DetectCorruptionCommandTest
    - Review `canDetectHardwareCorruption()` test against Requirements 6.1–6.6
    - Verify the existing test covers: corrupted Difference has same fileName (Req 6.1), same FileTime (Req 6.2), different FileHash (Req 6.3)
    - Add assertions for: unchanged-hash files not in corrupted list (Req 6.4), timestamp-changed files not in corrupted list (Req 6.5), non-corruption lists empty in corruption mode result (Req 6.6)
    - Extend `DetectCorruptionCommandTest` as needed
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

- [x] 8. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- All core components already exist in the codebase; tasks focus on verification and testing
- Property tests validate universal correctness properties from the design document
- Correctness properties are checked with JUnit 5 `@ParameterizedTest`; the project has no property-based testing library
- Each property test references its design document property number and validated requirements
- Property tests for DetectCorruptionCommand go in `DetectCorruptionCommandPropertyTest.java`
- Property tests for StateComparator corruption mode go in `StateComparatorCorruptionPropertyTest.java`
