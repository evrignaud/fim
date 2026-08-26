# Requirements Document

## Introduction

This document specifies the requirements for the Duplicate Detection and Removal feature of FIM (File Integrity Manager). This feature enables users to find duplicate files within a FIM repository by comparing file content hashes, and optionally remove the duplicates to reclaim wasted disk space. It encompasses the `find-duplicates` command, the `remove-duplicates` command, the internal DuplicateFinder engine, the DuplicateOutputGenerator for multiple output formats, and the DuplicateResult/DuplicateSet model objects that represent and sort duplicate groupings.

## Glossary

- **FIM**: File Integrity Manager — a Java CLI tool for managing file integrity of complete file trees.
- **Repository**: A directory tree managed by FIM, identified by the presence of a `.fim` directory at its root.
- **State**: A snapshot of the file tree containing FileState entries for all tracked files, as defined in the State Management spec.
- **FileState**: The metadata record for a single file, containing fileName, fileLength, fileTime, fileHash, fileAttributes, and modification type.
- **FileHash**: A composite hash object containing smallBlockHash, mediumBlockHash, and fullHash fields, used to identify file content.
- **Hash_Mode**: The hashing strategy used during state generation: hashAll, hashSmallBlock, hashMediumBlock, or dontHash.
- **FindDuplicatesCommand**: The command (`find-duplicates` / `fdup`) that searches for duplicate files within the FIM repository.
- **RemoveDuplicatesCommand**: The command (`remove-duplicates` / `rdup`) that removes duplicate files, either locally or against a master repository.
- **DuplicateFinder**: The internal engine that identifies groups of duplicate files by comparing FileHash values across all FileState entries in a State.
- **DuplicateResult**: The result object containing a list of DuplicateSet entries, aggregate counts of duplicated files, total wasted space, and removal statistics.
- **DuplicateSet**: A group of two or more FileState entries that share the same FileHash and fileLength, representing a set of duplicate files.
- **DuplicateOutputGenerator**: The internal component that formats DuplicateResult data for output in CSV or JSON format.
- **OutputType**: An enumeration of supported output formats: human, csv, json.
- **SortMethod**: An enumeration of supported sort criteria for duplicate sets: wasted (by wasted space), number (by number of files), size (by individual file size), path (by file path).
- **Path_Ordering_Key**: The file name that represents a DuplicateSet when ordering sets by path: the lowest of the file names it contains.
- **Native_Encoding**: The charset the JVM maps file names with, reported by the `sun.jnu.encoding` system property. It is derived from the locale of the process and cannot be overridden with a system property.
- **Wasted_Space**: The total disk space consumed by duplicate copies within a DuplicateSet, calculated as (number of files - 1) multiplied by the file size.
- **Master_Repository**: An external FIM repository used as a reference when removing duplicates; files in the current directory that match files in the Master_Repository are considered duplicates.
- **Confirmation_Prompt**: An interactive prompt requiring the user to confirm a destructive action by entering 'y', 'n', or 'A' (always yes).
- **Context**: The execution context carrying runtime parameters including Hash_Mode, outputType, sortMethod, sortAscending, removeDuplicates, alwaysYes, masterFimRepositoryDir, and file patterns.
- **FilePattern**: A pattern used to include or exclude files from duplicate detection based on file name matching.

## Requirements

### Requirement 1: Find Duplicates Command Execution

**User Story:** As a user, I want to find duplicate files in my FIM repository, so that I can identify redundant copies wasting disk space.

#### Acceptance Criteria

1. WHEN the `find-duplicates` command is executed, THE FindDuplicatesCommand SHALL check that the Hash_Mode is not dontHash and report "File content hashing mandatory for this command" and abort if it is.
2. WHEN the `find-duplicates` command is executed with the `--use-last-state` option, THE FindDuplicatesCommand SHALL load the last committed State from the repository and pass it to the DuplicateFinder.
3. WHEN the `find-duplicates` command is executed without the `--use-last-state` option, THE FindDuplicatesCommand SHALL generate a new State by scanning the current file tree and pass it to the DuplicateFinder.
4. WHEN the `find-duplicates` command completes, THE FindDuplicatesCommand SHALL display the DuplicateResult using the configured OutputType.
5. WHEN the `find-duplicates` command is executed with a Hash_Mode compatible with the Global_Hash_Mode, THE FindDuplicatesCommand SHALL log an informational message and proceed.

### Requirement 2: Duplicate Detection Engine

**User Story:** As a user, I want the system to accurately identify groups of files with identical content, so that I can trust the duplicate detection results.

#### Acceptance Criteria

1. THE DuplicateFinder SHALL sort a copy of the State's FileState entries by FileHash before scanning for duplicates, leaving the order of the State's own list unchanged.
2. THE DuplicateFinder SHALL group consecutive FileState entries that share the same FileHash and fileLength into a single DuplicateSet.
3. THE DuplicateFinder SHALL exclude FileState entries with a fileLength of zero from duplicate detection.
4. THE DuplicateFinder SHALL exclude FileState entries whose corresponding file no longer exists on disk from duplicate detection.
5. THE DuplicateFinder SHALL exclude FileState entries whose file name does not match the configured include patterns from duplicate detection.
6. THE DuplicateFinder SHALL exclude FileState entries whose file name matches the configured exclude patterns from duplicate detection.
7. WHEN a group of FileState entries with the same FileHash contains fewer than two entries, THE DuplicateFinder SHALL not create a DuplicateSet for that group.
8. WHEN duplicate detection completes, THE DuplicateFinder SHALL sort the resulting DuplicateSet list according to the configured SortMethod and sort order.

### Requirement 3: Wasted Space Calculation

**User Story:** As a user, I want to know how much disk space is wasted by duplicate files, so that I can prioritize which duplicates to address.

#### Acceptance Criteria

1. FOR EACH DuplicateSet, THE DuplicateResult SHALL calculate the Wasted_Space as (number of duplicate files minus one) multiplied by the file size of the first file in the set.
2. THE DuplicateResult SHALL maintain a running total of Wasted_Space across all DuplicateSet entries as the totalWastedSpace.
3. THE DuplicateResult SHALL maintain a running count of duplicated files (total files in all DuplicateSet entries minus one per set) as the duplicatedFilesCount.

### Requirement 4: Duplicate Result Sorting

**User Story:** As a user, I want to sort duplicate results by different criteria, so that I can focus on the most impactful duplicates first.

#### Acceptance Criteria

1. WHEN the SortMethod is set to `wasted`, THE DuplicateResult SHALL sort DuplicateSet entries by their Wasted_Space value.
2. WHEN the SortMethod is set to `number`, THE DuplicateResult SHALL sort DuplicateSet entries by the number of files in each set.
3. WHEN the SortMethod is set to `size`, THE DuplicateResult SHALL sort DuplicateSet entries by the individual file size of the duplicated file.
4. WHEN the sort order is descending (default), THE DuplicateResult SHALL sort DuplicateSet entries from largest to smallest value.
5. WHEN the sort order is ascending, THE DuplicateResult SHALL sort DuplicateSet entries from smallest to largest value.
6. WHEN the SortMethod is set to `path`, THE DuplicateResult SHALL sort DuplicateSet entries by their Path_Ordering_Key, bringing the sets whose files live in the same directory next to each other.
7. THE DuplicateSet SHALL derive its Path_Ordering_Key from the lowest of its file names rather than from its first file, so that the key does not depend on the order in which the duplicates were discovered. Duplicate sets are built while walking the files sorted by FileHash, which is unrelated to where the files live.
8. THE comparator selection SHALL cover every SortMethod value, so that a SortMethod without a comparator is rejected at compile time rather than producing an unsorted or failing comparison at run time.

### Requirement 5: Human-Readable Output

**User Story:** As a user, I want duplicate results displayed in a clear, human-readable format, so that I can quickly understand the duplicate situation.

#### Acceptance Criteria

1. WHEN the OutputType is human and duplicates exist, THE DuplicateResult SHALL display each DuplicateSet with a numbered label, the duplication count, the individual file size, and the Wasted_Space.
2. WHEN the OutputType is human and duplicates exist, THE DuplicateResult SHALL list each file name within the DuplicateSet, indented under the set header.
3. WHEN the OutputType is human and no duplicates exist, THE DuplicateResult SHALL display "No duplicate file found".
4. WHEN the OutputType is human and the verbose flag is disabled and removeDuplicates is not active, THE DuplicateResult SHALL display only the summary line with the total duplicated file count and total Wasted_Space.
5. WHEN the OutputType is human, THE DuplicateResult SHALL render the output itself and SHALL NOT delegate to the DuplicateOutputGenerator, so that the set listings stay interleaved with the interactive removal prompts and only one implementation of the human format exists.
6. IF the DuplicateOutputGenerator is asked to render the human OutputType, THEN it SHALL raise an IllegalStateException, because that format is produced by the DuplicateResult and the generator is never invoked for it.

### Requirement 6: CSV Output

**User Story:** As a user, I want duplicate results in CSV format, so that I can import them into spreadsheets or other tools for analysis.

#### Acceptance Criteria

1. WHEN the OutputType is csv, THE DuplicateOutputGenerator SHALL produce CSV output with the header: SetIndex, FileIndex, WastedSpace, FilePath, FileName, FileLength, FileType.
2. WHEN the OutputType is csv, THE DuplicateOutputGenerator SHALL output one row per file in each DuplicateSet, with SetIndex incrementing per set and FileIndex incrementing per file within a set.
3. WHEN the OutputType is csv, THE DuplicateOutputGenerator SHALL extract the directory path from the file name as FilePath and the file extension as FileType.

### Requirement 7: JSON Output

**User Story:** As a user, I want duplicate results in JSON format, so that I can programmatically process the results.

#### Acceptance Criteria

1. WHEN the OutputType is json, THE DuplicateOutputGenerator SHALL produce a JSON array of DuplicatedFiles objects, each containing a wastedSpace field and a fileList array.
2. WHEN the OutputType is json, THE DuplicateOutputGenerator SHALL include for each file in the fileList: name, length, path, and type fields.
3. WHEN the OutputType is json, THE DuplicateOutputGenerator SHALL use the Jackson ObjectMapper for serialization.

### Requirement 8: Local Duplicate Removal

**User Story:** As a user, I want to remove duplicate files from my repository interactively, so that I can reclaim wasted disk space while choosing which copies to keep.

#### Acceptance Criteria

1. WHEN the `remove-duplicates` command is executed without the `-M` option, THE RemoveDuplicatesCommand SHALL delegate to the FindDuplicatesCommand with the removeDuplicates flag set to true.
2. WHEN removeDuplicates is active and the OutputType is not human, THE FindDuplicatesCommand SHALL report "You cannot display duplicates in a non human format and remove them" and abort.
3. WHEN removeDuplicates is active and the alwaysYes flag is set, THE DuplicateResult SHALL mark all files except the first file in each DuplicateSet for removal.
4. WHEN removeDuplicates is active and the alwaysYes flag is not set, THE DuplicateResult SHALL display each file in the DuplicateSet with a numeric index and prompt the user to select which files to preserve.
5. WHEN the user enters a file index number at the preservation prompt, THE DuplicateResult SHALL mark all files except the selected file for removal.
6. WHEN the user enters multiple file index numbers at the preservation prompt, THE DuplicateResult SHALL mark all files except the selected files for removal.
7. WHEN the user enters "all" or "a" at the preservation prompt, THE DuplicateResult SHALL mark no files for removal in that DuplicateSet.
8. WHEN the user enters "none" or "n" at the preservation prompt, THE DuplicateResult SHALL mark all files in the DuplicateSet for removal.
9. WHEN files are removed, THE DuplicateResult SHALL display a summary showing the count of removed files and the total space freed.
10. WHEN files are removed and remaining duplicates exist, THE DuplicateResult SHALL display the count of remaining duplicate files and remaining wasted space.
11. WHEN the user enters "none" or "n" at the preservation prompt, THE DuplicateResult SHALL additionally add one file and one file length to the duplicated file count and the total wasted space, because removing the whole set removes one more file than the set was counted as wasting.
12. THE DuplicateResult SHALL read every preservation answer through a single Scanner over standard input, created on first use and never closed. Closing it would close standard input and leave later prompts unable to read, and a fresh Scanner per prompt could discard input it had already buffered past the line it returned.
13. IF standard input ends while a preservation answer is awaited, THEN THE DuplicateResult SHALL raise a DontWantToContinueException rather than continue prompting for input that cannot arrive.

### Requirement 9: Automatic Removal Safety

**User Story:** As a user, I want a safety confirmation when using automatic duplicate removal, so that I do not accidentally delete files.

#### Acceptance Criteria

1. WHEN the `find-duplicates` command is executed with both the removeDuplicates flag and the `-y` (alwaysYes) flag, THE FindDuplicatesCommand SHALL temporarily disable the alwaysYes flag and display a Confirmation_Prompt explaining that all duplicates will be removed automatically, keeping the first of each set.
2. IF the user declines the safety Confirmation_Prompt, THEN THE FindDuplicatesCommand SHALL abort without scanning for or removing any files.
3. WHEN the command is invoked from a test context (calledFromTest is true), THE FindDuplicatesCommand SHALL skip the safety Confirmation_Prompt.
4. WHEN the safety Confirmation_Prompt has been answered, THE FindDuplicatesCommand SHALL restore the alwaysYes flag, whatever the answer was, so that the flag applies to the duplicate sets themselves. The Confirmation_Prompt only re-arms the flag by itself when the answer is `A`, so an answer of `y` would otherwise leave it disabled and make every duplicate set ask which file to preserve, defeating `-y` entirely. Restoring it on refusal is harmless, because the command aborts at that point.

### Requirement 10: Master Repository Duplicate Removal

**User Story:** As a user, I want to remove files from a directory that are duplicates of files in a master FIM repository, so that I can deduplicate across repositories.

#### Acceptance Criteria

1. WHEN the `remove-duplicates` command is executed with the `-M` option specifying a Master_Repository path, THE RemoveDuplicatesCommand SHALL load the last committed State from the Master_Repository.
2. WHEN the `remove-duplicates` command is executed with the `-M` option, THE RemoveDuplicatesCommand SHALL generate a new State by scanning the current directory. It SHALL always rescan, and SHALL NOT honour the `-l` (use-last-state) option in this mode.
3. WHEN a file in the current directory has the same FileHash as a file in the Master_Repository State, THE RemoveDuplicatesCommand SHALL identify it as a duplicate and display the local file name and the corresponding master file name.
4. WHEN a duplicate is identified against the Master_Repository, THE RemoveDuplicatesCommand SHALL display a Confirmation_Prompt asking the user to confirm removal of each duplicate file individually.
5. WHEN the user confirms removal, THE RemoveDuplicatesCommand SHALL delete the local duplicate file and display a confirmation message.
6. WHEN master repository duplicate removal completes, THE RemoveDuplicatesCommand SHALL display a summary showing the total number of duplicates found and the number of files removed.
7. WHEN no duplicates are found against the Master_Repository, THE RemoveDuplicatesCommand SHALL display "No duplicate file found".
8. THE RemoveDuplicatesCommand SHALL exclude files with zero fileLength from master repository duplicate comparison.
9. THE RemoveDuplicatesCommand SHALL read the Master_Repository and the current directory each through its own Context, carrying the repository root that the tree in question actually describes: the Master_Repository for the State being loaded, and the current directory for the tree being scanned. It SHALL NOT repoint the caller's Context at the Master_Repository.
10. THE RemoveDuplicatesCommand SHALL resolve the ignore rules of the local scan against the directory being scanned. Because the current directory is required to sit outside the Master_Repository (Requirements 11.3 and 11.4), a repository root pointing at the Master_Repository would never be reached while walking up from the current directory, and the walk described in the File Ignore System spec would instead run to the top of the filesystem and apply `.fimignore` files from unrelated directories. This is what makes the `-M` option usable from inside a Fim repository.
11. THE RemoveDuplicatesCommand SHALL key the Master_Repository lookup by FileHash alone, retaining one master FileState per FileHash. When several master files share a FileHash, the reported match is therefore one of them rather than all of them.
12. THE RemoveDuplicatesCommand SHALL continue to prompt and remove through the caller's Context, so that answering `A` at a removal Confirmation_Prompt applies to the rest of the run.

### Requirement 11: Master Repository Validation

**User Story:** As a user, I want the system to validate the master repository configuration, so that I do not accidentally remove files from the wrong directory.

#### Acceptance Criteria

1. IF the `-M` option names a directory that does not exist, THEN THE FIM CLI SHALL report "Master Fim repository directory '&lt;dir&gt;' does not exist" while parsing the command line and abort. A caller that sets masterFimRepositoryDir directly, bypassing the CLI, reaches the check in Requirement 11.2 instead, because a directory that does not exist cannot contain a `.fim` directory.
2. IF the Master_Repository directory does not contain a `.fim` directory, THEN THE RemoveDuplicatesCommand SHALL report "Directory &lt;dir&gt; is not a Fim repository" and abort.
3. IF the current directory is the same as the Master_Repository directory, THEN THE RemoveDuplicatesCommand SHALL report "Cannot remove duplicates into the master directory" and abort.
4. IF the current directory is a subdirectory of the Master_Repository directory, THEN THE RemoveDuplicatesCommand SHALL report "Cannot remove duplicates into a sub-directory of the master directory" and abort.
5. WHEN the Hash_Mode is hashSmallBlock or hashMediumBlock for master repository removal, THE RemoveDuplicatesCommand SHALL display a warning about the reduced hash mode and request a Confirmation_Prompt before proceeding.
6. THE RemoveDuplicatesCommand SHALL evaluate the checks in Requirements 11.3, 11.4 and 11.2 in that order, on the absolute normalized form of both directories.
7. THE RemoveDuplicatesCommand SHALL NOT reject the reverse nesting, where the Master_Repository sits inside the current directory. That arrangement is permitted, and the master files are then reported as matches of the local files that duplicate them.

### Requirement 12: Duplicate Detection Invariants

**User Story:** As a developer, I want the duplicate detection algorithm to maintain correctness invariants, so that the results are reliable and consistent.

#### Acceptance Criteria

1. FOR ALL DuplicateSet entries in a DuplicateResult, every FileState within the set SHALL have the same FileHash value.
2. FOR ALL DuplicateSet entries in a DuplicateResult, every FileState within the set SHALL have the same fileLength value.
3. FOR ALL DuplicateSet entries in a DuplicateResult, the set SHALL contain at least two FileState entries.
4. FOR ALL DuplicateResult objects, the duplicatedFilesCount SHALL equal the sum of (file count minus one) across all DuplicateSet entries.
5. FOR ALL DuplicateResult objects, the totalWastedSpace SHALL equal the sum of Wasted_Space across all DuplicateSet entries.
6. FOR ALL States with no files sharing the same FileHash, THE DuplicateFinder SHALL produce a DuplicateResult with zero DuplicateSet entries and zero duplicatedFilesCount.
7. FOR ALL DuplicateSet entries, the Wasted_Space SHALL equal (number of files in the set minus one) multiplied by the fileLength of the first file.
8. FOR ALL DuplicateSet entries, the Path_Ordering_Key SHALL be the lowest file name the set contains.

### Requirement 13: File Removal Failure Reporting

**User Story:** As a user, I want to be told which file could not be removed and why, so that a failed removal is something I can act on rather than a bare error.

#### Acceptance Criteria

1. WHEN a file removal fails, THE FileUtil SHALL report the path of the file and the exception that stopped it.
2. WHEN a file removal fails, THE FileUtil SHALL report the failure once, after every attempt has been made, rather than once per attempt.
3. WHEN a file removal fails and the file does not exist, THE FileUtil SHALL not retry, because waiting cannot make an absent file appear. The retry loop exists for the file locking observed on Windows and macOS.
4. WHILE running on Windows or macOS, THE FileUtil SHALL retry a failed removal up to 10 times, pausing 1 millisecond between attempts, and SHALL serialize those removals on a shared lock, because deletion is not thread-safe on those platforms.
5. IF the name of the file cannot be represented in the Native_Encoding, THEN THE FileUtil SHALL state that in the failure report and SHALL ask for a UTF-8 locale, naming the encoding in use. A name Fim read from disk through a charset that cannot represent it comes back altered and no longer refers to the file it came from, so removal fails however many times it is retried, with nothing else in the output pointing at an encoding problem.
6. THE FileUtil SHALL treat a name containing the Unicode replacement character as not representable, since such a name has already lost information while being read.
7. WHEN a file removal fails, THE DuplicateResult SHALL leave the file out of the removed count and the freed space, and SHALL list it without the removal marker.
