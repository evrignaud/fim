# Requirements Document

## Introduction

This document specifies the requirements for the Hardware Corruption Detection feature of FIM (File Integrity Manager). This feature enables users to detect file corruption most likely caused by hardware failures or filesystem bugs. It identifies files whose content has changed (different hash) while their timestamps (creation time and last modified time) remain unchanged — a pattern characteristic of silent data corruption rather than intentional user modification. The feature encompasses the `detect-corruption` command, a specialized hardware corruption detection mode in the StateComparator, and the "corrupted" classification in the CompareResult model.

## Glossary

- **FIM**: File Integrity Manager — a Java CLI tool for managing file integrity of complete file trees.
- **Repository**: A directory tree managed by FIM, identified by the presence of a `.fim` directory at its root.
- **State**: A GZIP-compressed JSON snapshot containing file metadata, cryptographic hashes, timestamps, and modification data for all tracked files at a point in time, as defined in the State Management spec.
- **FileState**: The metadata record for a single file within a State, containing fileName, fileLength, fileTime, fileHash, fileAttributes, and modification type.
- **FileHash**: A composite hash object containing smallBlockHash, mediumBlockHash, and fullHash fields, used to identify file content.
- **FileTime**: A record containing creationTime and lastModified timestamps for a file.
- **Hash_Mode**: The hashing strategy used during state generation: hashAll, hashSmallBlock, hashMediumBlock, or dontHash.
- **DetectCorruptionCommand**: The command (`detect-corruption` / `dcor`) that searches for files suspected of hardware corruption.
- **StateComparator**: The internal component responsible for comparing two States and producing a CompareResult, supporting a specialized hardware corruption detection mode.
- **StateGenerator**: The internal component responsible for scanning the file tree and producing a new State object.
- **StateManager**: The internal component responsible for persisting, loading, and numbering State files.
- **CompareResult**: The result of comparing two States, containing lists of modifications including a dedicated "corrupted" list for hardware corruption results.
- **Difference**: A pair of FileState entries (previous and current) representing a detected change, used within CompareResult lists.
- **Corruption_Pattern**: The condition where a file has the same fileName and same FileTime (both creation time and last modified time) as the previous State, but a different FileHash.
- **Context**: The execution context carrying runtime parameters including Hash_Mode, current directory, repository root, and invocation flags.
- **Global_Hash_Mode**: The Hash_Mode configured at repository initialization time, stored in the settings file.

## Requirements

### Requirement 1: Detect Corruption Command Execution

**User Story:** As a user, I want to run a command that finds files likely corrupted by hardware failures, so that I can identify and address silent data corruption before data loss occurs.

#### Acceptance Criteria

1. WHEN the `detect-corruption` command is executed, THE DetectCorruptionCommand SHALL verify that the Hash_Mode is hashAll.
2. IF the Hash_Mode is not hashAll, THEN THE DetectCorruptionCommand SHALL report "Hardware corruption detection require to hash all the file content." and abort.
3. WHEN the Hash_Mode is hashAll, THE DetectCorruptionCommand SHALL check compatibility with the Global_Hash_Mode using the ALLOW_COMPATIBLE option.
4. WHEN the `detect-corruption` command is executed, THE StateGenerator SHALL scan the file tree and produce a new State reflecting the current file system contents.
5. WHEN the `detect-corruption` command is executed, THE StateManager SHALL load the last committed State from the repository.
6. WHEN the current State and last committed State are available, THE DetectCorruptionCommand SHALL create a StateComparator with hardware corruption detection mode enabled and invoke the comparison.
7. WHEN the comparison completes, THE DetectCorruptionCommand SHALL display the CompareResult using "Nothing corrupted" as the message when no corruption is found.

### Requirement 2: Subdirectory Invocation

**User Story:** As a user, I want to run corruption detection from a subdirectory of my repository, so that I can check a specific subtree without scanning the entire repository.

#### Acceptance Criteria

1. WHEN the `detect-corruption` command is invoked from a subdirectory of the repository, THE DetectCorruptionCommand SHALL filter the last committed State to include only files within the current subdirectory subtree.
2. WHEN the `detect-corruption` command is invoked from the repository root, THE DetectCorruptionCommand SHALL use the full last committed State without filtering.
3. WHEN filtering the last committed State for a subdirectory, THE DetectCorruptionCommand SHALL use the State filterDirectory method with the repository root directory and the current directory as parameters.

### Requirement 3: Hardware Corruption Detection Mode in StateComparator

**User Story:** As a developer, I want the StateComparator to support a specialized corruption detection mode, so that the comparison logic focuses exclusively on the corruption pattern.

#### Acceptance Criteria

1. WHEN the searchForHardwareCorruption method is called on a StateComparator instance, THE StateComparator SHALL enable hardware corruption detection mode for the subsequent comparison.
2. WHILE hardware corruption detection mode is enabled, THE StateComparator SHALL search for files with the same file name in both the previous and current States.
3. WHILE hardware corruption detection mode is enabled, THE StateComparator SHALL classify a file as corrupted when the file has the same fileName and the same FileTime but a different FileHash compared to the previous State.
4. WHILE hardware corruption detection mode is enabled, THE StateComparator SHALL skip the rename detection phase.
5. WHILE hardware corruption detection mode is enabled, THE StateComparator SHALL skip the copy detection phase.
6. WHILE hardware corruption detection mode is enabled, THE StateComparator SHALL skip the duplicate detection phase.
7. WHILE hardware corruption detection mode is enabled, THE StateComparator SHALL skip the added file detection phase.
8. WHILE hardware corruption detection mode is enabled, THE StateComparator SHALL skip the deleted file detection phase.
9. WHILE hardware corruption detection mode is enabled, THE StateComparator SHALL sort the final CompareResult before returning.

### Requirement 4: Corrupted File Classification

**User Story:** As a user, I want corrupted files to be clearly identified and classified, so that I can distinguish hardware corruption from intentional modifications.

#### Acceptance Criteria

1. WHEN a file matches the Corruption_Pattern, THE StateComparator SHALL add a Difference entry containing both the previous FileState and the current FileState to the CompareResult corrupted list.
2. WHEN a file matches the Corruption_Pattern, THE StateComparator SHALL set the Modification type of the current FileState to "corrupted".
3. WHEN a file has the same fileName and the same FileHash as the previous State, THE StateComparator SHALL not classify the file as corrupted.
4. WHEN a file has the same fileName and a different FileTime compared to the previous State, THE StateComparator SHALL not classify the file as corrupted, regardless of the FileHash value.

### Requirement 5: CompareResult Corruption Display

**User Story:** As a user, I want corruption detection results displayed clearly, so that I can quickly see which files are suspected of corruption and review their details.

#### Acceptance Criteria

1. WHEN the CompareResult contains corrupted files and verbose mode is enabled, THE CompareResult SHALL display each corrupted file with the label "Corrupted?:" followed by the file name and modified attributes.
2. WHEN the CompareResult contains corrupted files, THE CompareResult SHALL include the corrupted file count in the summary line using the format "<count> corrupted".
3. WHEN the CompareResult contains no corrupted files and no other modifications, THE CompareResult SHALL display the notModifiedMessage provided by the caller.
4. THE CompareResult SHALL include the corrupted list size in the total modifiedCount calculation.
5. THE CompareResult SHALL sort the corrupted list by file name before display.

### Requirement 6: Corruption Detection Invariants

**User Story:** As a developer, I want the corruption detection algorithm to maintain correctness invariants, so that the results are reliable and no false classifications occur.

#### Acceptance Criteria

1. FOR ALL Difference entries in the corrupted list, the previous FileState and the current FileState SHALL have the same fileName.
2. FOR ALL Difference entries in the corrupted list, the previous FileState and the current FileState SHALL have the same FileTime (both creationTime and lastModified).
3. FOR ALL Difference entries in the corrupted list, the previous FileState and the current FileState SHALL have different FileHash values.
4. FOR ALL files where the FileHash has not changed between the previous and current State, THE StateComparator in hardware corruption detection mode SHALL not include the file in the corrupted list.
5. FOR ALL files where the FileTime has changed between the previous and current State, THE StateComparator in hardware corruption detection mode SHALL not include the file in the corrupted list.
6. WHEN hardware corruption detection mode is enabled, THE CompareResult SHALL contain an empty added list, an empty deleted list, an empty renamed list, an empty copied list, and an empty duplicated list.
