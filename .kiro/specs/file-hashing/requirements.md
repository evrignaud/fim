# Requirements Document

## Introduction

This document specifies the requirements for the File Hashing System of FIM (File Integrity Manager). The File Hashing System is the multi-mode, multi-threaded subsystem responsible for computing cryptographic hashes of file contents. It supports four hash modes ranging from no hashing to full SHA-512 hashing, uses a block-based strategy to sample files at strategic positions for faster modes, and employs dynamic thread scaling to optimize disk throughput. The system produces a three-level FileHash (small block, medium block, full) for each file, enabling flexible trade-offs between speed and integrity coverage.

## Glossary

- **FIM**: File Integrity Manager — a Java CLI tool for managing file integrity of complete file trees.
- **Hash_Mode**: An enumeration controlling which hash levels are computed. Values: dontHash, hashSmallBlock, hashMediumBlock, hashAll.
- **FileHash**: A model object containing three hash strings: smallBlockHash, mediumBlockHash, and fullHash.
- **NO_HASH**: A sentinel string constant ("no_hash") used for hash levels that are not computed under the active Hash_Mode.
- **SHA-512**: The cryptographic hash algorithm used by all hashers in the system.
- **Ascii85**: The encoding scheme used to convert raw SHA-512 digest bytes into a compact printable string representation.
- **FileHasher**: The orchestrator component that runs as a thread, reads file paths from a BlockingDeque, coordinates all hasher types, and produces FileState objects with computed FileHash values.
- **FrontHasher**: The coordinating hasher that delegates to SmallBlockHasher, MediumBlockHasher, and FullHasher, merging their byte ranges and dispatching buffer updates.
- **AbstractHasher**: The base class providing common SHA-512 digest management, byte counting, throughput tracking, and the template method pattern for subclass-specific block selection.
- **BlockHasher**: The base class for block-based hashers (SmallBlockHasher, MediumBlockHasher) that samples three blocks at the beginning, middle, and end of a file.
- **SmallBlockHasher**: A BlockHasher that hashes three 4 KB blocks at strategic file positions.
- **MediumBlockHasher**: A BlockHasher that hashes three 1 MB blocks at strategic file positions.
- **FullHasher**: A hasher that hashes the entire file content in 30 MB chunks.
- **FileReHasher**: A specialized FileHasher that re-hashes previously scanned files when the Hash_Mode changes between commits.
- **HashProgress**: The component that tracks and displays hashing progress using character-based indicators scaled by cumulative file size.
- **ThroughputKeeper**: A sliding-window throughput monitor that computes instantaneous bytes-per-second rates for each hasher.
- **DynamicScaling**: The component that adjusts the thread pool size based on observed disk throughput, scaling up when throughput improves.
- **Context**: The execution context carrying runtime parameters including Hash_Mode, thread count, dynamic scaling settings, and repository paths.
- **Range**: A model representing a byte range [from, to) within a file, used to coordinate which portions of a file each hasher needs to read.
- **Block_Index**: A zero-based index identifying which block (of the configured block size) within a file a BlockHasher targets.
- **Memory_Mapped_Buffer**: A MappedByteBuffer obtained via FileChannel.map() used for efficient file I/O during hashing.

## Requirements

### Requirement 1: Hash Mode Selection

**User Story:** As a user, I want to select a hash mode for file integrity operations, so that I can trade off between hashing speed and integrity coverage.

#### Acceptance Criteria

1. THE Hash_Mode enumeration SHALL define exactly four values in order: dontHash, hashSmallBlock, hashMediumBlock, hashAll.
2. WHEN the Hash_Mode is set to dontHash, THE FileHasher SHALL return a FileHash with all three hash levels set to NO_HASH without reading file content.
3. WHEN the Hash_Mode is set to hashSmallBlock, THE FrontHasher SHALL activate the SmallBlockHasher and deactivate the MediumBlockHasher and FullHasher.
4. WHEN the Hash_Mode is set to hashMediumBlock, THE FrontHasher SHALL activate the SmallBlockHasher and MediumBlockHasher and deactivate the FullHasher.
5. WHEN the Hash_Mode is set to hashAll, THE FrontHasher SHALL activate the SmallBlockHasher, MediumBlockHasher, and FullHasher.
6. FOR EACH inactive hasher, THE AbstractHasher SHALL return NO_HASH as the hash value and zero as the bytes hashed count.

### Requirement 2: Hash Mode Compatibility

**User Story:** As a developer, I want hash mode compatibility to be well-defined, so that the system correctly determines which hashers are active for a given mode.

#### Acceptance Criteria

1. WHEN the Hash_Mode is hashAll, THE HashModeUtil SHALL report compatibility with hashSmallBlock, hashMediumBlock, and hashAll.
2. WHEN the Hash_Mode is hashMediumBlock, THE HashModeUtil SHALL report compatibility with hashSmallBlock and hashMediumBlock, and incompatibility with hashAll.
3. WHEN the Hash_Mode is hashSmallBlock, THE HashModeUtil SHALL report compatibility with hashSmallBlock only, and incompatibility with hashMediumBlock and hashAll.
4. WHEN the Hash_Mode is dontHash, THE HashModeUtil SHALL report compatibility with dontHash only.
5. THE compatibility relationship SHALL be transitive downward: a higher Hash_Mode activates all hashers that a lower Hash_Mode activates.

### Requirement 3: Full File Hashing

**User Story:** As a user, I want to hash the entire content of files, so that I can detect any modification regardless of its position within the file.

#### Acceptance Criteria

1. WHEN the FullHasher is active, THE FullHasher SHALL hash the complete file content from byte 0 to the end of the file.
2. THE FullHasher SHALL read the file in chunks of 30 MB (30 × 1,048,576 bytes) to manage memory usage.
3. WHEN the FullHasher completes, THE FullHasher SHALL report hashComplete as true only when the total bytes hashed equals the file size.
4. THE FullHasher SHALL compute a SHA-512 digest over all file bytes and encode the result using Ascii85.
5. WHEN the FullHasher is inactive, THE FullHasher SHALL return NO_HASH and report hashComplete as true.

### Requirement 4: Small Block Hashing

**User Story:** As a user, I want a super-fast hashing mode that samples small blocks from strategic file positions, so that I can quickly detect common modifications.

#### Acceptance Criteria

1. WHEN the SmallBlockHasher is active, THE SmallBlockHasher SHALL use a block size of 4 KB (4,096 bytes).
2. WHEN the file size exceeds four times the block size (16 KB), THE SmallBlockHasher SHALL hash three blocks: block at index 1 (skipping the header block), the middle block, and the last block.
3. WHEN the file size exceeds three times the block size (12 KB) but does not exceed four times the block size, THE SmallBlockHasher SHALL hash two blocks: block at index 1 and the last block.
4. WHEN the file size exceeds two times the block size (8 KB) but does not exceed three times the block size, THE SmallBlockHasher SHALL hash one block at index 1.
5. WHEN the file size does not exceed two times the block size (8 KB), THE SmallBlockHasher SHALL hash one block at index 0 (the beginning of the file).
6. THE SmallBlockHasher SHALL compute a SHA-512 digest over the selected blocks and encode the result using Ascii85.
7. WHEN the SmallBlockHasher completes, THE SmallBlockHasher SHALL report hashComplete as true only when the total bytes hashed equals the expected size to hash across all selected blocks.

### Requirement 5: Medium Block Hashing

**User Story:** As a user, I want a fast hashing mode that samples medium-sized blocks from strategic file positions, so that I can detect modifications with higher confidence than small block hashing.

#### Acceptance Criteria

1. WHEN the MediumBlockHasher is active, THE MediumBlockHasher SHALL use a block size of 1 MB (1,048,576 bytes).
2. WHEN the file size exceeds four times the block size (4 MB), THE MediumBlockHasher SHALL hash three blocks: block at index 1 (skipping the header block), the middle block, and the last block.
3. WHEN the file size exceeds three times the block size (3 MB) but does not exceed four times the block size, THE MediumBlockHasher SHALL hash two blocks: block at index 1 and the last block.
4. WHEN the file size exceeds two times the block size (2 MB) but does not exceed three times the block size, THE MediumBlockHasher SHALL hash one block at index 1.
5. WHEN the file size does not exceed two times the block size (2 MB), THE MediumBlockHasher SHALL hash one block at index 0 (the beginning of the file).
6. THE MediumBlockHasher SHALL compute a SHA-512 digest over the selected blocks and encode the result using Ascii85.
7. WHEN the MediumBlockHasher completes, THE MediumBlockHasher SHALL report hashComplete as true only when the total bytes hashed equals the expected size to hash across all selected blocks.


### Requirement 6: Block Position Strategy

**User Story:** As a developer, I want block-based hashers to skip file headers when possible, so that the hash is less susceptible to collisions caused by common file headers.

#### Acceptance Criteria

1. WHEN a file is large enough to contain more than two blocks, THE BlockHasher SHALL start hashing from block index 1 (skipping the first block) to avoid header bytes that may be identical across files of the same type.
2. WHEN a file is too small to skip the first block (file size does not exceed two times the block size), THE BlockHasher SHALL hash starting from block index 0.
3. THE BlockHasher SHALL compute the middle block index as half of the total number of blocks in the file (file size divided by block size, divided by 2).
4. THE BlockHasher SHALL compute the end block index as the total number of blocks minus one (file size divided by block size, minus 1).
5. FOR EACH selected block, THE BlockHasher SHALL clamp the byte range to the file size, ensuring the range does not extend beyond the end of the file.

### Requirement 7: FrontHasher Range Coordination

**User Story:** As a developer, I want the FrontHasher to coordinate byte ranges across all active hashers, so that file I/O is minimized by reading contiguous or overlapping regions together.

#### Acceptance Criteria

1. WHEN all three hashers are active (hashAll mode), THE FrontHasher SHALL compute the next range by starting with the FullHasher range and adjusting it to encompass any overlapping SmallBlockHasher and MediumBlockHasher ranges.
2. WHEN only SmallBlockHasher and MediumBlockHasher are active (hashMediumBlock mode), THE FrontHasher SHALL return the earlier range when the two ranges do not overlap, and the union of both ranges when they do overlap.
3. WHEN only the SmallBlockHasher is active (hashSmallBlock mode), THE FrontHasher SHALL return the SmallBlockHasher range directly.
4. WHEN the FrontHasher dispatches a buffer update, THE FrontHasher SHALL forward the buffer to each active hasher while preserving the buffer position and limit between calls, restoring them after each hasher even when that hasher raises an exception.
5. WHEN a hasher receives a buffer update, THE AbstractHasher SHALL extract only the bytes relevant to its next block range from the buffer and feed them to the SHA-512 digest.
6. WHEN no hasher is active, THE FrontHasher SHALL raise a FimInternalError from its range coordination. That state corresponds to dontHash, which never reaches this point because the FileHasher returns an all-NO_HASH FileHash before opening the file.
7. THE FrontHasher `isActive` SHALL report the conjunction of the three hashers, and is therefore true only in hashAll mode. It does not indicate whether there is work to do, and the FileHasher does not consult it.
8. THE FrontHasher SHALL raise a FimInternalError from the single-value hash accessor of the Hasher interface, since a combined hash string has no meaning for it. Callers SHALL obtain the three-part FileHash instead.
9. THE FrontHasher SHALL report bytes hashed, total bytes hashed and instant throughput as the maximum across the three hashers rather than the sum, because in hashAll mode the full hasher reads every byte the block hashers read.
10. WHEN only one of the block hashers still has a range left, THE FrontHasher SHALL return that range, and SHALL return nothing only once both have run out.

### Requirement 8: FileHash Model

**User Story:** As a developer, I want a three-level hash model, so that hash results from different modes can be stored and compared uniformly.

#### Acceptance Criteria

1. THE FileHash SHALL contain exactly three fields: smallBlockHash, mediumBlockHash, and fullHash.
2. THE FileHash SHALL intern all hash strings to reduce memory usage when many files share the same NO_HASH sentinel.
3. WHEN two FileHash objects are compared for equality, THE FileHash SHALL compare all three hash fields.
4. THE FileHash SHALL implement Comparable by comparing smallBlockHash first, then mediumBlockHash, then fullHash in lexicographic order.
5. THE FileHash SHALL implement Hashable to support State-level integrity hashing by contributing all three hash fields with field separators.
6. FOR ALL valid FileHash objects, cloning a FileHash SHALL produce a new FileHash with identical smallBlockHash, mediumBlockHash, and fullHash values.

### Requirement 9: Multi-Threaded File Hashing

**User Story:** As a user, I want files to be hashed using multiple threads, so that hashing large file trees completes faster by utilizing available CPU and I/O bandwidth.

#### Acceptance Criteria

1. THE FileHasher SHALL run as a Runnable that polls file paths from a shared BlockingDeque.
2. WHEN the file queue is empty and the scan is still in progress, THE FileHasher SHALL wait 500 milliseconds before re-checking the queue.
3. WHEN the file queue is empty and the scan is complete, THE FileHasher SHALL terminate and signal HashProgress that no more files remain.
4. FOR EACH file polled from the queue, THE FileHasher SHALL read file attributes (POSIX permissions on Unix, DOS permissions on Windows), compute the FileHash, and produce a FileState.
5. WHEN an error occurs while hashing a file, THE FileHasher SHALL log the error with the file path and continue processing remaining files without terminating the thread.
6. THE FileHasher SHALL use memory-mapped I/O (FileChannel.map) to read file content into buffers for hashing.
7. THE FileHasher SHALL attempt to explicitly unmap Memory_Mapped_Buffers after use to prevent OutOfMemoryError during large file tree scans, releasing each buffer as soon as it has been hashed and without forcing a garbage collection.
8. THE FileHasher SHALL unmap through `sun.misc.Unsafe.invokeCleaner`, reached by reflection, which is the mechanism available from Java 9 onwards.
9. THE FileHasher SHALL attempt the reflective lookup once per instance and remember its outcome, so that a JVM not offering it costs one failed lookup rather than one per buffer.
10. IF the lookup or the invocation fails, or the buffer is null or not direct, THEN THE FileHasher SHALL skip unmapping and leave the buffer to the garbage collector, without reporting an error.
11. THE FileHasher SHALL unmap in a finally block, so that a failed hash still releases its buffer.
12. THE FileHasher SHALL signal HashProgress that no more files remain from a finally block, so that the signal is sent whether the thread completed normally or was interrupted.
13. WHEN the hashing thread is interrupted, THE FileHasher SHALL restore the interrupt flag of the thread before reporting the error.

### Requirement 10: File Re-Hashing

**User Story:** As a user, I want files to be re-hashed when I change the hash mode between commits, so that the new state contains hashes computed with the current mode.

#### Acceptance Criteria

1. WHEN the Hash_Mode changes between the previous state and the current operation, THE FileReHasher SHALL re-hash files from the previous state using the new Hash_Mode.
2. THE FileReHasher SHALL read FileState objects from a dedicated BlockingDeque rather than file paths.
3. THE FileReHasher SHALL resolve each file path relative to the repository root directory and compute a new FileHash for the file.
4. WHEN an error occurs during re-hashing, THE FileReHasher SHALL log the error and continue processing remaining files.
5. WHEN the re-hash queue is empty, THE FileReHasher SHALL terminate and signal HashProgress that no more files remain.

### Requirement 11: Hash Progress Display

**User Story:** As a user, I want to see progress indicators during hashing, so that I can monitor the operation on large file trees.

#### Acceptance Criteria

1. WHEN verbose mode is enabled and Hash_Mode is not dontHash, THE HashProgress SHALL display character-based progress indicators during hashing.
2. THE HashProgress SHALL output a progress character every 10 files processed, where the character represents the cumulative file size of those 10 files.
3. THE HashProgress SHALL use the following size-based character scale: '.' for sizes below 20 MB, 'o' for 20 MB to 50 MB, '8' for 50 MB to 100 MB, 'O' for 100 MB to 200 MB, '@' for 200 MB to 1,000 MB, '#' for sizes at or above 1,000 MB (using decimal megabyte thresholds: 20,000,000 / 50,000,000 / 100,000,000 / 200,000,000 / 1,000,000,000 bytes).
4. THE HashProgress SHALL wrap progress output to a new line based on the terminal width (90% of terminal columns on non-Windows systems, default 100 characters).
5. WHEN verbose mode is disabled or Hash_Mode is dontHash, THE HashProgress SHALL suppress all progress output.
6. THE HashProgress SHALL provide a hashLegend method that returns a human-readable description of the progress character scale.
7. THE HashProgress SHALL carry the "hashing has started" signal as a boolean flag, separate from the "no more files" signal. The StateGenerator reads it to start the hasher threads exactly once, either when the queue passes half its capacity during the scan or after the scan finishes.
8. THE HashProgress SHALL carry the "no more files" signal as a one-shot latch, so that the first FileHasher thread to find the queue empty releases the wait and any later signal from the remaining threads is a no-op.
9. WHEN the thread waiting for hashing to finish is interrupted, THE HashProgress SHALL restore the interrupt flag of that thread and return.

### Requirement 12: Throughput Monitoring

**User Story:** As a developer, I want per-hasher throughput monitoring, so that the dynamic scaling system can make informed decisions about thread count adjustments.

#### Acceptance Criteria

1. THE ThroughputKeeper SHALL maintain a sliding window of 500 MB to compute instantaneous throughput in bytes per second.
2. THE ThroughputKeeper SHALL use a half-block checkpoint at 250 MB to enable smooth window transitions.
3. WHEN the sliding window exceeds 500 MB of accumulated data, THE ThroughputKeeper SHALL shift the window to the half-block checkpoint, discarding the older half.
4. WHEN the measurement duration is less than 1 second, THE ThroughputKeeper SHALL extrapolate the throughput to a per-second rate.
5. THE ThroughputKeeper SHALL report a minimum duration of 1 millisecond to avoid division by zero.
6. THE AbstractHasher SHALL create a ThroughputKeeper only when dynamic scaling is enabled, and SHALL feed it only under the same condition.
7. THE instant throughput accessor of a hasher SHALL be defined only while dynamic scaling is enabled, since it does not repeat that check. No production path reads it: the chain from FileHasher through FrontHasher to AbstractHasher exists but has no caller, so this is a precondition on the accessor rather than a reachable fault.

### Requirement 13: Dynamic Thread Scaling

**User Story:** As a user, I want the system to automatically adjust the number of hashing threads, so that disk throughput is optimized without manual tuning.

#### Acceptance Criteria

1. WHEN dynamic scaling is enabled, THE DynamicScaling SHALL start with the configured thread count (or 1 if not specified) and adjust upward based on throughput measurements.
2. THE DynamicScaling SHALL measure throughput at intervals of 100 milliseconds by tracking the volume of bytes processed.
3. WHEN throughput improves by more than 2% compared to the previous measurement, THE DynamicScaling SHALL increase the thread count by one, up to the maximum.
4. THE DynamicScaling SHALL set the maximum thread count to the greater of 4 or twice the number of available CPU cores.
5. THE DynamicScaling SHALL set the minimum thread count to 1.
6. WHEN dynamic scaling is disabled via the Context, THE DynamicScaling SHALL use a fixed thread count and perform no throughput-based adjustments.
7. WHEN the thread count has already reached the maximum, THE DynamicScaling SHALL skip throughput measurement and adjustment.
8. WHEN taking the first measurement, where no previous throughput is known, THE DynamicScaling SHALL record it without adjusting the thread count, since a change can only be computed against a previous measurement.
9. THE DynamicScaling SHALL only ever increase the thread count. The minimum is the floor of the initial value and is never used to reduce a count that has already grown.
10. THE DynamicScaling SHALL express throughput in bytes per second, both in the measurement it compares and in the value it exposes.
11. THE DynamicScaling SHALL recheck the elapsed interval under its lock before measuring, so that concurrent hashing threads produce one measurement per interval rather than one each.
12. WHEN a measurement is taken, THE DynamicScaling SHALL reset the accumulated volume, so that each measurement covers only its own interval.

### Requirement 14: Hash Algorithm and Encoding

**User Story:** As a developer, I want all hashers to use a consistent algorithm and encoding, so that hash values are uniform and comparable across all hash levels.

#### Acceptance Criteria

1. THE AbstractHasher SHALL use the SHA-512 algorithm (via java.security.MessageDigest) for all hash computations.
2. THE AbstractHasher SHALL encode the final SHA-512 digest bytes using Ascii85 encoding to produce the hash string.
3. WHEN a hasher is reset for a new file, THE AbstractHasher SHALL reset the MessageDigest state and the per-file bytes hashed counter while preserving the cumulative total bytes hashed.
4. THE AbstractHasher SHALL track both per-file bytes hashed and cumulative total bytes hashed across all files processed by the hasher instance.

### Requirement 15: Hash Completion Verification

**User Story:** As a developer, I want the system to verify that all active hashers have completed before producing a FileHash, so that partial or incomplete hashes are never stored.

#### Acceptance Criteria

1. WHEN all active hashers report hashComplete as true, THE FrontHasher SHALL produce a FileHash from the three hasher results.
2. IF any active hasher reports hashComplete as false after processing the entire file, THEN THE FileHasher SHALL raise a FimInternalError identifying which hashers did not complete and the file path and size.
3. FOR the FullHasher, hashComplete SHALL be true when bytes hashed equals the file size.
4. FOR BlockHasher subclasses, hashComplete SHALL be true when bytes hashed equals the sum of all selected block ranges.
5. FOR inactive hashers, hashComplete SHALL always be true.
