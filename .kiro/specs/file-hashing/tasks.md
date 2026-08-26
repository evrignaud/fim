# Implementation Plan: File Hashing System

## Overview

The File Hashing System core components already exist in the codebase (`AbstractHasher`, `BlockHasher`, `SmallBlockHasher`, `MediumBlockHasher`, `FullHasher`, `FrontHasher`, `FileHasher`, `FileReHasher`, `HashProgress`, `ThroughputKeeper`, `DynamicScaling`, `HashModeUtil`, `FileHash`, `Range`, `HashMode`). This plan focuses on verifying correctness against requirements, extending existing unit tests, and implementing parameterized tests (using JUnit 5 `@ParameterizedTest`) for the 20 correctness properties defined in the design document.

## Tasks

- [x] 1. Verify HashMode enum and hash mode selection logic
  - [x] 1.1 Verify HashMode enum and FrontHasher hasher activation
    - Review `org.fim.model.HashMode` for exactly 4 values in order: `dontHash`, `hashSmallBlock`, `hashMediumBlock`, `hashAll`
    - Review `FrontHasher` constructor to verify correct hasher activation per mode
    - Review `AbstractHasher.isActive()` and inactive hasher behavior (NO_HASH, 0 bytes, hashComplete=true)
    - Add unit tests to `FileHasherTest` or create new test class verifying dontHash returns all NO_HASH without reading file content
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_

  - [x]* 1.2 Write property test: Hash mode determines hasher activation (Property 1)
    - **Property 1: Hash mode determines hasher activation**
    - Generate random `HashMode` values, construct `FrontHasher`, verify correct set of active hashers per mode
    - **Validates: Requirements 1.3, 1.4, 1.5**

  - [x]* 1.3 Write property test: Inactive hasher invariants (Property 2)
    - **Property 2: Inactive hasher invariants**
    - Generate random `HashMode` values and hasher types, verify inactive hashers return NO_HASH, 0 bytes hashed, and hashComplete=true
    - **Validates: Requirements 1.6, 3.5, 15.5**

- [x] 2. Verify HashModeUtil compatibility logic
  - [x] 2.1 Verify HashModeUtil transitive compatibility
    - Review `org.fim.util.HashModeUtil.isCompatible()` against Requirements 2.1–2.5
    - Extend `HashModeUtilTest` with tests for all mode pairs and transitivity
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

  - [x]* 2.2 Write property test: Hash mode compatibility is transitive downward (Property 3)
    - **Property 3: Hash mode compatibility is transitive downward**
    - Generate all pairs of `HashMode` values A, B where A.ordinal() >= B.ordinal(), and for any X, verify if `isCompatible(B, X)` then `isCompatible(A, X)`
    - **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5**

- [x] 3. Verify BlockHasher block selection and range clamping
  - [x] 3.1 Verify BlockHasher block selection strategy and range clamping
    - Review `BlockHasher.reset()` block selection logic for all four file-size tiers
    - Review `SmallBlockHasher` block size (4096) and `MediumBlockHasher` block size (1048576)
    - Verify block index 1 skipping, middle index calculation, end index calculation
    - Verify range clamping to file size in `BlockHasher`
    - Extend `BlockHasherTest` with edge-case tests for boundary file sizes
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 5.1, 5.2, 5.3, 5.4, 5.5, 6.1, 6.2, 6.3, 6.4, 6.5_

  - [x]* 3.2 Write property test: Block selection strategy based on file size (Property 4)
    - **Property 4: Block selection strategy based on file size**
    - Generate random file sizes (1 byte to 100 MB), verify block count and indices for both `SmallBlockHasher` and `MediumBlockHasher` match the four-tier strategy
    - **Validates: Requirements 4.2, 4.3, 4.4, 4.5, 5.2, 5.3, 5.4, 5.5, 6.1, 6.2, 6.3, 6.4**

  - [x]* 3.3 Write property test: Block ranges are clamped to file size (Property 5)
    - **Property 5: Block ranges are clamped to file size**
    - Generate random file sizes, verify all selected `Range` objects satisfy `from >= 0` and `to <= fileSize`
    - **Validates: Requirements 6.5**

- [x] 4. Verify FullHasher behavior
  - [x] 4.1 Verify FullHasher chunk-based hashing and completion
    - Review `FullHasher.getNextRange()` for 30 MB chunk sizes
    - Review `FullHasher.hashComplete()` for `bytesHashed == fileSize` check
    - Review inactive FullHasher returning NO_HASH and hashComplete=true
    - Add unit tests for FullHasher with various file sizes (0 bytes, < 30 MB, exactly 30 MB, > 30 MB)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [x]* 4.2 Write property test: FullHasher completion invariant (Property 18)
    - **Property 18: FullHasher completion invariant**
    - Generate random file sizes, verify `hashComplete()` returns true iff `getBytesHashed() == fileSize`
    - **Validates: Requirements 3.1, 3.3, 15.3**

  - [x]* 4.3 Write property test: FullHasher range size is bounded (Property 20)
    - **Property 20: FullHasher range size is bounded**
    - Generate random file sizes and positions, verify `getNextRange()` returns ranges of at most 30 × 1,048,576 bytes
    - **Validates: Requirements 3.2**

- [x] 5. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Verify FrontHasher range coordination and buffer dispatch
  - [x] 6.1 Verify FrontHasher range merging and buffer dispatch
    - Review `FrontHasher.getNextRange()` range merging logic for all three modes (hashAll, hashMediumBlock, hashSmallBlock)
    - Review `FrontHasher.update()` buffer position/limit preservation between hasher calls
    - Add unit tests for range merging with overlapping and non-overlapping sub-hasher ranges
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [x]* 6.2 Write property test: FrontHasher range encompasses all active hasher ranges (Property 6)
    - **Property 6: FrontHasher range encompasses all active hasher ranges**
    - Generate random file sizes and positions, verify FrontHasher range contains all sub-hasher ranges (`frontRange.from <= R.from` and `frontRange.to >= R.to`)
    - **Validates: Requirements 7.1, 7.2, 7.3**

  - [x]* 6.3 Write property test: Buffer position and limit preservation (Property 7)
    - **Property 7: Buffer position and limit preservation during FrontHasher update**
    - Generate random `ByteBuffer` instances, call `FrontHasher.update()`, verify position and limit unchanged after call
    - **Validates: Requirements 7.4**

- [x] 7. Verify FileHash model
  - [x] 7.1 Verify FileHash equality, ordering, cloning, and interning
    - Review `FileHash` for three fields, `equals()`, `hashCode()`, `compareTo()`, `Hashable` implementation, `clone()`, and string interning
    - Extend `FileHashTest` with tests for lexicographic ordering, clone distinctness, and intern reference equality
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_

  - [x]* 7.2 Write property test: FileHash equality (Property 9)
    - **Property 9: FileHash equality compares all three fields**
    - Generate random `FileHash` pairs, verify `equals()` returns true iff all three fields match, and `hashCode()` consistency
    - **Validates: Requirements 8.3, 8.5**

  - [x]* 7.3 Write property test: FileHash ordering (Property 10)
    - **Property 10: FileHash ordering is lexicographic across three fields**
    - Generate random `FileHash` pairs, verify `compareTo()` follows smallBlockHash → mediumBlockHash → fullHash order
    - **Validates: Requirements 8.4**

  - [x]* 7.4 Write property test: FileHash clone (Property 11)
    - **Property 11: FileHash clone produces equal but distinct object**
    - Generate random `FileHash` objects, verify clone is `equals()` to original but `!=` reference
    - **Validates: Requirements 8.6**

  - [x]* 7.5 Write property test: FileHash string interning (Property 12)
    - **Property 12: FileHash string interning**
    - Generate random strings, set on `FileHash` fields, verify stored strings are reference-equal to their `intern()` result
    - **Validates: Requirements 8.2**

- [x] 8. Verify SHA-512/Ascii85 hash computation
  - [x] 8.1 Verify AbstractHasher SHA-512 algorithm and Ascii85 encoding
    - Review `AbstractHasher` for SHA-512 `MessageDigest` usage and `Ascii85Util` encoding
    - Review `reset()` behavior: digest reset, per-file counter reset, cumulative total preservation
    - Add unit tests verifying hash output against known SHA-512/Ascii85 values for small byte arrays
    - _Requirements: 14.1, 14.2, 14.3, 14.4_

  - [x]* 8.2 Write property test: Hash output matches SHA-512/Ascii85 oracle (Property 8)
    - **Property 8: Hash output matches independent SHA-512/Ascii85 oracle**
    - Generate random byte arrays, feed to hasher, compare with independently computed `Ascii85Encode(SHA512(bytes))`
    - **Validates: Requirements 3.4, 4.6, 5.6, 14.2**

  - [x]* 8.3 Write property test: Hasher reset preserves cumulative totals (Property 17)
    - **Property 17: Hasher reset preserves cumulative totals**
    - Generate random file sequences, verify after `reset()` that `getBytesHashed()` returns 0 while `getTotalBytesHashed()` equals sum of previous files
    - **Validates: Requirements 14.3, 14.4**

- [x] 9. Verify BlockHasher completion and hash completion verification
  - [x] 9.1 Verify hash completion logic across all hasher types
    - Review `FrontHasher.hashComplete()` aggregation of all active hashers
    - Review `FileHasher.hashFile()` for `FimInternalError` on incomplete hash
    - Extend `FileHasherTest` with test for error on incomplete hash scenario
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5_

  - [x]* 9.2 Write property test: BlockHasher completion invariant (Property 19)
    - **Property 19: BlockHasher completion invariant**
    - Generate random file sizes, verify `hashComplete()` returns true iff `getBytesHashed()` equals sum of all selected block range sizes
    - **Validates: Requirements 4.7, 5.7, 15.4**

- [x] 10. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Verify FileHasher and FileReHasher threading behavior
  - [x] 11.1 Verify FileHasher queue polling, error handling, and memory-mapped I/O
    - Review `FileHasher.run()` for BlockingDeque polling with 500ms wait, termination on scan complete, and `HashProgress.noMoreFileToHash()` signaling
    - Review `FileHasher.hashFile()` for file attribute reading (POSIX/DOS), memory-mapped I/O via `FileChannel.map()`, and explicit buffer unmapping
    - Review per-file error handling: IOException caught, logged, and processing continues
    - Extend `FileHasherTest` with tests for error resilience (file not found, permission denied scenarios)
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7_

  - [x] 11.2 Verify FileReHasher re-hashing behavior
    - Review `FileReHasher` for reading `FileState` from `BlockingDeque<FileState>`, resolving paths relative to repository root, and computing new `FileHash`
    - Review error handling and termination signaling
    - Add unit tests for FileReHasher path resolution and error handling
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

- [x] 12. Verify HashProgress display logic
  - [x] 12.1 Verify HashProgress character scale and display conditions
    - Review `HashProgress.getProgressChar()` for correct size-based character mapping using decimal thresholds
    - Review `HashProgress.isProgressDisplayed()` for verbose AND non-dontHash condition
    - Review progress output frequency (every 10 files) and line wrapping logic
    - Extend `HashProgressTest` with tests for all character thresholds and edge cases
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6_

  - [x]* 12.2 Write property test: Progress character matches file size scale (Property 13)
    - **Property 13: Progress character matches file size scale**
    - Generate random file sizes (0 to 2 GB), verify correct progress character per decimal threshold
    - **Validates: Requirements 11.3**

  - [x]* 12.3 Write property test: Progress display condition (Property 14)
    - **Property 14: Progress display condition**
    - Generate random verbose/hashMode combinations, verify `isProgressDisplayed()` returns true iff verbose=true AND hashMode != dontHash
    - **Validates: Requirements 11.1, 11.5**

- [x] 13. Verify ThroughputKeeper and DynamicScaling
  - [x] 13.1 Verify ThroughputKeeper sliding window and DynamicScaling bounds
    - Review `ThroughputKeeper` for 500 MB window, 250 MB half-block checkpoint, window shifting, sub-second extrapolation, and 1ms minimum duration
    - Review `DynamicScaling` for 100ms measurement interval, 2% throughput improvement threshold, thread count bounds (min=1, max=max(4, 2×cores)), and disabled mode
    - Add unit tests for ThroughputKeeper edge cases (zero duration, sub-second measurements)
    - Add unit tests for DynamicScaling bounds and disabled mode
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 13.1, 13.2, 13.3, 13.4, 13.5, 13.6, 13.7_

  - [x]* 13.2 Write property test: ThroughputKeeper minimum duration prevents division by zero (Property 15)
    - **Property 15: ThroughputKeeper minimum duration prevents division by zero**
    - Generate random sequences of `update()` calls, verify `getInstantThroughput()` never throws and always returns non-negative
    - **Validates: Requirements 12.5**

  - [x]* 13.3 Write property test: DynamicScaling thread count stays within bounds (Property 16)
    - **Property 16: DynamicScaling thread count stays within bounds**
    - Generate random sequences of `fileProcessed()` calls with various file sizes, verify `getCurrentThreadCount()` always in [1, max(4, 2×cores)]
    - **Validates: Requirements 13.3, 13.5, 13.6, 13.7**

- [x] 14. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- All core components already exist in the codebase; tasks focus on verification and testing
- Property tests validate universal correctness properties from the design document
- Correctness properties are checked with JUnit 5 `@ParameterizedTest`; the project has no property-based testing library
- Each property test references its design document property number and validated requirements
- Existing test classes (`FileHasherTest`, `BlockHasherTest`, `HashProgressTest`, `FileHashTest`, `RangeTest`, `HashModeUtilTest`) should be extended rather than replaced
