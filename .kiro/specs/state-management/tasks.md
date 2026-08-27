# Implementation Plan: State Management

## Overview

The State Management core components already exist in the codebase (`StateManager`, `StateGenerator`, `StateComparator`, `SettingsManager`, `InitCommand`, `CommitCommand`, `RollbackCommand`, `LogCommand`, `PurgeStatesCommand`, `State`, `FileState`, `FileHash`, `FileTime`, `ModificationCounts`, `Context`). This plan focuses on verifying correctness against requirements, extending existing unit tests, and implementing parameterized tests (using JUnit 5 `@ParameterizedTest`) for the 14 correctness properties defined in the design document.

## Tasks

- [x] 1. Verify State persistence and serialization
  - [x] 1.1 Verify State GZIP serialization, field completeness, and sort invariant
    - Review `State.saveToGZipFile()` for GZIP-compressed JSON output via Jackson ObjectMapper
    - Review `State.loadFromGZipFile()` for correct deserialization and optional integrity verification
    - Verify FileState entries are sorted by fileName before serialization
    - Verify `fileCount` and `filesContentLength` are computed before save, and recomputed on every read, counting only the entries not marked `deleted`
    - Verify `stateHash` is computed via SHA-512/Ascii85 over all content fields
    - Add unit tests for round-trip save/load with various State configurations
    - _Requirements: 3.1, 3.2, 3.3, 7.1, 7.2, 7.3_

  - [x]* 1.2 Write property test: State serialization round-trip (Property 1)
    - **Property 1: State serialization round-trip**
    - Generate random State objects with arbitrary FileState entries, save to GZIP file, load back, verify equality
    - **Validates: Requirements 7.1, 7.2**

  - [x]* 1.3 Write property test: FileState sort invariant on save (Property 4)
    - **Property 4: FileState sort invariant on save**
    - Generate State objects with randomly ordered FileState entries, save, verify persisted entries are sorted by fileName
    - **Validates: Requirements 3.2**

- [x] 2. Verify State hash integrity and corruption detection
  - [x] 2.1 Verify State hash computation and integrity verification on load
    - Review `State.hashState()` for SHA-512 computation over modelVersion, timestamp, comment, fileCount, filesContentLength, hashMode, ignoredFiles (iterated as stored, never reordered), and all FileState entries. The two counts are taken from the stored fields, so loading an existing State still hashes the values it was written with
    - Review `State.loadFromGZipFile()` with full validation for hash verification and `CorruptedStateException` on mismatch
    - Review `StateManager.loadState()` for hash mode adjustment of FileHash fields
    - Add unit tests for successful integrity verification and corrupted state detection
    - _Requirements: 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9_

  - [x]* 2.2 Write property test: State hash integrity verification (Property 2)
    - **Property 2: State hash integrity verification**
    - Generate random State objects, save to GZIP, load with full validation, verify no CorruptedStateException
    - **Validates: Requirements 3.3, 3.7**

  - [x]* 2.3 Write property test: Corrupted state detection (Property 3)
    - **Property 3: Corrupted state detection**
    - Generate random State files, modify a byte in the decompressed JSON, verify loading with full validation raises CorruptedStateException
    - **Validates: Requirements 3.8**

- [x] 3. Verify StateManager numbering and settings persistence
  - [x] 3.1 Verify StateManager state numbering, settings tracking, and self-healing
    - Review `StateManager.createNewState()` for incrementing state number and saving to correct file
    - Review `StateManager.getLastStateNumber()` for reading from settings and self-healing when desynchronized
    - Review `StateManager.saveLastStateNumber()` for persisting to settings.json
    - Review `SettingsManager` for Gson-based serialization of globalHashMode and lastStateNumber
    - Add unit tests for state number increment, settings persistence, and self-healing scenarios
    - _Requirements: 3.10, 3.11_

  - [x]* 3.2 Write property test: State number monotonic increment (Property 5)
    - **Property 5: State number monotonic increment**
    - Perform N sequential `createNewState()` calls, verify last state number equals initial + N and all state files exist
    - **Validates: Requirements 2.3, 3.10**

  - [x]* 3.3 Write property test: State number self-healing (Property 13)
    - **Property 13: State number self-healing**
    - Create states, manually desynchronize settings lastStateNumber, call `getLastStateNumber()`, verify it returns the actual highest state number from disk
    - **Validates: Requirements 3.11**

- [x] 4. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Verify InitCommand behavior
  - [x] 5.1 Verify InitCommand repository creation and first state generation
    - Review `InitCommand.execute()` for creating `.fim` and `.fim/states` directories, and confirm they are created only after the scan has completed
    - Review StateGenerator invocation for full file tree scan during init
    - Verify default comment "Initial State" when no `-m` option provided
    - Verify confirmation prompt when no comment provided
    - Verify error when `.fim` directory already exists (MUST_NOT_EXIST constraint)
    - Verify the not-writable check falls back to a real write probe rather than trusting the reported permissions
    - Verify Hash_Mode persistence as Global_Hash_Mode in settings
    - Verify first state saved as `state_1.json.gz` with State_Number set to 1
    - Add unit tests for init with comment, init without comment, init on existing repo
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10, 1.11, 1.12_

  - [x]* 5.2 Write property test: Comment preservation (Property 6)
    - **Property 6: Comment preservation**
    - Generate random non-null comment strings, create State with comment, save and reload, verify comment field matches
    - **Validates: Requirements 1.3, 2.5**

  - [x]* 5.3 Write property test: State field completeness on creation (Property 14)
    - **Property 14: State field completeness on creation**
    - Generate random State objects via StateGenerator, verify modelVersion equals CURRENT_MODEL_VERSION, positive timestamp, fileCount equals the number of entries not marked `deleted`, filesContentLength equals the sum of their fileLengths, non-null hashMode and stateHash
    - **Validates: Requirements 1.11, 3.12, 3.13**

- [x] 6. Verify CommitCommand behavior
  - [x] 6.1 Verify CommitCommand state generation, comparison, and persistence
    - Review `CommitCommand.execute()` for generating new state, comparing with last state, and persisting when changes exist
    - Verify "Nothing committed" when no modifications detected
    - Verify confirmation prompt when no comment provided and abort on decline
    - Verify abort with "Computing hash is mandatory" when dontHash used with non-dontHash global mode
    - Verify abort with "Not allowed to ignore any aspect while committing" when aspects ignored
    - Verify hash mode mismatch handling: reload last state with global hash mode and retrieve missing hashes
    - Verify subdirectory commit consolidation: merge subdirectory scan with files outside subdirectory from last state
    - Verify subdirectory commit abort on model version mismatch
    - Verify purge-states option triggers purge after successful commit
    - Add unit tests for commit with changes, commit without changes, commit error conditions
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 2.11, 2.12, 2.13, 2.14_

  - [x]* 6.2 Write property test: No-op commit when unchanged (Property 7)
    - **Property 7: No-op commit when unchanged**
    - Set up repository, commit initial state, execute commit again without file changes, verify no new state file created and state number unchanged
    - **Validates: Requirements 2.4**

  - [x]* 6.3 Write property test: Subdirectory commit consolidation (Property 8)
    - **Property 8: Subdirectory commit consolidation**
    - Set up repository with files in multiple subdirectories, commit from a subdirectory, verify consolidated state contains subdirectory files plus files outside subdirectory from previous state
    - **Validates: Requirements 2.11**

  - [x]* 6.4 Write property test: ModificationCounts consistency (Property 9)
    - **Property 9: ModificationCounts consistency**
    - Generate two States with known differences, compare, verify ModificationCounts fields match CompareResult difference list sizes (except corrupted field which remains 0)
    - **Validates: Requirements 2.14**

- [x] 7. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Verify RollbackCommand behavior
  - [x] 8.1 Verify RollbackCommand state deletion and number decrement
    - Review `RollbackCommand.execute()` for identifying last state, displaying summary, confirmation prompt, and deletion
    - Verify state file deletion and state number decrement on confirmed rollback
    - Verify abort without modification on declined rollback
    - Verify "No commit to rollback" when only one state exists
    - Add unit tests for rollback with multiple states, rollback with one state, rollback declined
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [x]* 8.2 Write property test: Rollback decrements state count (Property 10)
    - **Property 10: Rollback decrements state count**
    - Set up repository with N states (N > 1), perform confirmed rollback, verify last state number is N-1, state_N.json.gz deleted, state_(N-1).json.gz still exists
    - **Validates: Requirements 4.3**

- [x] 9. Verify PurgeStatesCommand behavior
  - [x] 9.1 Verify PurgeStatesCommand deletion and renumbering
    - Review `PurgeStatesCommand.execute()` for identifying all states except the most recent, confirmation prompt, deletion, and renumbering
    - Verify all old state files deleted, last state renumbered to `state_1.json.gz`, state number set to 1
    - Verify abort without modification on declined purge
    - Verify "No State to purge" when only one state exists
    - Add unit tests for purge with multiple states, purge with one state, purge declined
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

  - [x]* 9.2 Write property test: Purge retains only last state (Property 11)
    - **Property 11: Purge retains only last state**
    - Set up repository with N states (N > 1), perform confirmed purge, verify only state_1.json.gz remains, all previous files deleted, state number set to 1
    - **Validates: Requirements 6.1, 6.3**

- [x] 10. Verify LogCommand behavior
  - [x] 10.1 Verify LogCommand state iteration and display
    - Review `LogCommand.execute()` for iterating states 1 through last state number
    - Verify display of state number, formatted timestamp, file count, files content length, hash mode, subdirectory info, comment, and modification summary
    - Verify "No State found" when no states exist
    - Verify states loaded without full integrity validation for performance
    - Add unit tests for log with multiple states, log with no states
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

  - [x]* 10.2 Write property test: Log entry completeness (Property 12)
    - **Property 12: Log entry completeness**
    - Generate State objects, construct log entries, verify each contains state number, non-null timestamp, correct file count, files content length, comment, and non-null ModificationCounts
    - **Validates: Requirements 5.1, 5.2, 5.3, 5.4**

- [x] 11. Resuming an interrupted init (GitHub issue #27)

  - [x] 11.1 Keep the hashes of a running init on disk
    - Add `InitProgressManager`, storing `.fim/init-progress.json.gz` as an ordinary State file so the hashes are read back through the existing integrity check
    - Write a temporary file and move it into place, so the file on disk is never a truncated mix of two saves
    - Save on a daemon thread at most a minute apart, and once more from a JVM shutdown hook
    - Discard progress that cannot be read, carries another Model_Version, fails its integrity check, or used another Hash_Mode
    - _Requirements: 1.14, 1.15, 1.17_

  - [x] 11.2 Reuse the hashes an interrupted init computed
    - `StateGenerator.setInitProgressManager()` opts a generation into reuse; a file whose recorded name, length and FileTime still match is never queued, so its content is not read
    - `FileHasher` holds its results in a synchronized list and hands out copies, and `StateGenerator` keeps its hashers in a `CopyOnWriteArrayList`, because the saving happens while the hashing goes on
    - Report how many hashes were reused, and keep the hashed byte count describing only what was actually read
    - _Requirements: 1.16_

  - [x] 11.3 Make `init` create the repository directory before it scans
    - Create `.fim` up front, keep `.fim/states` creation at the end so an unfinished init is not mistaken for a usable repository
    - Remove a `.fim` this run created when the init fails leaving nothing to resume
    - Do not record `.fim` among the ignored files of the State an init produces
    - _Requirements: 1.1, 1.12, 1.13, 1.21_

  - [x] 11.4 Let the CLI checks tell the three cases apart
    - `.fim/states` present means the repository already exists; `.fim` holding progress is a resume; `.fim` holding neither gets a message saying that removing it unblocks `init`
    - The other commands report the interrupted init rather than claiming the repository does not exist
    - _Requirements: 1.6, 1.19, 1.20_

  - [x] 11.5 Test the interruption and the resume
    - `InitProgressManagerTest`: round trip, reuse matching, discarding unusable progress, saving while the hashing goes on
    - `InitCommandTest`: resuming reuses only what still matches and leaves no progress behind
    - `FimWithoutReposTest`: the three CLI cases
    - **Covers Properties 15, 16, 17**
    - _Requirements: 1.14, 1.16, 1.17, 1.18, 1.19, 1.20_

- [x] 12. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- All core components already exist in the codebase; tasks focus on verification and testing
- Property tests validate universal correctness properties from the design document
- Correctness properties are checked with JUnit 5 `@ParameterizedTest`; the project has no property-based testing library
- Each property test references its design document property number and validated requirements
- Existing test classes (`StateManagerTest`, `StateGeneratorTest`, `StateComparatorTest`, `InitCommandTest`, `CommitCommandTest`, etc.) should be extended rather than replaced
