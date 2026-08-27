/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2026 Etienne Vrignaud
 *
 * Fim is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Fim is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Fim.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.fim.integration;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import org.fim.tooling.IntegrationRepository;
import org.fim.tooling.TestTiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * Removing duplicates against another repository used as the master, through the command line.
 *
 * This is the one operation fim performs across two repositories, and the only part of it reachable from a
 * test was the failure when the master directory does not exist. The path that actually deletes files was
 * exercised by setting the master directory on a Context, which skips the option parsing and the check that
 * the master is a repository at all.
 *
 * The scenario is the one the option exists for: a backup that was copied from a source long ago, where the
 * files still present in the source can be dropped and the ones that only exist in the backup must be kept.
 */
@Tag(TestTiers.INTEGRATION)
public class MasterRepositoryIT {
    private IntegrationRepository source;
    private IntegrationRepository backup;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        source = new IntegrationRepository(testInfo, "source");
        backup = new IntegrationRepository(testInfo, "backup");
    }

    @Test
    public void filesAlsoHeldByTheMasterAreRemovedAndTheOthersAreKept() throws Exception {
        givenASourceAndAnOlderBackup();

        backup.fim().succeeds("rdup", "-y", "-M", source.rootDir().toString());

        // Held by the source as well, so the backup no longer needs them.
        assertThat(backup.exists("shared-1.txt")).isFalse();
        assertThat(backup.exists("shared-2.txt")).isFalse();

        // Only in the backup, so they are the reason the backup is worth keeping.
        assertThat(backup.exists("only-in-backup.txt")).isTrue();
        assertThat(backup.exists("changed.txt")).isTrue();
    }

    /**
     * The decision is made on content. A file whose content differs from the source copy is not a duplicate
     * of it, even though it has the same name.
     */
    @Test
    public void aFileWithTheSameNameButDifferentContentIsKept() throws Exception {
        givenASourceAndAnOlderBackup();

        backup.fim().succeeds("rdup", "-y", "-M", source.rootDir().toString());
        backup.fim().outputContains("shared-1.txt");
        backup.fim().outputDoesNotContain("changed.txt");
    }

    /** A file the source moved elsewhere is still the same content, so the backup copy is still a duplicate. */
    @Test
    public void aFileTheMasterMovedIsStillRecognised() throws Exception {
        givenASourceAndAnOlderBackup();

        source.createDirectory("archive");
        source.move("shared-1.txt", "archive/shared-1.txt");
        source.fim().succeeds("ci", "-y", "-m", "moved into archive");

        backup.fim().succeeds("rdup", "-y", "-M", source.rootDir().toString());

        assertThat(backup.exists("shared-1.txt")).isFalse();
    }

    @Test
    public void theMasterIsLeftUntouched() throws Exception {
        givenASourceAndAnOlderBackup();

        backup.fim().succeeds("rdup", "-y", "-M", source.rootDir().toString());

        assertThat(source.exists("shared-1.txt")).isTrue();
        assertThat(source.exists("shared-2.txt")).isTrue();
        source.fim().succeeds("st");
        source.fim().outputContains("Nothing modified");
    }

    @Test
    public void nothingHappensWhenTheBackupSharesNothing() throws Exception {
        source.createFile("source-only.txt", "from the source");
        source.fim().succeeds("init", "-y", "-m", "source");

        backup.createFile("backup-only.txt", "from the backup");
        backup.fim().succeeds("init", "-y", "-m", "backup");

        backup.fim().succeeds("rdup", "-y", "-M", source.rootDir().toString());
        backup.fim().outputContains("No duplicate file found");
        assertThat(backup.exists("backup-only.txt")).isTrue();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // What is refused
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void aMasterDirectoryThatDoesNotExistIsRefused() throws Exception {
        givenASourceAndAnOlderBackup();

        backup.fim().failsAsBadUsage("rdup", "-y", "-M", backup.rootDir().resolve("nowhere").toString());
        backup.fim().outputContains("does not exist");
    }

    @Test
    public void aMasterDirectoryThatIsNotARepositoryIsRefused() throws Exception {
        givenASourceAndAnOlderBackup();
        backup.createDirectory("not-a-repository");

        backup.fim().failsAsBadUsage("rdup", "-y", "-M",
                backup.rootDir().resolve("not-a-repository").toString());
        backup.fim().outputContains("is not a Fim repository");
    }

    /** Deduplicating a directory against itself would delete the very files it is comparing against. */
    @Test
    public void usingTheSameDirectoryAsMasterIsRefused() throws Exception {
        givenASourceAndAnOlderBackup();

        backup.fim().failsAsBadUsage("rdup", "-y", "-M", backup.rootDir().toString());
        backup.fim().outputContains("Cannot remove duplicates into the master directory");
    }

    /** Content hashing is how a duplicate is recognised, so the mode that hashes nothing cannot serve. */
    @Test
    public void removingAgainstAMasterWithoutHashingIsRefused() throws Exception {
        givenASourceAndAnOlderBackup();

        backup.fim().failsAsBadUsage("rdup", "-y", "-n", "-M", source.rootDir().toString());
        backup.fim().outputContains("File content hashing mandatory for this command");
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * A source repository, and a backup that was copied from it and has since drifted: two files are still
     * shared, one was only ever in the backup, and one was edited there.
     */
    private void givenASourceAndAnOlderBackup() throws Exception {
        source.createFile("shared-1.txt", "the first shared file");
        source.createFile("shared-2.txt", "the second shared file");
        source.createFile("changed.txt", "as the source has it");
        source.fim().succeeds("init", "-y", "-m", "source");

        backup.createFile("shared-1.txt", "the first shared file");
        backup.createFile("shared-2.txt", "the second shared file");
        backup.createFile("changed.txt", "as the backup has it, which is different");
        backup.createFile("only-in-backup.txt", "nowhere else");
        backup.fim().succeeds("init", "-y", "-m", "backup");
    }
}
