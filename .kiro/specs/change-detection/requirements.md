# Requirements Document

## Introduction

This document specifies the requirements for the Change Detection feature of FIM (File Integrity Manager). Change Detection is the core capability that compares the current file tree against a previously committed State to identify what has changed. It encompasses the `status` command (and its deprecated `diff` alias), the internal StateComparator comparison engine, and the CompareResult output formatting. The comparison pipeline detects nine categories of file changes: added, deleted, content-modified, date-modified, attribute-modified, renamed, copied, duplicated, and corrupted files.

## Glossary

- **FIM**: File Integrity Manager — a Java CLI tool for managing file integrity of complete file trees.
- **State**: A snapshot of the file tree containing FileState entries for all tracked files, as defined in the State Management spec.
- **FileState**: The metadata record for a single file, containing fileName, fileLength, fileTime, fileHash, fileAttributes, and modification type.
- **FileHash**: A composite hash object containing smallBlockHash, mediumBlockHash, and fullHash fields, used to identify file content.
- **Hash_Mode**: The hashing strategy used during state generation: hashAll, hashSmallBlock, hashMediumBlock, or dontHash.
- **StatusCommand**: The command (`status` / `st`) that compares the current directory state with the last committed State.
- **DiffCommand**: A deprecated command (`diff`) that delegates to the StatusCommand for backward compatibility.
- **StateComparator**: The internal comparison engine that takes a previous State and a current State and produces a CompareResult.
- **CompareResult**: The structured result of a comparison, containing categorized lists of Difference objects and a ModificationCounts summary.
- **Difference**: A pairing of a current FileState with an optional previous FileState, representing a single detected change.
- **Modification**: An enumeration of change categories: added, copied, duplicated, dateModified, contentModified, attributesModified, renamed, deleted, corrupted.
- **ModificationCounts**: A summary object containing the count of each Modification type detected during comparison.
- **Context**: The execution context carrying runtime parameters including Hash_Mode, ignored settings, truncateOutput, verbose flag, and directory paths.
- **Ignored**: A configuration object controlling which difference types to suppress: attributesIgnored, datesIgnored, renamedIgnored.
- **longHashCode**: A SHA-512-based long hash computed from a FileState's complete content (fileName, fileLength, fileTime, fileHash, fileAttributes), used for fast equality checks that avoid int hash collisions.
- **Multimap**: A hash-based lookup structure (keyed by FileHash) used by the StateComparator for O(1) file lookups during comparison.
- **Subdirectory_Filtering**: The process of filtering a State's FileState entries to include only files within a specific subdirectory, used when the command is invoked from a subdirectory of the repository.
- **Truncation**: The mechanism that limits the number of displayed differences per category to the configured truncateOutput value (default 200).

## Requirements

### Requirement 1: Status Command Execution

**User Story:** As a user, I want to run the `status` command to see what files have changed since my last commit, so that I can review modifications before committing.

#### Acceptance Criteria

1. WHEN the `status` command is executed, THE StatusCommand SHALL generate a new State from the current file tree and load the last committed State from the repository.
2. WHEN the `status` command is executed, THE StatusCommand SHALL pass both States to the StateComparator and display the resulting CompareResult.
3. WHEN the CompareResult contains no modifications, THE StatusCommand SHALL display "Nothing modified".
4. WHEN the `status` command is executed with the `-f` option, THE StatusCommand SHALL use hashMediumBlock as the Hash_Mode for state generation.
5. WHEN the `status` command is executed with the `-s` option, THE StatusCommand SHALL use hashSmallBlock as the Hash_Mode for state generation.
6. WHEN the `status` command is executed with the `-n` option, THE StatusCommand SHALL use dontHash as the Hash_Mode, comparing only file names, dates, and attributes.
7. WHEN the `status` command is invoked from a subdirectory of the repository, THE StatusCommand SHALL filter the last committed State to include only FileState entries within that subdirectory before comparison.
8. WHEN the Hash_Mode used for status differs from the Global_Hash_Mode and is compatible, THE StatusCommand SHALL log an informational message and proceed with the current Hash_Mode.

### Requirement 2: Diff Command Backward Compatibility

**User Story:** As a user who previously used the `diff` command, I want it to continue working, so that my existing scripts and habits are not broken.

#### Acceptance Criteria

1. WHEN the `diff` command is executed, THE DiffCommand SHALL display a deprecation message indicating that `diff` is deprecated and `status` should be used instead.
2. WHEN the `diff` command is executed, THE DiffCommand SHALL delegate to the StatusCommand execution logic and return the same CompareResult.
3. THE DiffCommand SHALL return an empty string for `getShortCmdName()` (no short alias for the deprecated command).

### Requirement 3: Unchanged File Detection

**User Story:** As a user, I want unchanged files to be quickly identified and excluded from further comparison, so that the status operation completes efficiently.

#### Acceptance Criteria

1. WHEN comparing two States, THE StateComparator SHALL build a hash-based lookup map from the previous State's FileState entries keyed by FileHash.
2. WHEN comparing two States, THE StateComparator SHALL use longHashCode to perform a fast initial comparison, identifying files that are completely unchanged (same fileName, fileLength, fileTime, fileHash, and fileAttributes).
3. WHEN a current FileState's longHashCode matches an entry in the previous State lookup, THE StateComparator SHALL classify that file as not modified and exclude it from further comparison phases.
4. WHEN a current FileState has the same fileName as a previous FileState and all content, time, and attribute fields are identical but the longHashCode did not match (due to hash collision avoidance), THE StateComparator SHALL classify that file as not modified during the same-filename matching phase rather than passing it to the hash-based difference search phase.

### Requirement 4: Content Modification Detection

**User Story:** As a user, I want to know which files have had their content changed, so that I can identify substantive modifications to my file tree.

#### Acceptance Criteria

1. WHEN a current FileState has the same fileName as a previous FileState but a different FileHash or fileLength, THE StateComparator SHALL classify that file as contentModified.
2. WHEN a file is classified as contentModified, THE StateComparator SHALL record a Difference containing both the previous and current FileState.
3. WHEN a file is classified as contentModified, THE StateComparator SHALL update the previous FileState's newFileHash to the current FileHash for accurate subsequent duplicate detection.
4. WHEN a file is classified as contentModified in the same-filename matching phase, THE StateComparator SHALL record the previous file's name as consumed.
5. WHEN the hash-based difference search phase selects the origin for a current file and several previous files share that FileHash, THE StateComparator SHALL prefer a previous file that was not recorded as consumed, so that a still-unmodified previous file is chosen as the origin ahead of a contentModified one.
6. WHEN every previous file sharing that FileHash was recorded as consumed, THE StateComparator SHALL still select one of them as the origin, because a consumed previous file is a precondition of copied classification (see Requirement 8).

### Requirement 5: Date Modification Detection

**User Story:** As a user, I want to know which files have had their timestamps changed without content changes, so that I can identify metadata-only modifications.

#### Acceptance Criteria

1. WHEN a current FileState has the same fileName and same content (equal FileHash and fileLength) as a previous FileState but a different fileTime, THE StateComparator SHALL classify that file as dateModified.
2. WHEN a file is classified as dateModified, THE StateComparator SHALL record a Difference containing both the previous and current FileState with their respective timestamps.

### Requirement 6: Attribute Modification Detection

**User Story:** As a user, I want to know which files have had their file attributes changed without content or date changes, so that I can track permission and metadata modifications.

#### Acceptance Criteria

1. WHEN a current FileState has the same fileName, same content, and same fileTime as a previous FileState but different fileAttributes, THE StateComparator SHALL classify that file as attributesModified.
2. WHEN a file is classified as attributesModified, THE StateComparator SHALL record a Difference containing both the previous and current FileState with their respective attribute maps.

### Requirement 7: Renamed File Detection

**User Story:** As a user, I want to know which files have been renamed, so that I can track file movements without treating them as deletions and additions.

#### Acceptance Criteria

1. WHEN a current FileState has a different fileName but the same FileHash as a previous FileState that is no longer present at its original path, THE StateComparator SHALL classify that file as renamed.
2. WHEN the Hash_Mode is dontHash, THE StateComparator SHALL skip hash-based matching and not detect renamed files.
3. WHEN a file has zero length, THE StateComparator SHALL skip hash-based matching for that file and classify it as added.
4. WHEN the renamedIgnored flag is set in the Context, THE StateComparator SHALL count renamed files as not modified instead of reporting them as renamed.
5. WHEN a file is classified as renamed, THE StateComparator SHALL select the rename origin from previous files that are actually deleted (not found in the current state), ensuring the Difference links to the correct original file path.

### Requirement 8: Copied File Detection

**User Story:** As a user, I want to know which files are copies of existing files that have been modified since the last commit, so that I can distinguish copies from duplicates.

#### Acceptance Criteria

1. WHEN a current FileState has the same FileHash as a previous FileState, the previous FileState is still present at its original path, and the previous FileState's content has changed since the last commit (newFileHash differs from original FileHash), THE StateComparator SHALL classify the current file as copied.
2. WHEN a file is classified as copied, THE StateComparator SHALL record a Difference linking the current FileState to the original FileState it was copied from.
3. WHEN the only previous files sharing the current file's FileHash were consumed as contentModified in the same-filename matching phase, THE StateComparator SHALL classify the current file as copied rather than added, since a consumed previous file is the only way newFileHash can differ from the original FileHash.

### Requirement 9: Duplicated File Detection

**User Story:** As a user, I want to know which files are duplicates of existing unchanged files, so that I can identify redundant copies in my file tree.

#### Acceptance Criteria

1. WHEN a current FileState has the same FileHash as a previous FileState, the previous FileState is still present at its original path, and the previous FileState's content has not changed since the last commit, THE StateComparator SHALL classify the current file as duplicated.
2. WHEN a file is classified as duplicated, THE StateComparator SHALL record a Difference linking the current FileState to the original FileState it duplicates.

### Requirement 10: Added File Detection

**User Story:** As a user, I want to know which files are new additions to the file tree, so that I can track new content entering the repository.

#### Acceptance Criteria

1. WHEN a current FileState has no matching previous FileState by fileName or by FileHash, THE StateComparator SHALL classify that file as added.
2. WHEN a current FileState has zero fileLength and no matching fileName in the previous State, THE StateComparator SHALL classify that file as added regardless of hash matching.
3. WHEN the Hash_Mode is dontHash and a current FileState has no matching fileName in the previous State, THE StateComparator SHALL classify that file as added.

### Requirement 11: Deleted File Detection

**User Story:** As a user, I want to know which files have been removed from the file tree, so that I can track content that is no longer present.

#### Acceptance Criteria

1. WHEN a previous FileState has no matching current FileState after all other comparison phases complete, THE StateComparator SHALL classify that file as deleted.
2. WHEN a previous FileState's fileName matches an entry in the current State's ignored files list, THE StateComparator SHALL exclude that file from the deleted list.
3. WHEN a previous FileState's fileName starts with a directory prefix that appears in the current State's ignored files list (ending with "/"), THE StateComparator SHALL exclude that file from the deleted list.

### Requirement 12: Corrupted File Detection

**User Story:** As a user, I want to detect files whose content has changed without any timestamp modification, so that I can identify potential hardware corruption or tampering.

#### Acceptance Criteria

1. WHEN the StateComparator is operating in hardware corruption detection mode and a current FileState has the same fileName and same fileTime as a previous FileState but a different FileHash, THE StateComparator SHALL classify that file as corrupted.
2. WHILE the StateComparator is operating in hardware corruption detection mode, THE StateComparator SHALL skip the renamed, copied, duplicated, added, and deleted detection phases.

### Requirement 13: Comparison Pipeline Integrity

**User Story:** As a developer, I want the comparison algorithm to account for every file in both states, so that no files are silently lost or double-counted.

#### Acceptance Criteria

1. THE StateComparator SHALL verify, after the hash-based difference search and before the deletion sweep, that the sum of not-modified files and all modification counts equals the total file count in the current State.
2. IF the verification in criterion 1 fails, THEN THE StateComparator SHALL raise an IllegalStateException indicating a comparison algorithm error.
3. THE StateComparator SHALL verify that the addedOrModified working list is empty after the differences search phase completes.
4. IF the addedOrModified list is not empty after the differences search phase, THEN THE StateComparator SHALL raise an IllegalStateException.
5. THE verification in criterion 1 SHALL run before the deletion sweep because deleted files come from the previous State and are not part of the current State's file count. Once the sweep has run, the modification total exceeds the current file count by the number of deleted entries.
6. WHEN the last committed State holds FileState entries marked deleted, recorded by the commit that removed them, THE StateComparator SHALL drop them while making that State comparable, so that they take no part in the comparison.
7. THE file count the verification compares against SHALL exclude any entry marked deleted, as required by the State Management spec. At the point the verification runs, the current State carries no such entry, since the deletion sweep has not yet marked any.

### Requirement 14: Ignore Flags

**User Story:** As a user, I want to selectively ignore certain types of changes during comparison, so that I can focus on the modifications that matter to me.

#### Acceptance Criteria

1. WHEN the attributesIgnored flag is set, THE StateComparator SHALL clear all fileAttributes from both the current and previous State's FileState entries before comparison.
2. WHEN the datesIgnored flag is set, THE StateComparator SHALL set all fileTime values to zero in both the current and previous State's FileState entries before comparison.
3. WHEN the renamedIgnored flag is set, THE StateComparator SHALL treat renamed files as not modified instead of reporting them as renamed.
4. WHEN any ignore flag is active, THE CompareResult SHALL append a parenthetical note to the output indicating which aspects are being ignored.

### Requirement 15: Cross-Platform Compatibility

**User Story:** As a user, I want to compare states created on different operating systems, so that I can use FIM across platforms.

#### Acceptance Criteria

1. WHILE running on Windows, THE StateComparator SHALL filter out PosixFilePermissions attributes from the previous State before comparison.
2. WHILE running on a non-Windows OS, THE StateComparator SHALL filter out DosFilePermissions attributes from the previous State before comparison.
3. WHILE SELinux is not enabled on the current system, THE StateComparator SHALL filter out SELinuxLabel attributes from the previous State before comparison.

### Requirement 16: CompareResult Display Formatting

**User Story:** As a user, I want comparison results displayed in a clear, organized format, so that I can quickly understand what has changed.

#### Acceptance Criteria

1. WHEN displaying changes, THE CompareResult SHALL show the timestamp and comment of the last committed State being compared against.
2. WHEN the verbose flag is enabled and modifications exist, THE CompareResult SHALL display each category of modifications with a labeled prefix (e.g., "Added:", "Deleted:", "Content modified:").
3. WHEN displaying renamed files, THE CompareResult SHALL show the previous file name, an arrow, and the new file name.
4. WHEN displaying copied or duplicated files, THE CompareResult SHALL show the current file name and the original file name it was copied from or duplicates.
5. WHEN displaying date-modified, content-modified, or attribute-modified files, THE CompareResult SHALL show the file name and the specific attribute changes (previous value to new value).
6. THE CompareResult SHALL sort all Difference entries within each category alphabetically by file name before display.
7. THE CompareResult SHALL display a summary line listing the count of each modification type that has at least one occurrence.

### Requirement 17: Output Truncation

**User Story:** As a user, I want large outputs to be truncated to a manageable size, so that the display remains readable when many files have changed.

#### Acceptance Criteria

1. WHEN the number of differences in a single category exceeds the configured truncateOutput value (default 200), THE CompareResult SHALL display the first truncateOutput entries, then a truncation message, and the count of remaining files.
2. WHEN the truncateOutput value is set to 0, THE CompareResult SHALL suppress all detailed difference output and display only the summary counts.
3. THE CompareResult SHALL truncate output when the number of displayed entries reaches `truncateOutput` AND the number of remaining entries exceeds one quarter of `truncateOutput`. The truncation message shows the count of undisplayed entries. The remaining entries after the truncation point are NOT displayed.
4. THE CompareResult.getModificationCounts() SHALL return a ModificationCounts object whose fields match the sizes of the corresponding modification lists. Note: the current implementation does NOT populate the `corrupted` field — only `modifiedCount()` includes corrupted entries in its total.

### Requirement 18: Model Version Compatibility

**User Story:** As a user, I want the comparison to handle states from different model versions gracefully, so that upgrades do not cause crashes.

#### Acceptance Criteria

1. IF the last committed State has a different Model_Version than the current State, THEN THE StateComparator SHALL log a warning and treat the comparison as if no previous State exists.
2. WHEN no previous State is available for comparison, THE StateComparator SHALL classify all current files as added.

### Requirement 19: Comparison Algorithm Invariants

**User Story:** As a developer, I want the comparison to maintain correctness invariants, so that the results are reliable and consistent.

#### Acceptance Criteria

1. FOR ALL pairs of identical States (same FileState entries), THE StateComparator SHALL produce a CompareResult with zero modifications.
2. FOR ALL comparisons, the total number of files classified (not-modified + all modification categories except deleted) SHALL equal the file count of the current State. Deleted entries are additional to that total, since they describe files present only in the previous State, so the complete modification count is bounded by the current file count plus the number of deletions.
3. FOR ALL files classified as contentModified, the fileName SHALL be the same in both the previous and current FileState.
4. FOR ALL files classified as renamed, the FileHash SHALL be the same in both the previous and current FileState.
5. FOR ALL files classified as dateModified, the FileHash and fileLength SHALL be the same in both the previous and current FileState.
