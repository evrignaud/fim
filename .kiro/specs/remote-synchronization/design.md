# Design Document: Remote Synchronization

## Overview

This design covers the feature asked for in [issue #18](https://github.com/evrignaud/fim/issues/18):
synchronizing two Fim repositories through a `git push` / `git pull` kind of interface, with named remotes.

**Nothing described here is implemented.** The document exists to separate the part of the work that is
ordinary from the part that is a decision, because the two are very unevenly distributed. Moving files between
two directories is ordinary. Deciding *whether* they may be moved is not, and that decision needs information
the State file format does not carry.

The reporter's problem is worth restating, because it shapes the phasing:

> I have 3 HD: A, B and C. The content of A and B is backed up on C. I want to keep C synchronised, being sure
> about data integrity.

Two things follow from it. First, what is wanted is confidence about a backup, and confidence is deliverable
before transfer is. Second, A, B and C already exist as repositories that were each initialized on their own,
so they have no shared history at all — which is the case a naive `push` handles worst.

## What already exists

Worth being precise about, because it is more than it looks.

| Capability | Where | Note |
|---|---|---|
| Two-repository operation | `RemoveDuplicatesCommand` (`-M`) | Loads the peer's committed State, scans the local tree, matches on `FileHash`. This is the template for Phase 1. |
| Reading a peer's States | `StateManager` against a cloned `Context` | `Context` derives `.fim` and `.fim/states` from `repositoryRootDir`, so repointing a clone is all it takes. |
| Locations on network shares | `FileUtil.isWritableDirectory` / `isWritableFile` | A CIFS/SMB mount reports ownership and mode bits of the mount rather than of the account in use, so writability is decided by probing. Fixed for issue #25. |
| Cross-platform State files | `FileUtil.getNormalizedFileName` / `getRelativeFileName` | Names are stored repository-relative with `/` separators, so a State is readable on any supported OS. |
| Per-state identity | `State.stateHash` | SHA-512/Ascii85 over Model_Version, timestamp, comment, counts, Hash_Mode, ignored files and every FileState. |
| Integrity refusal | `State.loadFromGZipFile` → `CorruptedStateException` | A peer's altered State is already rejected rather than used. |

So a Location that is a mounted path is already reachable, and a peer's State is already readable. Phase 1
needs no new infrastructure at all.

## What is missing

Five things, in increasing order of cost.

**1. Positional arguments.** `Fim.parseCommandDetails` removes `argv[0]` as the command name and hands the
rest to commons-cli; `buildCommand` reads only options. Nothing in `src/main` calls `getArgList()` or
`getArgs()`. So `fim remote add backup /mnt/backup` has nowhere to deliver `backup` and `/mnt/backup`. The
positional arguments have to be captured into `Context`, and — easy to miss — added to `Context.clone()`,
since `run()` executes `command.execute(context.clone())` and a field absent from `clone()` is silently
dropped.

**2. Persisted remotes.** `Settings` holds `globalHashMode` and `lastStateNumber` and nothing else. Adding
`List<Remote>` is genuinely cheap: `SettingsManager` uses Gson, `Settings` has an implicit no-arg
constructor, so Gson runs the field initializers and a `settings.json` written before the change deserializes
to an empty list rather than to null. The settings file is not covered by any integrity hash and has no
version field.

**3. A peer that is not a local path.** Everything derives from `Context.getRepositoryRootDir()`, a
`java.nio.file.Path`. Mounted volumes, SMB/CIFS mounts and UNC paths are therefore already supported by doing
nothing. `uuid://` is a resolution problem, solvable by calling out to the platform the way `SELinux` and
`DosFilePermissions` already do through `CommandUtil`. `ftp://` is a different matter: it needs a filesystem
abstraction that does not exist, and `FileHasher.hashBuffer` memory-maps content through
`FileChannel.map`, which does not work through an arbitrary non-local provider. A streaming fallback for
hashing is a prerequisite, and it is not small.

Also note the existing entry under *Known limitations* in the changelog: `-M` does not work in the Docker
image. Whatever replaces or joins it inherits that problem.

**4. History lineage.** This is the decision. Described in its own section below.

**5. The transfer, and the destination's State.** Copying is ordinary. What is not ordinary is that after a
transfer the destination must hold a State that describes the files it actually holds, including when the
transfer was interrupted. Fim's whole value is that a State can be trusted, so a half-applied push must not
leave a State claiming content that is not there.

## The decision: State lineage

### Why the current model cannot answer the question

The issue asks for `push` to "check that the two locations are synchronised, with the source of the operation
ahead of any given commit in respect with the destination". Fim cannot currently evaluate that.

State_Numbers cannot serve. They order the States of one repository and nothing more, and they are not even
stable within one: `PurgeStatesCommand` deletes `state_1` through `state_N-1`, moves `state_N` to `state_1`
and sets the last state number to 1. Two repositories both reporting "last state is 1" tells you nothing.

State_Hashes are better than they first appear. `State.hashObject` covers `timestamp`, so two commits of an
identical tree made at different moments have different hashes — a State_Hash is a per-state identity, not a
content identity. What is missing is only the link: a State says nothing about which State came before it,
so a set of hashes on one side cannot be placed against a set on the other.

### The proposed change

Add a nullable `parentStateHash` to `State`, set at commit time from the State_Hash that was last in the
repository, absent on the first State. Include it in `hashObject` **only when it is present**.

That last detail is what keeps the change from invalidating existing repositories. `hashObject` hashes the
sequence it is told to hash; a State with no parent produces exactly the byte sequence it produces today, so
every State file already on disk still passes its integrity check, and the warning in `State.hashObject`
about pinning the hashed representation is respected. States written afterwards hash their parent too, which
means a State_Hash then identifies the State's entire ancestry, the way a git commit id does, and tampering
with lineage is caught by the integrity check that already exists.

With that, the four answers fall out by set membership over each side's State_Hashes: equal last hashes means
in sync; the destination's last hash appearing in the source's history means a Fast_Forward; the reverse
means the destination is ahead; each side holding hashes the other does not means Divergence.

### Why the Model_Version still has to be raised

Serialization is not the constraint — `JsonIO` sets `Include.NON_NULL`, so an absent parent is simply not
written. Deserialization is. `JsonIO` leaves `FAIL_ON_UNKNOWN_PROPERTIES` at its default, and
`State.loadFromGZipFile` deserializes *before* it looks at `modelVersion`.

This was measured rather than reasoned about, by feeding this Fim a State file carrying a `parentStateHash`
field. Both with the file declaring `"modelVersion": "5"` and with it declaring `"6"`, loading failed
identically:

```
com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException: Unrecognized field "parentStateHash"
(class org.fim.model.State), not marked as ignorable (11 known properties: ...)
```

Two conclusions, and the second is the uncomfortable one:

- The Model_Version has to go to `"6"`, so that versions from here on can tell a lineage-carrying State file
  apart from one without.
- Raising it buys **nothing** for versions already released. They fail inside Jackson before reaching the
  branch that warns "State ... use a different model version. Some features will not work completely." That
  branch has never been reachable for a State file that adds a field, only for one that changes the meaning of
  existing fields.

So if a released Fim meeting a newer State file should say something useful rather than surface a Jackson
error, `FAIL_ON_UNKNOWN_PROPERTIES` has to be disabled and *released* before any new field is introduced. That
ordering cannot be recovered later, which is why it is the first open question rather than an implementation
detail.

### Migration, and the case the reporter is actually in

Within one repository the commit order is recorded by the State_Numbers, so the missing parent links of an
existing repository can be derived by reading `state_1` upward, without rewriting any file. A repository
upgrades itself in place, lazily.

What migration cannot do is invent a shared history between repositories that never had one. A, B and C were
each initialized separately, so they hold no State_Hash in common and never will. Their relationship is
*unrelated*, which is a third answer distinct from "diverged", and reporting it as divergence would be
actively misleading — nothing is in conflict, they simply have no common point.

That is why Baseline_Adoption is a required part of the feature and not a refinement: without it, `push` is
unusable for exactly the situation that prompted the request. Adopting a baseline declares two repositories
synchronized as of a given State, after which ordinary Fast_Forward reasoning applies. It should be explicit
and should say what it is trusting, since it is the one operation in the feature that asserts something Fim
has not verified.

`purge-states` interacts here too. It truncates history, so a repository's oldest surviving State refers to a
parent that is gone. Where the common ancestor would have fallen in the removed part, the relationship cannot
be established, and the honest answer is to say so rather than to guess. This is the same shape as a git
shallow clone.

## Phasing

### Phase 1 — Comparison, no format change

`fim status -M <peer>`, or a Location where `-M` is accepted today. Loads the peer's last committed State,
scans the local tree, and reports the differences through the existing status output. This is
`RemoveDuplicatesCommand`'s `-M` path with `StateComparator` in place of the `FileHash` map, and it needs
none of the five missing pieces above.

It answers "does the backup still hold what the source holds", which is the reporter's stated concern, and it
is useful whether or not the later phases are ever built. It also cannot paint the later phases into a
corner, since it adds no persisted data and no file format change.

Two things to get right, both learned from existing fixes:

- Give each tree its own `Context` carrying the repository root it actually describes. Repointing one Context
  at the peer is what made `rdup -M` apply unrelated `.fimignore` files, issue #13.
- Refuse a peer whose Global_Hash_Mode cannot be compared with the one in use. Two repositories describing
  their files to different depths cannot be compared on content, the same reason a resumed `init` will not
  mix hash modes.

### Phase 2 — Lineage

The `parentStateHash` field, its derivation for existing repositories, the Model_Version bump, and a command
that reports the relationship between two repositories. Still no transfer, so a mistake here cannot move or
delete a file.

Sequencing the report before the transfer is deliberate: it makes the relationship logic observable on real
repositories, which is where the cases that are easy to reason about wrongly — unrelated histories, purged
history — will actually show up.

### Phase 3 — Remotes and transfer

Positional arguments, the `remote` command and the persisted list, then `push`/`pull` over a Location that is
a path. Baseline_Adoption belongs here, as the thing that makes the phase usable at all on pre-existing
repositories.

`uuid://` resolution is an independent addition once a Location is more than a path. `ftp://` stays out until
hashing can work without memory-mapping.

## Correctness properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system.*

### Property 1: Comparison changes nothing

*For any* two repositories, comparing them should leave every file, State file and settings file in both
repositories byte-identical to what it was before.

**Validates: Requirements 1.3**

### Property 2: Existing State files keep verifying

*For any* State file written before the lineage change, loading it after the change should pass its integrity
check, and its recomputed State_Hash should equal the one stored in it.

**Validates: Requirements 2.4**

### Property 3: A State_Hash covers its ancestry

*For any* two histories that differ at any point, the State_Hash of their last States should differ, even
when those two last States describe an identical file tree with an identical timestamp and comment.

**Validates: Requirements 2.3**

### Property 4: The relationship is exactly one of five answers

*For any* two repositories, the relationship reported should be exactly one of in sync, local ahead, peer
ahead, diverged, or unrelated, and it should be symmetric: comparing B to A should report the mirror of
comparing A to B.

**Validates: Requirements 2.7, 2.8**

### Property 5: Derived lineage matches recorded lineage

*For any* repository created before the lineage change, deriving the parent links from the State_Number
ordering should produce the same links that committing the same sequence of States after the change would
have recorded.

**Validates: Requirements 2.5**

### Property 6: A transfer never loses committed history

*For any* two repositories and *for any* transfer that completes, every State the destination held before the
transfer should still be reachable in its history afterwards.

**Validates: Requirements 4.2, 4.3**

### Property 7: The destination's State describes the destination

*For any* transfer, completed or interrupted, the last committed State of the destination should describe the
files the destination actually holds.

**Validates: Requirements 4.6, 4.9**

## Open questions

These need answering before Phase 2 starts, and none of them is answerable from the code.

1. Should `FAIL_ON_UNKNOWN_PROPERTIES` be disabled and *released* before any new State field is introduced?
   Measured above: without that, a released Fim meeting a lineage-carrying State file surfaces a Jackson
   `UnrecognizedPropertyException` rather than the model-version warning, whatever version the file declares.
   The ordering cannot be recovered afterwards.
2. Is a State's history meant to be immutable once it can be pushed? `rollback` and `purge-states` both
   rewrite history and are the two commands a synchronized repository makes dangerous.
3. Should a transfer be allowed between repositories with different Global_Hash_Modes, and if so, which one
   wins at the destination?
4. Is Baseline_Adoption acceptable as an explicit user assertion, or should the first synchronization instead
   verify the two trees fully, at the cost of hashing both sides in their entirety?
