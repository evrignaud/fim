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

import org.fim.tooling.FimCli;
import org.fim.tooling.IntegrationRepository;
import org.fim.tooling.TestTiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A day in the life of a repository, walked through the command line once per hash mode.
 *
 * The existing scenario test covers this ground by calling command objects directly, and skips the duplicate
 * handling for two of the four modes. This one goes through the command line so that the mode reaches the
 * commands the way a user sets it, with an option, and it covers every mode to the same depth.
 *
 * The files are several megabytes each, which is what puts the block hashing modes on their real code path:
 * a file larger than four blocks is hashed as three separate blocks, while a small one is read whole and every
 * mode collapses to the same behaviour. That size is also why this belongs in the slow tier.
 */
@Tag(TestTiers.SLOW)
public class FullLifecycleSlowIT {
    /** Above four medium blocks, so the medium mode hashes three separate blocks rather than the whole file. */
    private static final int FIVE_MEGABYTES = 5 * 1_024 * 1_024;

    /** Inside the first block every hashing mode reads, so a change there is visible to all of them. */
    private static final long INSIDE_THE_FIRST_BLOCK = 5_000;

    private IntegrationRepository repository;
    private FimCli fim;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        // The mode is part of the directory name, so the four runs cannot tread on each other.
        repository = new IntegrationRepository(testInfo, "run");
        fim = repository.fim();
    }

    /**
     * @param hashModeOption   the option that selects the mode, empty for the default of hashing everything
     * @param detectsContent   whether the mode compares content at all
     */
    @ParameterizedTest(name = "hash mode {1}")
    @CsvSource({
            "'',full,true",
            "-f,fast,true",
            "-s,super-fast,true",
            "-n,do not hash,false",
    })
    public void aRepositoryIsInitialisedChangedCommittedAndRolledBack(
            String hashModeOption, String modeName, boolean detectsContent) throws Exception {

        String[] mode = hashModeOption.isEmpty() ? new String[0] : new String[] { hashModeOption };

        // --- Initialise -------------------------------------------------------------------------------------
        givenAFewLargeFiles();

        fim.succeeds(withMode(mode, "init", "-y", "-m", "first state"));
        fim.outputContains("using '" + modeName + "' mode", "Repository initialized");
        assertThat(repository.stateCount()).isEqualTo(1);
        assertThat(repository.settingsFile()).exists();

        fim.succeeds(withMode(mode, "st"));
        fim.outputContains("Nothing modified");

        // --- Change the tree --------------------------------------------------------------------------------
        // Content changed in place, so the length stays and only the mode decides whether it is noticed.
        repository.overwriteBytesKeepingSizeAndTimestamps("modified.bin", INSIDE_THE_FIRST_BLOCK, "CHANGED!");
        repository.touchLastModified("modified.bin");

        repository.move("to-rename.bin", "renamed.bin");
        repository.delete("to-delete.bin");
        repository.createFile("added.bin", "newly added", FIVE_MEGABYTES);

        // --- Look at it -------------------------------------------------------------------------------------
        fim.succeeds(withMode(mode, "st"));
        fim.outputContains("added.bin");

        if (detectsContent) {
            // Content is compared, so the edit is an edit and the move is recognised as the same content.
            fim.outputContains("modified.bin", "1 content modified", "1 renamed", "1 deleted");
        } else {
            // Without hashing there is nothing to recognise a moved file by, so it reads as one file gone and
            // another arrived, and the edit shows only through its modification time.
            fim.outputContains("modified.bin", "1 date modified", "2 added", "2 deleted");
            fim.outputDoesNotContain("1 renamed");
        }

        // --- Commit -----------------------------------------------------------------------------------------
        fim.succeeds(withMode(mode, "ci", "-y", "-m", "second state"));
        assertThat(repository.stateCount()).isEqualTo(2);

        fim.succeeds(withMode(mode, "st"));
        fim.outputContains("Nothing modified");

        // --- The history ------------------------------------------------------------------------------------
        fim.succeeds("log");
        fim.outputContains("first state", "second state");

        // --- Roll it back -----------------------------------------------------------------------------------
        fim.succeeds("rbk", "-y");
        assertThat(repository.stateCount()).isEqualTo(1);

        // The files were never touched by the rollback, so the changes are pending again.
        assertThat(repository.exists("added.bin")).isTrue();
        assertThat(repository.exists("renamed.bin")).isTrue();
        fim.succeeds(withMode(mode, "st"));
        fim.outputContains("added.bin");
    }

    /**
     * Duplicate handling, once per mode that is able to do it. Recognising a duplicate means comparing
     * content, so the mode that hashes nothing is refused rather than given a wrong answer.
     */
    @ParameterizedTest(name = "hash mode {1}")
    @CsvSource({
            "'',full,true",
            "-f,fast,true",
            "-s,super-fast,true",
            "-n,do not hash,false",
    })
    public void duplicatesAreFoundAndRemovedByEveryModeThatHashes(
            String hashModeOption, String modeName, boolean canFindDuplicates) throws Exception {

        String[] mode = hashModeOption.isEmpty() ? new String[0] : new String[] { hashModeOption };

        givenAFewLargeFiles();
        repository.copy("keep.bin", "keep-copy-1.bin");
        repository.copy("keep.bin", "keep-copy-2.bin");
        fim.succeeds(withMode(mode, "init", "-y", "-m", "with duplicates"));

        if (!canFindDuplicates) {
            fim.failsAsBadUsage(withMode(mode, "fdup"));
            fim.outputContains("File content hashing mandatory for this command");
            return;
        }

        fim.succeeds(withMode(mode, "fdup"));
        fim.outputContains("Duplicate set #1", "of total wasted space");

        fim.succeeds(withMode(mode, "rdup", "-y"));

        // One of the three survives and the repository is consistent once the removal is recorded.
        int remaining = 0;
        for (String candidate : new String[] { "keep.bin", "keep-copy-1.bin", "keep-copy-2.bin" }) {
            if (repository.exists(candidate)) {
                remaining++;
            }
        }
        assertThat(remaining).isEqualTo(1);

        fim.succeeds(withMode(mode, "ci", "-y", "-m", "after removing duplicates"));
        fim.succeeds(withMode(mode, "st"));
        fim.outputContains("Nothing modified");
    }

    // -----------------------------------------------------------------------------------------------------------------

    private void givenAFewLargeFiles() throws Exception {
        repository.createFile("keep.bin", "kept unchanged", FIVE_MEGABYTES);
        repository.createFile("modified.bin", "to be edited", FIVE_MEGABYTES);
        repository.createFile("to-rename.bin", "to be moved", FIVE_MEGABYTES);
        repository.createFile("to-delete.bin", "to be removed", FIVE_MEGABYTES);
    }

    /** Put the hash mode option, when there is one, in front of the arguments. */
    private static String[] withMode(String[] mode, String... arguments) {
        String[] full = new String[mode.length + arguments.length];
        System.arraycopy(arguments, 0, full, 0, arguments.length);
        System.arraycopy(mode, 0, full, arguments.length, mode.length);
        return full;
    }
}
