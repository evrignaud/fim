# Requirements Document

## Introduction

This document specifies the requirements for Remote Synchronization in FIM (File Integrity Manager), the
feature asked for in [issue #18](https://github.com/evrignaud/fim/issues/18): a `git push` / `git pull` kind
of interface able to synchronize two Fim repositories, with named remotes and an assurance that the two sides
agree on the content.

Unlike the other specs in this directory, this one describes a feature that **is not implemented**. It is
written to make the work reviewable before any of it is built, because the cost is not spread evenly: the
transfer is ordinary work, while deciding the relationship between two repositories requires information the
State file format does not currently carry.

The requirements are therefore grouped in three phases that can ship independently:

- **Phase 1 — Comparison.** Answer "does the backup match the source?" without changing the State file
  format. This is the phase that already answers the reporter's stated problem, and nothing in it constrains
  the later phases.
- **Phase 2 — Lineage.** Give States the ancestry needed to tell "ahead", "behind", "in sync" and "diverged"
  apart. This is a State file format change and is the decision that gates the rest.
- **Phase 3 — Named remotes and transfer.** The interface the issue describes.

## Glossary

- **FIM**: File Integrity Manager — a Java CLI tool for managing file integrity of complete file trees.
- **Repository**: A directory tree managed by FIM, identified by the presence of a `.fim` directory at its root.
- **State**: A GZIP-compressed JSON snapshot holding a FileState entry for every tracked file, as defined in the State Management spec.
- **State_Number**: The integer in a State file name (`state_<number>.json.gz`). It orders the States of one repository and carries no meaning across repositories.
- **State_Hash**: The SHA-512 hash, Ascii85 encoded, that every State carries over its own content. It covers Model_Version, timestamp, comment, file count, content length, Hash_Mode, ignored files and every FileState.
- **Model_Version**: The State file format version, currently `"5"`.
- **Local_Repository**: The repository the command is run from.
- **Peer_Repository**: The other repository a command compares against or transfers with.
- **Remote**: A name given to the location of a Peer_Repository, persisted in the Settings_File.
- **Location**: Where a Peer_Repository is. A filesystem path, which covers a locally mounted volume as well as an SMB/CIFS mount or a UNC path.
- **Settings_File**: The `.fim/settings.json` file holding repository-wide configuration.
- **Divergence**: The condition where each of two repositories holds at least one committed State the other does not, so neither can be brought to the other by adding States.
- **Fast_Forward**: The condition where one repository's history is a prefix of the other's, so the shorter one can be brought to the longer one by adding States.
- **Parent_State_Hash**: The State_Hash of the State committed immediately before a given State in the same repository. Absent on the first State.
- **Shallow_Repository**: A repository whose earlier States were removed by `purge-states`, so its oldest surviving State refers to a Parent_State_Hash that is no longer present.
- **Baseline_Adoption**: Declaring two repositories with unrelated histories to be synchronized from a given point, which is what allows repositories that were each initialized on their own to be used together afterwards.

## Requirements

### Requirement 1: Comparing a repository against a peer (Phase 1)

**User Story:** As someone keeping a backup disk, I want to be told whether the backup still holds exactly
what the source holds, so that I learn about a difference before I need the backup.

#### Acceptance Criteria

1. WHEN the `status` command is executed with a Location naming a Peer_Repository, THE FIM CLI SHALL compare the last committed State of the Peer_Repository against the current content of the Local_Repository and report the differences using the existing status output.
2. THE comparison SHALL report, for the Local_Repository relative to the Peer_Repository, the files that are added, the files that are deleted, and the files whose content differs.
3. THE comparison SHALL be read-only. It SHALL NOT create a State, modify a State, or write, move or delete any file in either repository.
4. IF the Location does not hold a `.fim` directory, THEN THE FIM CLI SHALL report that the directory is not a Fim repository and abort.
5. IF the Location resolves to the Local_Repository itself or to a directory containing it, THEN THE FIM CLI SHALL report the misuse and abort, as `remove-duplicates` already does for its master directory.
6. WHEN the Peer_Repository was initialized with a Global_Hash_Mode that cannot be compared with the one in use, THE FIM CLI SHALL report it and abort rather than report differences it cannot substantiate. Two repositories describing their files to different depths cannot be compared on content.
7. THE comparison SHALL rely only on information present in State files of Model_Version 5, so that it works on repositories created by any released version of Fim.

### Requirement 2: State lineage (Phase 2)

**User Story:** As a user synchronizing two repositories, I want Fim to know which of the two is ahead, so
that it refuses an operation that would lose committed history instead of performing it.

#### Acceptance Criteria

1. THE State SHALL carry the Parent_State_Hash of the State committed before it in the same repository, and SHALL carry none on the first State.
2. WHEN a State is committed, THE StateManager SHALL set its Parent_State_Hash from the State_Hash of the State that was last in the repository at that moment.
3. THE State_Hash SHALL cover the Parent_State_Hash when one is present, so that a State identifies its whole ancestry and altering the ancestry is detected by the existing integrity check.
4. WHEN a State carries no Parent_State_Hash, THE State_Hash SHALL be computed over exactly the byte sequence it is computed over today, so that every State file written by an earlier version of Fim still passes its integrity check.
5. WHEN the States of a repository created before this change are read, THE StateManager SHALL derive the missing Parent_State_Hash values from the State_Number ordering, which is the recorded commit order within one repository, and SHALL do so without rewriting the State files.
6. THE Model_Version SHALL be raised, so that versions of Fim from that point on can tell a State file carrying lineage apart from one without. Raising it does not help versions already released: deserialization rejects unknown fields and happens before the Model_Version is inspected, so those fail inside Jackson whatever version the file declares. This was measured.
7. GIVEN two repositories, WHEN their histories are compared, THE comparison SHALL report exactly one of: in sync, the Local_Repository is ahead, the Peer_Repository is ahead, or the two have diverged.
8. IF the two repositories have no State_Hash in common, THEN THE comparison SHALL report that their histories are unrelated, which is distinct from Divergence and is the expected state of two repositories that were each initialized on their own.
9. IF either repository is a Shallow_Repository and the common ancestor would fall in the removed part of its history, THEN THE comparison SHALL report that it cannot establish the relationship rather than assume one.
10. THE FIM CLI SHOULD stop rejecting State file fields it does not know, and that change SHOULD be released before the field of requirement 2.1 is introduced, so that a released version meeting a newer State file can report the model version difference instead of surfacing a deserialization error. The ordering cannot be recovered afterwards.

### Requirement 3: Named remotes (Phase 3)

**User Story:** As a user synchronizing the same two repositories repeatedly, I want to name the other side
once, so that I do not retype its location on every command.

#### Acceptance Criteria

1. THE FIM CLI SHALL provide a `remote` command able to add a Remote under a name, to change the Location of a Remote, to remove a Remote, and to list the Remotes.
2. THE Remotes SHALL be persisted in the Settings_File of the Local_Repository.
3. WHEN a Settings_File written before this change is read, THE SettingsManager SHALL treat it as holding no Remote, so that existing repositories keep working.
4. THE FIM CLI SHALL accept a Remote name wherever it accepts a Location.
5. WHEN a Remote is added, THE FIM CLI SHALL verify that its Location holds a `.fim` directory and report it when it does not, so that a name is not recorded against a location that cannot serve.
6. THE `remote` command SHALL take its name and its Location as positional arguments, which requires the CLI to pass positional arguments through to commands. Nothing in Fim reads them today.

### Requirement 4: Transfer (Phase 3)

**User Story:** As someone keeping a backup disk, I want one command to bring the backup up to date with the
source, so that keeping them synchronized does not depend on my remembering what changed.

#### Acceptance Criteria

1. THE FIM CLI SHALL provide a `push` command sending the content of the Local_Repository to a Peer_Repository, and a `pull` command bringing content the other way, the two being one operation differing only in direction.
2. WHEN a transfer is requested, THE FIM CLI SHALL establish the relationship of the two histories first, and SHALL proceed only when the destination can be brought to the source by a Fast_Forward.
3. IF the two repositories have diverged, THEN THE FIM CLI SHALL abort, naming the States each side holds that the other does not.
4. IF the two histories are unrelated, THEN THE FIM CLI SHALL abort and point at Baseline_Adoption, which is the only way two separately initialized repositories can become synchronized.
5. THE transfer SHALL copy only the files whose content the destination does not already hold, decided on the recorded hashes rather than on timestamps.
6. WHEN the transfer has completed, THE destination SHALL hold a committed State describing the files it now actually holds, so that a transfer that was interrupted leaves a repository whose State still describes its content.
7. THE FIM CLI SHALL confirm before a transfer that would delete files at the destination, and SHALL honour the existing `-y` option.
8. THE FIM CLI SHALL accept a subdirectory on either side, so that part of a repository can be synchronized on its own.
9. THE transfer SHALL verify what it wrote at the destination, so that a transfer reports success only for content the destination can be shown to hold.

### Requirement 5: Locations (Phase 3)

**User Story:** As a user whose backup disk is not always mounted in the same place, I want to name it in a
way that survives being mounted elsewhere.

#### Acceptance Criteria

1. THE FIM CLI SHALL accept as a Location any path the filesystem can reach, which covers a locally mounted volume, an SMB/CIFS mount and a UNC path. These need no new mechanism: a Location is used as a path, and creating a repository on a CIFS share was already made to work by the fix for issue #25.
2. WHEN a Location cannot be reached, THE FIM CLI SHALL report it and abort, rather than treat an unmounted volume as an empty repository.
3. THE FIM CLI MAY accept a volume identifier as a Location, resolving it to its current mount point, which is what makes a Location survive a volume being mounted elsewhere. Resolution is platform-specific and would follow the pattern already used to call out to the system for SELinux labels and DOS permissions.
4. A Location naming a protocol Fim cannot reach as a path, such as FTP, is out of scope. Supporting one requires a filesystem abstraction that does not exist, and file hashing memory-maps file content, which does not work through an arbitrary non-local filesystem provider. A streaming fallback for hashing would be a prerequisite.

### Requirement 6: Behaviour shared with the rest of Fim

#### Acceptance Criteria

1. Every command added by this feature SHALL report a Peer_Repository whose State files fail their integrity check as corrupted, and SHALL refuse to use them, as `StateManager` already does.
2. Every command added by this feature SHALL derive each repository's paths from its own Context, so that the `.fimignore` files applied to a directory tree are those of the repository that tree belongs to. Repointing a single Context at another root is what caused issue #13.
3. Every command added by this feature SHALL work between repositories on different operating systems. State files record repository-relative names with `/` separators, so they are already portable.
4. THE FIM CLI SHALL keep the existing `-M` option working as it does today for `remove-duplicates`.
