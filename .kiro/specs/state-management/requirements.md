# Requirements Document

## Introduction

This document specifies the requirements for the State Management feature of FIM (File Integrity Manager). State Management covers the core lifecycle of integrity snapshots ("States") within a FIM repository: initialization, committing new states, rolling back commits, viewing state history, and purging old states. Each State is a GZIP-compressed JSON file containing cryptographic hashes and metadata for every tracked file in the repository's file tree.

## Glossary

- **FIM**: File Integrity Manager — a Java CLI tool for managing file integrity of complete file trees.
- **State**: A GZIP-compressed JSON snapshot containing file metadata, cryptographic hashes, timestamps, comments, and modification counts for all tracked files at a point in time.
- **Repository**: A directory tree managed by FIM, identified by the presence of a `.fim` directory at its root.
- **Dot_Fim_Directory**: The `.fim` directory at the repository root that stores all FIM metadata, states, and settings.
- **States_Directory**: The `.fim/states/` directory where State files are persisted.
- **State_File**: A file named `state_<number>.json.gz` stored in the States_Directory.
- **State_Number**: A monotonically incrementing integer identifying each committed State, tracked in the settings file.
- **State_Hash**: A SHA-512 hash computed over the entire State content, used to verify State integrity.
- **Model_Version**: A version string (currently "5") embedded in each State to track schema compatibility.
- **Hash_Mode**: The hashing strategy used during state generation (hashAll, hashSmallBlock, hashMediumBlock, or dontHash).
- **Global_Hash_Mode**: The Hash_Mode configured at repository initialization time, stored in the settings file.
- **Settings_File**: The `.fim/settings.json` file storing repository-wide configuration including Global_Hash_Mode and last State_Number.
- **StateManager**: The internal component responsible for persisting, loading, and numbering State files.
- **StateGenerator**: The internal component responsible for scanning the file tree and producing a new State object.
- **StateComparator**: The internal component responsible for comparing two States and producing a CompareResult.
- **CompareResult**: The result of comparing two States, containing lists of added, deleted, modified, renamed, and corrupted files along with ModificationCounts.
- **FileState**: The metadata record for a single file within a State, containing fileName, fileLength, fileTime, modification type, fileHash, and fileAttributes.
- **ModificationCounts**: A summary of the number of each type of modification (added, deleted, modified, renamed, etc.) detected during a commit.
- **Context**: The execution context carrying all runtime parameters such as current directory, hash mode, comment, thread count, and repository paths.
- **Confirmation_Prompt**: An interactive prompt requiring the user to confirm a destructive action by entering 'y', 'n', or 'A' (always yes).

## Requirements

### Requirement 1: Repository Initialization

**User Story:** As a user, I want to initialize a FIM repository in a directory, so that I can begin tracking file integrity for that file tree.

#### Acceptance Criteria

1. WHEN the `init` command is executed, THE InitCommand SHALL create the Dot_Fim_Directory and the States_Directory within the current directory.
2. WHEN the `init` command is executed, THE StateGenerator SHALL scan the entire file tree recursively and produce the first State containing a FileState entry for every regular file.
3. WHEN the `init` command is executed and a comment is provided via the `-m` option, THE InitCommand SHALL store the provided comment in the generated State.
4. WHEN the `init` command is executed and no comment is provided, THE InitCommand SHALL use "Initial State" as the default comment.
5. WHEN the `init` command is executed and no comment is provided, THE InitCommand SHALL display a Confirmation_Prompt before proceeding.
6. IF the Dot_Fim_Directory or States_Directory already exists in the current directory, THEN THE FIM CLI SHALL report "Fim repository already exist" before the command runs and abort without modifying the existing repository.
7. IF the current directory cannot be written to, THEN THE FIM CLI SHALL report "Not allowed to create the '&lt;dir&gt;' directory that holds the Fim repository" and abort.
8. WHEN deciding whether the current directory can be written to, THE FIM CLI SHALL consult the permissions the filesystem reports first, and SHALL fall back to creating and deleting a probe file when they say the directory is not writable. The reported permissions are not conclusive on a network share: a CIFS/SMB mount commonly presents ownership and mode bits belonging to the mount rather than to the account in use, and a directory that can in fact be written to is then reported as read-only. A directory that genuinely denies writing fails the probe as well, so real permission problems are still reported.
9. WHEN the `init` command is executed with a Hash_Mode other than hashAll, THE InitCommand SHALL persist the Hash_Mode as the Global_Hash_Mode in the Settings_File.
10. WHEN the `init` command completes successfully, THE StateManager SHALL save the generated State as `state_1.json.gz` in the States_Directory and set the State_Number to 1.
11. THE State generated during initialization SHALL contain the current Model_Version, a timestamp, the file count, total files content length, the Hash_Mode, and a State_Hash computed via SHA-512.
12. THE InitCommand SHALL create the Dot_Fim_Directory only after the file tree scan has completed. An interrupted scan therefore leaves no repository behind, and `init` can simply be run again.

### Requirement 2: State Commit

**User Story:** As a user, I want to commit the current state of my file tree, so that I can record changes since the last committed state.

#### Acceptance Criteria

1. WHEN the `commit` command is executed, THE StateGenerator SHALL scan the file tree and produce a new State reflecting the current file system contents.
2. WHEN the `commit` command is executed, THE StateComparator SHALL compare the new State against the last committed State and produce a CompareResult.
3. WHEN the CompareResult indicates modifications exist, THE StateManager SHALL persist the new State as the next sequential State_File with an incremented State_Number.
4. WHEN the CompareResult indicates no modifications exist, THE CommitCommand SHALL display "Nothing committed" and not create a new State_File.
5. WHEN a comment is provided via the `-m` option, THE CommitCommand SHALL store the provided comment in the new State.
6. WHEN no comment is provided, THE CommitCommand SHALL display a Confirmation_Prompt informing the user that modifications will be committed without a comment.
7. IF the user declines the Confirmation_Prompt when no comment is provided, THEN THE CommitCommand SHALL abort without creating a new State.
8. IF the Hash_Mode is set to dontHash and the Global_Hash_Mode is not dontHash, THEN THE CommitCommand SHALL report "Computing hash is mandatory" and abort.
9. IF any aspect is set to ignored in the Context, THEN THE CommitCommand SHALL report "Not allowed to ignore any aspect while committing" and abort.
10. WHEN the commit Hash_Mode differs from the Global_Hash_Mode and is not dontHash, THE CommitCommand SHALL reload the last State with the Global_Hash_Mode and retrieve missing hashes for unmodified files from the previous State.
11. WHEN the commit is invoked from a subdirectory of the repository, THE CommitCommand SHALL produce a consolidated State merging the subdirectory scan with files from the last State that are outside the subdirectory.
12. WHEN the commit is invoked from a subdirectory and the last State uses a different Model_Version, THE CommitCommand SHALL report an error and abort.
13. WHEN the `-p` (purge-states) option is active and the commit succeeds, THE CommitCommand SHALL execute the purge-states operation after persisting the new State.
14. THE committed State SHALL include ModificationCounts summarizing the number of added, deleted, content-modified, date-modified, attribute-modified, renamed, and duplicated files. Note: the `CompareResult.getModificationCounts()` method does not currently populate the `corrupted` field in `ModificationCounts`, even though `modifiedCount()` includes corrupted entries in its total.
15. WHEN a commit records deletions, THE CommitCommand SHALL add the deleted FileState entries to the State being saved, so that `fim log` can list what that commit removed. Those entries keep the `deleted` modification, and the next comparison drops them again when it resets the loaded State.
16. WHEN the commit is invoked from a subdirectory, THE CommitCommand SHALL recompute the ModificationCounts of the consolidated State from the FileState entries it contains, rather than reusing the counts of the subdirectory comparison.

### Requirement 3: State Persistence and Integrity

**User Story:** As a user, I want my states to be stored reliably with integrity verification, so that I can trust the recorded file integrity data has not been tampered with.

#### Acceptance Criteria

1. THE StateManager SHALL persist each State as a GZIP-compressed JSON file named `state_<State_Number>.json.gz` in the States_Directory.
2. WHEN saving a State, THE State SHALL sort all FileState entries by file name before serialization.
3. WHEN saving a State, THE State SHALL compute a State_Hash using SHA-512 over the complete State content (modelVersion, timestamp, comment, fileCount, filesContentLength, hashMode, ignoredFiles, and all FileState entries) and embed the State_Hash in the persisted file.
4. WHEN the ignored files are assigned to a State, THE State SHALL store them in a canonical Set representation, so that two States holding the same ignored files always produce the same State_Hash regardless of the Set implementation the caller supplied.
5. WHEN computing the State_Hash, THE State SHALL iterate the ignored files in their stored order without reordering them.
6. WHEN loading a State that was persisted by an earlier Fim version, THE StateManager SHALL successfully verify its embedded State_Hash, and SHALL NOT report it as corrupted on account of the ignored files ordering.
7. WHEN loading a State with full validation enabled, THE StateManager SHALL verify the embedded State_Hash against a recomputed hash of the State content.
8. IF the State_Hash verification fails during loading, THEN THE StateManager SHALL raise a CorruptedStateException indicating the State file may have been tampered with.
9. WHEN loading a State whose Model_Version differs from the current Model_Version, THE StateManager SHALL log a warning that some features may not work completely and skip integrity hash verification.
10. THE StateManager SHALL track the last State_Number in the Settings_File and increment it each time a new State is created.
11. IF the last State_Number in the Settings_File is desynchronized with the actual State files on disk, THEN THE StateManager SHALL detect the inconsistency, reset the State_Number to match the actual files, and log an error message.
12. THE State SHALL count towards its file count, and sum into its total files content length, only the FileState entries that are not marked `deleted`. A commit stores the entries it deleted alongside the surviving ones (Requirement 2.15), and those entries stand for files that are gone: counting them reported the deleted files as still present and still occupying space, and made the space freed by a commit appear one State later than the commit that freed it.
13. THE State SHALL recompute the file count and the total files content length from its FileState entries on every read, rather than trusting the values read from the file, so that States written before this rule was applied report corrected numbers as well.
14. THE State SHALL feed the State_Hash from the stored file count and total files content length fields, which keep whatever value they were saved with. The integrity check of existing State files is therefore unaffected by Requirements 3.12 and 3.13, and no Model_Version change is required.
15. THE Settings_File SHALL be read and written as UTF-8, using Gson, with pretty printing on write.
16. IF the Settings_File cannot be read or written, THEN THE SettingsManager SHALL raise a RepositoryException carrying "Error reading settings" or "Error saving settings" respectively.

### Requirement 4: State Rollback

**User Story:** As a user, I want to undo my last commit, so that I can revert to the previous state if I committed by mistake.

#### Acceptance Criteria

1. WHEN the `rollback` command is executed and more than one State exists, THE RollbackCommand SHALL identify the last State_File and display a summary of the State to be removed, including its state number, timestamp, comment, and modification counts.
2. WHEN the `rollback` command is executed, THE RollbackCommand SHALL display a Confirmation_Prompt asking the user to confirm removal of the last State.
3. WHEN the user confirms the rollback, THE RollbackCommand SHALL delete the last State_File from the States_Directory and decrement the State_Number in the Settings_File.
4. WHEN the user declines the rollback Confirmation_Prompt, THE RollbackCommand SHALL abort without modifying any State files or the State_Number.
5. WHEN the `rollback` command is executed and only one State (the initial State) exists, THE RollbackCommand SHALL display "No commit to rollback" and take no action.

### Requirement 5: State History Log

**User Story:** As a user, I want to view the history of all committed states, so that I can review what changes were recorded over time.

#### Acceptance Criteria

1. WHEN the `log` command is executed, THE LogCommand SHALL iterate through all State files from State 1 to the last State_Number in sequential order.
2. FOR EACH State in the history, THE LogCommand SHALL display the state number, formatted timestamp, file count, total files content length, hash mode used, and the subdirectory from which the commit was generated (if applicable). The file count and the total files content length exclude the entries the commit deleted, per Requirement 3.12, so the space a commit freed is reflected by that commit.
3. FOR EACH State in the history, THE LogCommand SHALL display the comment associated with the State, if a comment exists.
4. FOR EACH State in the history, THE LogCommand SHALL display the modification summary showing counts of added, deleted, modified, renamed, and other change types.
5. IF no States exist in the repository, THEN THE LogCommand SHALL display "No State found".
6. WHEN loading States for the log display, THE LogCommand SHALL load States without full integrity validation to optimize performance.

### Requirement 6: State Purge

**User Story:** As a user, I want to remove old states to save disk space, so that only the most recent state is retained.

#### Acceptance Criteria

1. WHEN the `purge-states` command is executed and more than one State exists, THE PurgeStatesCommand SHALL identify all State files except the most recent one as candidates for deletion.
2. WHEN State files are identified for purging, THE PurgeStatesCommand SHALL display the count of State files to be deleted and request a Confirmation_Prompt.
3. WHEN the user confirms the purge, THE PurgeStatesCommand SHALL delete all identified State files from the States_Directory, retaining only the most recent State_File, renumber it to `state_1.json.gz`, and update the last State_Number to 1.
4. WHEN the user declines the purge Confirmation_Prompt, THE PurgeStatesCommand SHALL abort without deleting any State files.
5. WHEN the `purge-states` command is executed and only one State exists, THE PurgeStatesCommand SHALL display "No State to purge" and take no action.

### Requirement 7: State Serialization Round-Trip

**User Story:** As a developer, I want State serialization and deserialization to be lossless, so that saved states can be reliably restored.

#### Acceptance Criteria

1. FOR ALL valid State objects, saving to a GZIP-compressed JSON file and then loading from that file SHALL produce a State object equivalent to the original (round-trip property).
2. FOR ALL valid FileState objects within a State, the serialized JSON representation SHALL preserve all fields: fileName, fileLength, fileTime, modification, fileHash, and fileAttributes.
3. THE State serializer SHALL use the Jackson ObjectMapper for JSON serialization and deserialization, ensuring consistent field mapping.
