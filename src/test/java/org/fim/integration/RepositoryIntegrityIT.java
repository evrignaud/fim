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
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.fim.tooling.FimCli;
import org.fim.tooling.IntegrationRepository;
import org.fim.tooling.TestTiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * What fim does when the repository it depends on is not in the shape it expects.
 *
 * Everything here is about the .fim directory rather than the files being watched. A State that was edited, a
 * State file that went missing, an init that never finished: each is a situation a user can land in, and each
 * has a defined answer in the code that nothing was covering from the outside.
 *
 * These are also the cases where being wrong is expensive. Reporting nothing modified because a State was
 * quietly ignored would defeat the point of the tool, so the tests are as interested in what fim refuses to
 * do as in what it does.
 */
@Tag(TestTiers.INTEGRATION)
public class RepositoryIntegrityIT {
    private IntegrationRepository repository;
    private FimCli fim;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        repository = new IntegrationRepository(testInfo);
        fim = repository.fim();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // A State that no longer agrees with its own hash
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * A State carries a hash of its own content, and comparing against one that was edited is refused.
     *
     * Note what this does not assert: an exit status. The refusal arrives as an IllegalStateException, which
     * main() does not catch, so the command dies on a stack trace rather than the tidy message and status the
     * other failures get. The test is written around the behaviour that exists rather than the one that would
     * be nicer, and will fail the day that is tidied up, which is the point at which someone should look at
     * it again.
     */
    @Test
    public void comparingAgainstAnEditedStateIsRefused() throws Exception {
        repository.createFiles("file-1", "file-2");
        fim.succeeds("init", "-y", "-m", "first");

        repository.tamperWithState(1, "first", "something else entirely");

        assertThatThrownBy(() -> fim.run("st"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("The content of the State file #1 have been modified and may be corrupted");
    }

    /** Committing reads the last State too, so it is refused for the same reason. */
    @Test
    public void committingOnTopOfAnEditedStateIsRefused() throws Exception {
        repository.createFile("file-1");
        fim.succeeds("init", "-y", "-m", "first");
        repository.createFile("file-2");

        repository.tamperWithState(1, "first", "tampered");

        assertThatThrownBy(() -> fim.run("ci", "-y", "-m", "second"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("may be corrupted");

        // The tampered State is still the only one, so nothing was written on top of it.
        assertThat(repository.stateCount()).isEqualTo(1);
    }

    /**
     * Listing the history does not verify the hashes, and is expected not to.
     *
     * log and rollback read States with the integrity check turned off, because they only need the header:
     * the comment, the date, the counts. Being unable to see the history of a repository because one old
     * State was damaged would be worse than showing it. This pins that choice down, so that turning the check
     * on everywhere is a deliberate change rather than an accident.
     */
    @Test
    public void listingTheHistoryStillWorksOnAnEditedState() throws Exception {
        repository.createFile("file-1");
        fim.succeeds("init", "-y", "-m", "first");
        repository.createFile("file-2");
        fim.succeeds("ci", "-y", "-m", "second");

        repository.tamperWithState(1, "first", "edited");

        fim.succeeds("log");
        fim.outputContains("edited", "second");
    }

    /**
     * A State written by a different version of the format is warned about rather than refused, since its
     * hash was computed over a content laid out differently and could not match.
     */
    @Test
    public void aStateFromAnotherModelVersionIsWarnedAboutNotRefused() throws Exception {
        repository.createFile("file-1");
        fim.succeeds("init", "-y", "-m", "first");

        repository.tamperWithState(1, "\"modelVersion\": \"5\"", "\"modelVersion\": \"4\"");

        fim.succeeds("st");
        fim.outputContains("use a different model version");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // A State file that is not there
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * The settings remember which State was the last one. When that file is gone, fim finds the highest one
     * that does exist and writes the corrected number back, instead of failing on a number it cannot use.
     */
    @Test
    public void aMissingLastStateFileIsNoticedAndTheNumberCorrected() throws Exception {
        repository.createFile("file-1");
        fim.succeeds("init", "-y", "-m", "first");
        repository.createFile("file-2");
        fim.succeeds("ci", "-y", "-m", "second");
        assertThat(repository.stateCount()).isEqualTo(2);

        Files.delete(repository.stateFile(2));

        fim.succeeds("st");
        fim.outputContains("lastStateNumber desynchronized. Resetting it to 1.");

        // Having gone back to State #1, the file added by the second commit looks new again.
        fim.outputContains("1 added", "file-2");

        // The correction was saved, so the next run does not have to make it again.
        fim.succeeds("st");
        fim.outputDoesNotContain("desynchronized");
    }

    /** Once the number is corrected, committing carries on from there rather than leaving a hole. */
    @Test
    public void committingAfterACorrectionReusesTheFreedNumber() throws Exception {
        repository.createFile("file-1");
        fim.succeeds("init", "-y", "-m", "first");
        repository.createFile("file-2");
        fim.succeeds("ci", "-y", "-m", "second");

        Files.delete(repository.stateFile(2));

        fim.succeeds("ci", "-y", "-m", "second again");

        assertThat(repository.stateFile(2)).exists();
        fim.succeeds("log");
        fim.outputContains("first", "second again");
        fim.outputDoesNotContain("Comment: second\n");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // An init that never finished
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * A .fim directory holding only the progress of an interrupted init is not a repository yet, and every
     * other command says so and points at what to do about it.
     */
    @Test
    public void everyCommandExplainsThatAnInitWasInterrupted() throws Exception {
        repository.createFiles("file-1", "file-2", "file-3");
        fim.succeeds("init", "-y", "-m", "first");
        repository.makeInitLookInterrupted();

        for (String command : new String[] { "st", "ci", "log", "fdup", "dign" }) {
            fim.failsAsBadUsage(command, "-y");
            fim.outputContains("The previous 'fim init' has been interrupted. Please run 'fim init' again to resume it.");
        }
    }

    /** Running init again picks the saved hashes back up rather than reading every file a second time. */
    @Test
    public void initResumesFromTheProgressLeftBehind() throws Exception {
        repository.createFiles("file-1", "file-2", "file-3");
        fim.succeeds("init", "-y", "-m", "first");
        repository.makeInitLookInterrupted();

        fim.succeeds("init", "-y", "-m", "second attempt");
        fim.outputContains("Resuming the interrupted 'fim init'", "is already known");

        // The repository is whole again: one State, and nothing left to resume.
        assertThat(repository.stateCount()).isEqualTo(1);
        fim.succeeds("st");
        fim.outputContains("Nothing modified");
    }

    /** Damaged progress is thrown away and everything hashed again, rather than trusted. */
    @Test
    public void damagedProgressIsDiscardedAndEverythingHashedAgain() throws Exception {
        repository.createFiles("file-1", "file-2");
        fim.succeeds("init", "-y", "-m", "first");
        repository.tamperWithState(1, "first", "damaged");
        repository.makeInitLookInterrupted();

        fim.succeeds("init", "-y", "-m", "second attempt");
        fim.outputContains("The progress of the interrupted 'fim init' is corrupted. Hashing everything again.");

        assertThat(repository.stateCount()).isEqualTo(1);
        fim.succeeds("st");
        fim.outputContains("Nothing modified");
    }

    /**
     * A .fim directory that holds neither a State nor any progress is something fim did not make, and it
     * refuses to take it over.
     */
    @Test
    public void anUnrelatedDotFimDirectoryIsNotTakenOver() throws Exception {
        repository.createFile("file-1");
        repository.createDirectory(".fim");
        repository.createFile(".fim/notes.txt");

        fim.failsAsBadUsage("init", "-y", "-m", "first");
        fim.outputContains("already exists but does not hold any State");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // A repository that cannot be written to
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Being unable to write the repository is reported as -2, which is the third exit status and the only one
     * no test was reaching.
     *
     * Windows is left out because taking the write permission away from a directory there does not stop the
     * owner from writing in it, so there is no way to arrange the situation this covers.
     */
    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "a read-only directory does not stop its owner from writing on Windows")
    public void aRepositoryThatCannotBeWrittenToIsReportedSeparately() throws Exception {
        repository.createFile("file-1");
        fim.succeeds("init", "-y", "-m", "first");
        repository.createFile("file-2");

        repository.setPermissions(".fim/states", "r-xr-xr-x", null);
        try {
            fim.failsOnRepository("ci", "-y", "-m", "second");
            fim.outputContains("Not allowed to modify States into");
        } finally {
            repository.setPermissions(".fim/states", "rwxr-xr-x", null);
        }
    }
}
