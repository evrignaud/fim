# Requirements Document

## Introduction

This document specifies the requirements for the File Ignore System feature of FIM (File Integrity Manager). The File Ignore System provides mechanisms for users to exclude files and directories from integrity tracking. It supports `.fimignore` files (analogous to `.gitignore`), CLI-based include/exclude patterns, a command to display ignored files, a command to reset file attributes, and a glob-style pattern matching model used throughout the system.

## Glossary

- **FIM**: File Integrity Manager — a Java CLI tool for managing file integrity of complete file trees.
- **Repository**: A directory tree managed by FIM, identified by the presence of a Dot_Fim_Directory at its root.
- **Dot_Fim_Directory**: The `.fim` directory at the repository root that stores all FIM metadata, states, and settings.
- **Fimignore_File**: A file named `.fimignore` placed in any directory within the Repository, containing patterns that specify files and directories to exclude from tracking.
- **FimIgnoreManager**: The internal component responsible for loading, merging, and evaluating Fimignore_File patterns during state generation.
- **FimIgnore**: A model object holding two sets of FilePattern instances: patterns that apply locally to the containing directory and patterns that apply to all subdirectories.
- **FilePattern**: A model object representing a single glob-style file matching pattern, compiled into a regular expression for evaluation.
- **StateGenerator**: The internal component responsible for scanning the file tree and producing a new State object.
- **State**: A snapshot containing file metadata and cryptographic hashes for all tracked files at a point in time.
- **Context**: The execution context carrying all runtime parameters such as current directory, hash mode, include patterns, exclude patterns, and repository paths.
- **Include_Pattern**: A CLI-specified pattern (via `--include`) restricting state generation to files whose names match the pattern.
- **Exclude_Pattern**: A CLI-specified pattern (via `--exclude`) excluding files whose names match the pattern from state generation.
- **All_Directories_Prefix**: The `**/` prefix in a Fimignore_File pattern indicating the pattern applies recursively to all subdirectories.
- **Ignored_Directories**: A fixed set of directory names that are always excluded from scanning: `.fim`, `.git`, `.svn`, `.cvs`.
- **Ignored_Files_Set**: The collection of file and directory paths that were excluded during state generation, stored as metadata in the State.
- **DisplayIgnoredFilesCommand**: The command that retrieves and displays the Ignored_Files_Set from the last committed State.
- **ResetFileAttributesCommand**: The command that resets file permissions, timestamps, and SELinux labels to match the values stored in the last committed State.
- **FileState**: The metadata record for a single file within a State, containing fileName, fileLength, fileTime, modification type, fileHash, and fileAttributes.

## Requirements

### Requirement 1: Fimignore File Loading and Parsing

**User Story:** As a user, I want to place `.fimignore` files in my repository directories, so that I can specify which files and directories to exclude from integrity tracking.

#### Acceptance Criteria

1. WHEN the StateGenerator begins scanning a directory, THE FimIgnoreManager SHALL check for a Fimignore_File in that directory and load its contents.
2. WHEN a Fimignore_File contains a line prefixed with the All_Directories_Prefix (`**/`), THE FimIgnoreManager SHALL add the pattern (without the prefix) to the FimIgnore all-directories set, making the pattern apply recursively to all subdirectories.
3. WHEN a Fimignore_File contains a line without the All_Directories_Prefix, THE FimIgnoreManager SHALL add the pattern to the FimIgnore local set, making the pattern apply only to the directory containing the Fimignore_File.
4. WHEN the FimIgnoreManager loads a local FimIgnore for a subdirectory, THE FimIgnoreManager SHALL merge the parent FimIgnore all-directories patterns into the child FimIgnore, preserving recursive pattern inheritance.
5. IF a Fimignore_File cannot be read due to an I/O error, THEN THE FimIgnoreManager SHALL log an error message including the file path and error details, and continue scanning with an empty FimIgnore for that directory.
6. WHEN FIM is invoked from a subdirectory of the Repository, THE FimIgnoreManager SHALL traverse parent directories up to the repository root, loading each Fimignore_File and merging all All_Directories_Prefix patterns into the initial FimIgnore.
7. THE traversal in Requirement 1.6 SHALL stop when it reaches the repository root of the Context, or when it runs out of parent directories.
8. THE caller SHALL set a repository root that contains the directory being scanned. The traversal recognises its stopping point by comparing directories, so a repository root outside the scanned tree is never reached and the traversal continues to the top of the filesystem, merging Fimignore_Files from directories unrelated to the repository. See the Duplicate Detection spec, Requirements 10.9 and 10.10, for the `rdup -M` case where these two directories are genuinely different trees.
9. THE FimIgnoreManager SHALL load a Fimignore_File from the directory named by the `user.dir` system property as the starting point of the initial FimIgnore, before the traversal of Requirement 1.6 is applied.
10. THE FimIgnoreManager SHALL record ignored paths relative to the repository root of the Context, so that a repository root outside the scanned tree also produces absolute rather than relative entries in the Ignored_Files_Set.

### Requirement 2: File and Directory Ignore Evaluation

**User Story:** As a user, I want files and directories matching my ignore patterns to be excluded from state generation, so that irrelevant files do not appear in my integrity tracking.

#### Acceptance Criteria

1. WHEN the StateGenerator encounters a directory whose name is in the Ignored_Directories set (`.fim`, `.git`, `.svn`, `.cvs`), THE FimIgnoreManager SHALL classify the directory as ignored regardless of Fimignore_File patterns.
2. WHEN the StateGenerator encounters a file or directory, THE FimIgnoreManager SHALL evaluate the name against the FimIgnore all-directories patterns first, then against the FimIgnore local patterns.
3. WHEN a file or directory name matches any pattern in either the all-directories set or the local set, THE FimIgnoreManager SHALL classify the file or directory as ignored.
4. WHEN a file or directory is classified as ignored, THE FimIgnoreManager SHALL record its relative path (with a trailing `/` for directories) in the Ignored_Files_Set.
5. WHEN a directory is classified as ignored, THE StateGenerator SHALL skip recursive scanning of that directory and all its contents.
6. WHEN a regular file is not classified as ignored, THE StateGenerator SHALL proceed to evaluate the file against Include_Pattern and Exclude_Pattern rules before enqueuing the file for hashing.

### Requirement 3: FilePattern Glob Matching

**User Story:** As a user, I want to use glob-style patterns in my ignore files and CLI options, so that I can flexibly specify which files to include or exclude.

#### Acceptance Criteria

1. THE FilePattern SHALL convert a glob-style pattern string into a compiled regular expression by escaping special regex characters (`\`, `^`, `$`, `{`, `}`, `[`, `]`, `(`, `)`, `.`, `|`, `?`) and converting `*` to `.*`.
2. THE FilePattern SHALL collapse multiple consecutive `*` characters into a single `*` before conversion.
3. THE FilePattern SHALL anchor the compiled pattern with `^` at the start and `$` at the end, ensuring full-name matching rather than partial matching.
4. WHEN the FilePattern `match` method is called with a file name, THE FilePattern SHALL return true if the compiled regular expression matches the file name, and false otherwise.
5. IF the pattern string produces an invalid regular expression during compilation, THEN THE FilePattern SHALL fall back to exact string equality matching for that pattern.
6. FOR ALL valid glob patterns, constructing a FilePattern and calling `match` with the original pattern string (without wildcards) SHALL return true (identity property).
7. THE FilePattern `equals()` and `hashCode()` methods SHALL handle a null `compiled` field safely, using null-safe access (e.g., converting to string only when non-null) to prevent NullPointerException when the pattern failed to compile.

### Requirement 4: CLI Include and Exclude Patterns

**User Story:** As a user, I want to specify include and exclude patterns on the command line, so that I can filter which files are tracked during a specific operation without modifying `.fimignore` files.

#### Acceptance Criteria

1. WHEN the `--include` option is provided with a colon-separated list of patterns, THE FIM CLI SHALL parse each pattern into a FilePattern and store the list as Include_Patterns in the Context.
2. WHEN the `--exclude` option is provided with a colon-separated list of patterns, THE FIM CLI SHALL parse each pattern into a FilePattern and store the list as Exclude_Patterns in the Context.
3. WHEN Include_Patterns are set in the Context, THE StateGenerator SHALL enqueue a regular file for hashing only if the file name matches at least one Include_Pattern.
4. WHEN Exclude_Patterns are set in the Context, THE StateGenerator SHALL skip a regular file if the file name matches any Exclude_Pattern.
5. WHEN both Include_Patterns and Exclude_Patterns are set, THE StateGenerator SHALL enqueue a file only if the file name matches at least one Include_Pattern AND does not match any Exclude_Pattern.
6. WHEN no Include_Patterns are set in the Context, THE StateGenerator SHALL treat all non-ignored regular files as included by default.
7. WHEN no Exclude_Patterns are set in the Context, THE StateGenerator SHALL treat no files as excluded by default.

### Requirement 5: Display Ignored Files Command

**User Story:** As a user, I want to see which files are being ignored, so that I can verify and debug my ignore configuration.

#### Acceptance Criteria

1. WHEN the `display-ignored` command (alias `dign`) is executed, THE DisplayIgnoredFilesCommand SHALL load the last committed State and retrieve its Ignored_Files_Set.
2. WHEN the Ignored_Files_Set contains entries, THE DisplayIgnoredFilesCommand SHALL display each ignored file or directory path, one per line.
3. WHEN the Ignored_Files_Set is empty or null, THE DisplayIgnoredFilesCommand SHALL display "No files or directories ignored into this State".
4. WHEN displaying the last State summary, THE DisplayIgnoredFilesCommand SHALL show the state number, formatted timestamp, file count, and total files content length. Both totals exclude the FileState entries marked deleted that a commit records, as required by the State Management spec, so they describe the files the State actually holds.
5. WHEN the last State has a non-empty comment, THE DisplayIgnoredFilesCommand SHALL display the comment.
6. IF no State exists in the Repository, THEN THE DisplayIgnoredFilesCommand SHALL display "No State found" and return without further output.

### Requirement 6: Reset File Attributes Command

**User Story:** As a user, I want to reset file attributes to match the last committed state, so that I can restore permissions and timestamps that were accidentally changed.

#### Acceptance Criteria

1. WHEN the `reset-file-attrs` command (alias `rfa`) is executed, THE ResetFileAttributesCommand SHALL load the last committed State and display a summary including the formatted timestamp of that State.
2. WHEN the `reset-file-attrs` command is executed, THE ResetFileAttributesCommand SHALL display a confirmation prompt before proceeding with attribute resets.
3. WHEN the user confirms the reset and the operating system is POSIX-compatible, THE ResetFileAttributesCommand SHALL compare each file's current POSIX permissions against the stored PosixFilePermissions attribute and reset permissions that differ.
4. WHEN the user confirms the reset and the operating system is Windows, THE ResetFileAttributesCommand SHALL compare each file's current DOS permissions against the stored DosFilePermissions attribute and reset permissions that differ.
5. WHEN the user confirms the reset, THE ResetFileAttributesCommand SHALL compare each file's current creation time against the stored creation time and reset creation times that differ.
6. WHEN the user confirms the reset, THE ResetFileAttributesCommand SHALL compare each file's current last-modified time against the stored last-modified time and reset last-modified times that differ.
7. WHILE SELinux is enabled on the system, WHEN the user confirms the reset, THE ResetFileAttributesCommand SHALL compare each file's current SELinux label against the stored SELinuxLabel attribute and reset labels that differ.
8. WHEN the command is invoked from a subdirectory of the Repository, THE ResetFileAttributesCommand SHALL filter the last State to include only files within the current subdirectory before performing resets.
9. WHEN attribute resets are performed, THE ResetFileAttributesCommand SHALL display each reset operation showing the file name, old value, and new value.
10. WHEN no file attributes require resetting, THE ResetFileAttributesCommand SHALL display "No file attributes have been reset".
11. WHEN attribute resets complete, THE ResetFileAttributesCommand SHALL display a summary indicating the total number of files whose attributes were reset.
12. IF a file listed in the last State no longer exists on disk, THEN THE ResetFileAttributesCommand SHALL skip that file without reporting an error.
13. IF an error occurs while resetting attributes for a specific file, THEN THE ResetFileAttributesCommand SHALL log the error message and continue processing remaining files.

### Requirement 7: Ignored Files Persistence in State

**User Story:** As a developer, I want ignored file paths to be stored in each State, so that the display-ignored command can retrieve them without re-scanning the file tree.

#### Acceptance Criteria

1. WHEN the StateGenerator completes a file tree scan, THE StateGenerator SHALL store the Ignored_Files_Set collected by the FimIgnoreManager into the generated State object.
2. THE State serializer SHALL include the Ignored_Files_Set in the GZIP-compressed JSON output when persisting a State.
3. WHEN loading a State from disk, THE State deserializer SHALL restore the Ignored_Files_Set from the JSON content.
4. FOR ALL States containing an Ignored_Files_Set, saving to a GZIP-compressed JSON file and then loading from that file SHALL produce an equivalent Ignored_Files_Set (round-trip property).
