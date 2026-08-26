# Implementation Plan: Duplicate Detection

## Overview

The Duplicate Detection core components already exist in the codebase (FindDuplicatesCommand, RemoveDuplicatesCommand, DuplicateFinder, DuplicateOutputGenerator, DuplicateResult, DuplicateSet, and supporting model/output classes). This plan focuses on verifying correctness against requirements and implementing parameterized tests for the 18 correctness properties defined in the design document using JUnit 5 `@ParameterizedTest`; the project has no property-based testing library.

## Tasks

- [x] 1. Verify DuplicateFinder detection engine and core model
  - [x] 1.1 Verify DuplicateFinder detection logic against requirements
    - Review `DuplicateFinder.findDuplicates()` for: sort-by-hash before scan, grouping by FileHash+fileLength, zero-length exclusion, non-existent file exclusion, include/exclude pattern filtering, minimum group size of 2, and result sorting
    - Add unit tests to `DuplicateFinderTest` for any uncovered edge cases: pattern-based filtering, non-existent file exclusion
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8_

  - [x]* 1.2 Write property test: DuplicateSet structural invariant (Property 1)
    - **Property 1: DuplicateSet structural invariant**
    - Generate random States with FileState entries sharing hashes, run `findDuplicates()`, verify every DuplicateSet has >= 2 entries with identical FileHash and fileLength
    - **Validates: Requirements 2.2, 2.7, 12.1, 12.2, 12.3**

  - [x]* 1.3 Write property test: Zero-length file exclusion (Property 2)
    - **Property 2: Zero-length file exclusion**
    - Generate random States containing zero-length FileState entries, run `findDuplicates()`, verify no zero-length file appears in any DuplicateSet
    - **Validates: Requirements 2.3**

  - [x]* 1.4 Write property test: File pattern filtering (Property 3)
    - **Property 3: File pattern filtering**
    - Generate random States with include/exclude patterns configured, run `findDuplicates()`, verify no excluded or non-included file appears in any DuplicateSet
    - **Validates: Requirements 2.5, 2.6**

  - [x]* 1.5 Write property test: Input order independence (Property 7)
    - **Property 7: Input order independence**
    - Generate random States, shuffle FileState list, run `findDuplicates()` on both orderings, verify equivalent DuplicateSet results
    - **Validates: Requirements 2.1**

  - [x]* 1.6 Write property test: No duplicates when all hashes unique (Property 8)
    - **Property 8: No duplicates when all hashes unique**
    - Generate States where every FileState has a unique FileHash, run `findDuplicates()`, verify zero DuplicateSets and zero duplicatedFilesCount
    - **Validates: Requirements 12.6**


- [x] 2. Checkpoint - Ensure detection engine tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. Verify DuplicateSet and DuplicateResult model
  - [x] 3.1 Verify DuplicateSet wasted space calculation and DuplicateResult aggregation
    - Review `DuplicateSet` constructor for wasted space computation: `(size - 1) * firstFile.fileLength`
    - Review `DuplicateResult.addDuplicatedFiles()` for aggregate statistics: duplicatedFilesCount and totalWastedSpace accumulation
    - Add unit tests to `DuplicateSetTest` and `DuplicateResultTest` for edge cases if not already covered
    - _Requirements: 3.1, 3.2, 3.3, 12.4, 12.5, 12.7_

  - [x]* 3.2 Write property test: Wasted space calculation per set (Property 4)
    - **Property 4: Wasted space calculation per set**
    - Generate random DuplicateSets with varying file counts and sizes, verify `getWastedSpace() == (getDuplicatedFilesCount() - 1) * getDuplicatedFileSize()`
    - **Validates: Requirements 3.1, 12.7**

  - [x]* 3.3 Write property test: Aggregate statistics consistency (Property 5)
    - **Property 5: Aggregate statistics consistency**
    - Generate random DuplicateResults with multiple DuplicateSets, verify `duplicatedFilesCount` equals sum of `(count - 1)` per set and `totalWastedSpace` equals sum of `getWastedSpace()` per set
    - **Validates: Requirements 3.2, 3.3, 12.4, 12.5**

- [x] 4. Verify DuplicateResult sorting
  - [x] 4.1 Verify DuplicateResult sorting by all SortMethod values and directions
    - Review `DuplicateResult.sortDuplicateSets()` and inner comparator classes (WastedSpaceComparator, NumberOfFileComparator, FileSizeComparator, FilePathComparator)
    - Add unit tests for each SortMethod (wasted, number, size, path) in both ascending and descending order
    - Verify the comparator switch stays exhaustive over SortMethod, with no default arm
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8_

  - [x]* 4.2 Write property test: Sort correctness (Property 6)
    - **Property 6: Sort correctness**
    - Generate random DuplicateResults, apply each SortMethod and direction, verify the resulting list is correctly ordered by the configured criterion
    - **Validates: Requirements 2.8, 4.1, 4.2, 4.3, 4.4, 4.5, 4.6**


- [x] 5. Verify DuplicateOutputGenerator output formats
  - [x] 5.1 Verify human-readable output generation
    - Review `DuplicateResult.displayAndRemoveDuplicates()` for human output: numbered set headers, file listings, "No duplicate file found" message, summary-only when verbose is disabled. The human format lives only here; `DuplicateOutputGenerator` rejects it with an `IllegalStateException`
    - Add unit tests for: no-duplicates message, verbose vs non-verbose output, summary line format
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

  - [x] 5.2 Verify CSV output generation
    - Review `DuplicateOutputGenerator.generateCSV()` for: header row, one row per file, SetIndex/FileIndex incrementing, path and extension extraction
    - Add unit tests to `DuplicatedFilesTest` or create new test for CSV output structure
    - _Requirements: 6.1, 6.2, 6.3_

  - [x]* 5.3 Write property test: CSV output row count (Property 9)
    - **Property 9: CSV output row count**
    - Generate random DuplicateResults, produce CSV output, verify row count equals 1 (header) + total files across all DuplicateSets, with correct SetIndex and FileIndex values
    - **Validates: Requirements 6.1, 6.2**

  - [x]* 5.4 Write property test: Path and extension extraction (Property 10)
    - **Property 10: Path and extension extraction**
    - Generate random file name strings, verify path extraction returns everything before last `/` and extension extraction returns lowercase substring after last `.`
    - **Validates: Requirements 6.3**

  - [x] 5.5 Verify JSON output generation
    - Review `DuplicateOutputGenerator.generateJson()` for: JSON array structure, wastedSpace field, fileList with name/length/path/type fields, Jackson ObjectMapper usage
    - Add unit tests for JSON output structure and field correctness
    - _Requirements: 7.1, 7.2, 7.3_

  - [x]* 5.6 Write property test: JSON serialization round-trip (Property 11)
    - **Property 11: JSON serialization round-trip**
    - Generate random DuplicateResults, produce JSON output, deserialize back, verify wastedSpace and fileList fields match original data
    - **Validates: Requirements 7.1, 7.2**

- [x] 6. Checkpoint - Ensure output format tests pass
  - Ensure all tests pass, ask the user if questions arise.


- [x] 7. Verify FindDuplicatesCommand execution and safety
  - [x] 7.1 Verify FindDuplicatesCommand execution flow
    - Review `FindDuplicatesCommand.execute()` for: dontHash rejection, use-last-state vs new state, output type dispatch, hash mode compatibility logging
    - Verify existing `FindDuplicatesCommandTest` covers these flows; add missing tests
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

  - [x]* 7.2 Write property test: dontHash mode rejection (Property 18)
    - **Property 18: dontHash mode rejection**
    - Generate random Contexts with HashMode set to `dontHash`, verify `FindDuplicatesCommand.execute()` throws `BadFimUsageException` with "File content hashing mandatory for this command"
    - **Validates: Requirements 1.1**

  - [x]* 7.3 Write property test: Remove with non-human output rejection (Property 12)
    - **Property 12: Remove with non-human output rejection**
    - Generate Contexts with non-human OutputType (csv or json) and `removeDuplicates=true`, verify `FindDuplicatesCommand.execute()` throws `BadFimUsageException`
    - **Validates: Requirements 8.2**

  - [x] 7.4 Verify safety confirmation for automatic removal
    - Review `FindDuplicatesCommand.explicitlyConfirmAutomaticRemoval()` for: temporary alwaysYes disable, confirmation prompt display, abort on decline, calledFromTest skip, and restoration of alwaysYes after the answer so that `-y` still applies to the duplicate sets
    - Add unit tests for safety confirmation behavior
    - _Requirements: 9.1, 9.2, 9.3_

- [x] 8. Verify interactive duplicate removal logic
  - [x] 8.1 Verify DuplicateResult interactive removal methods
    - Review `DuplicateResult.selectFilesToRemove()` and `manageAnswers()` for: alwaysYes marks all except first, numeric index preservation, "all"/"a" preserves all, "none"/"n" removes all, multi-index support
    - Add unit tests for each answer type and edge cases (invalid input, out-of-range indices)
    - _Requirements: 8.3, 8.4, 8.5, 8.6, 8.7, 8.8, 8.9, 8.10_

  - [x]* 8.2 Write property test: AlwaysYes marks all except first (Property 13)
    - **Property 13: AlwaysYes marks all except first for removal**
    - Generate random DuplicateSets, call `selectFilesToRemove()` with `alwaysYes=true`, verify first file has `toRemove=false` and all others have `toRemove=true`
    - **Validates: Requirements 8.3**

  - [x]* 8.3 Write property test: Preserve by index (Property 14)
    - **Property 14: Preserve by index**
    - Generate random DuplicateSets and valid index sets, call `manageAnswers()` with numeric indices, verify only specified files have `toRemove=false`
    - **Validates: Requirements 8.5, 8.6**

  - [x]* 8.4 Write property test: "all" preserves all files (Property 15)
    - **Property 15: "all" preserves all files**
    - Generate random DuplicateSets, call `manageAnswers()` with "all" or "a", verify no files have `toRemove=true`
    - **Validates: Requirements 8.7**

  - [x]* 8.5 Write property test: "none" removes all files (Property 16)
    - **Property 16: "none" removes all files**
    - Generate random DuplicateSets, call `manageAnswers()` with "none" or "n", verify all files have `toRemove=true`
    - **Validates: Requirements 8.8**


- [x] 9. Verify RemoveDuplicatesCommand and master repository logic
  - [x] 9.1 Verify RemoveDuplicatesCommand local and master modes
    - Review `RemoveDuplicatesCommand.execute()` for: local mode delegation to FindDuplicatesCommand with removeDuplicates=true, master mode state loading, hash comparison, per-file confirmation, summary display
    - Verify existing `RemoveDuplicatesCommandTest` covers these flows; add missing tests
    - _Requirements: 8.1, 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7, 10.8_

  - [x]* 9.2 Write property test: Master repository hash matching with zero-length exclusion (Property 17)
    - **Property 17: Master repository hash matching with zero-length exclusion**
    - Generate random local FileStates and master States, verify a local file is identified as duplicate iff its FileHash matches a master entry and its fileLength > 0
    - **Validates: Requirements 10.3, 10.8**

  - [x] 9.3 Verify master repository validation
    - Review `RemoveDuplicatesCommand.execute()` for: non-existent master dir, missing `.fim` dir, current dir equals master, current dir is subdirectory of master, reduced hash mode warning
    - Add unit tests for each validation error case
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5_

- [x] 10. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- All core components already exist in the codebase; tasks focus on verification and testing
- Property tests validate the 18 universal correctness properties from the design document
- Correctness properties are checked with JUnit 5 `@ParameterizedTest`; the project has no property-based testing library
- Each property test references its design document property number and validated requirements
- Checkpoints ensure incremental validation of detection engine, output formats, and removal logic
