# Implementation Plan: Remote Synchronization

## Overview

None of this is implemented. Unlike the other plans in this directory, which verify code that already exists,
this one builds a feature from nothing.

The phases ship independently and in order. Phase 1 is worth doing on its own merits and commits to nothing.
Phase 2 changes the State file format and should not be started before the open questions at the end of the
design document are answered. Phase 3 depends on Phase 2.

## Phase 1 — Comparing a repository against a peer

- [ ] 1. Compare the local tree against a peer's committed State
  - [ ] 1.1 Accept a peer Location on the `status` command
    - Extend the existing `-M` handling so `status` accepts it, keeping `remove-duplicates` behaviour unchanged
    - Validate the peer the way `RemoveDuplicatesCommand` does: it holds a `.fim` directory, it is not the local repository, and the local repository is not inside it
    - Report an unreachable Location rather than treating it as an empty repository
    - _Requirements: 1.1, 1.4, 1.5, 5.2, 6.4_
  - [ ] 1.2 Run the comparison through StateComparator
    - Give each tree its own `Context` carrying the repository root it describes, as the fix for issue #13 established, so `.fimignore` resolution stays correct
    - Load the peer's last committed State, scan the local tree, and feed both to `StateComparator` so the existing status output reports the differences
    - Refuse a peer whose Global_Hash_Mode cannot be compared with the one in use
    - _Requirements: 1.2, 1.6, 6.1, 6.2_
  - [ ] 1.3 Test the comparison
    - Identical trees reporting nothing; added, deleted and content-modified files each reported
    - A peer that is not a repository, a peer that is the local repository, a peer containing the local repository, an unreachable Location
    - A peer whose States are corrupted being refused
    - A `.fimignore` above the scanned directory not being applied to the peer's tree, mirroring the regression test added for issue #13
    - **Covers Property 1**
    - _Requirements: 1.1 to 1.7_
  - [ ] 1.4 Document it
    - The FAQ, as the way to check a backup against its source
    - _Requirements: 1.1_

## Phase 2 — State lineage

Blocked on the open questions in the design document. In particular, whether
`FAIL_ON_UNKNOWN_PROPERTIES` should be disabled and released before any new State field is introduced.

- [ ] 2. Record what came before each State
  - [ ] 2.1 Stop rejecting unknown State fields, and get it released before anything else here
    - Measured: a State file carrying a field this Fim does not know fails with a Jackson
      `UnrecognizedPropertyException` before `modelVersion` is ever inspected, whatever version the file declares
    - So this has to reach users **before** task 2.2, or no released version will ever report the model version
      difference gracefully. The ordering cannot be recovered afterwards
    - _Requirements: 2.10_
  - [ ] 2.2 Add the parent link
    - Nullable `parentStateHash` on `State`, absent on the first State, set at commit time from the State_Hash that was last in the repository
    - Hash it in `hashObject` only when present, so a State without a parent hashes exactly the byte sequence it hashes today
    - Raise `CURRENT_MODEL_VERSION` to `"6"`
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.6_
  - [ ] 2.3 Derive the lineage of existing repositories
    - Read `state_1` upward to fill the missing links from the recorded commit order, without rewriting any State file
    - _Requirements: 2.5_
  - [ ] 2.4 Report the relationship between two repositories
    - Decide in sync, local ahead, peer ahead, diverged, or unrelated, by set membership over each side's State_Hashes
    - Report an unrelated pair as unrelated rather than as diverged, and point at Baseline_Adoption
    - Report that the relationship cannot be established when a purged history removed the point where the common ancestor would fall
    - _Requirements: 2.7, 2.8, 2.9_
  - [ ] 2.5 Test the lineage
    - Every State file committed to the repository as a fixture still verifying after the change
    - Two identical trees committed at the same timestamp with different ancestry hashing differently
    - The five relationships, each also checked in the mirror direction
    - Derived links matching recorded links for the same commit sequence
    - A purged history reporting that it cannot establish the relationship
    - **Covers Properties 2, 3, 4, 5**
    - _Requirements: 2.1 to 2.9_

## Phase 3 — Named remotes and transfer

- [ ] 3. Pass positional arguments through to commands
  - [ ] 3.1 Capture them
    - Read `getArgList()` in `buildCommand` into `Context`, and add the field to `Context.clone()`, since commands execute on a clone and a field missing from it is silently dropped
    - _Requirements: 3.6_
- [ ] 4. Persist named remotes
  - [ ] 4.1 Add the remotes to the settings
    - `List<Remote>` on `Settings` with accessors on `SettingsManager`; a settings file written earlier reads back as holding none, because Gson runs the field initializers for absent keys
    - _Requirements: 3.2, 3.3_
  - [ ] 4.2 Add the `remote` command
    - Add, set, remove and list; verify on add that the Location holds a `.fim` directory
    - Accept a Remote name wherever a Location is accepted
    - _Requirements: 3.1, 3.4, 3.5_
- [ ] 5. Transfer
  - [ ] 5.1 Adopt a baseline
    - Declare two repositories with unrelated histories synchronized from a given State, stating what is being trusted
    - Needed for the reporter's own case: A, B and C were each initialized separately and share no history
    - _Requirements: 4.4_
  - [ ] 5.2 Push and pull
    - One operation, two directions. Establish the relationship first and proceed only on a Fast_Forward
    - Abort on divergence, naming the States each side holds that the other does not
    - Copy only content the destination does not already hold, decided on recorded hashes rather than timestamps
    - Confirm before deleting at the destination, honouring `-y`
    - Accept a subdirectory on either side
    - _Requirements: 4.1, 4.2, 4.3, 4.5, 4.7, 4.8_
  - [ ] 5.3 Leave the destination describing itself
    - Commit a State at the destination describing the files it now holds, including after an interruption
    - Verify what was written before reporting success
    - _Requirements: 4.6, 4.9_
  - [ ] 5.4 Test the transfer
    - Fast-forward applied, divergence refused, unrelated histories refused and pointed at adoption
    - Only missing content copied; a subdirectory transfer touching nothing outside it
    - An interrupted transfer leaving a destination whose State describes its actual content
    - Between repositories whose States were written on a different operating system
    - **Covers Properties 6, 7**
    - _Requirements: 4.1 to 4.9, 6.3_

## Out of scope

- `ftp://` and any Location that is not reachable as a filesystem path. It needs a filesystem abstraction that
  does not exist, and hashing memory-maps content through `FileChannel.map`, which does not work through an
  arbitrary non-local provider. A streaming fallback for hashing is a prerequisite and is its own piece of work.

## Notes

- Locations that are mounted volumes, SMB/CIFS mounts or UNC paths need no new mechanism, since a Location is
  used as a path. Creating a repository on a CIFS share was already made to work for issue #25.
- Volume identifier resolution (`uuid://`) is an independent addition once a Location is more than a path, and
  would follow the pattern already used to call out to the platform for SELinux labels and DOS permissions.
- `-M` does not work in the Docker image, listed under *Known limitations* in the changelog. Anything that
  joins or replaces it inherits that.
- `rollback` and `purge-states` both rewrite history and are the two existing commands that a synchronized
  repository makes dangerous. Open question 2 in the design document.
