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
import org.fim.tooling.FimCli;
import org.fim.tooling.IntegrationRepository;
import org.fim.tooling.TestTiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * The commands that manage the history of States, and the options that shape what a command prints.
 *
 * log, rollback and purge-states each had command-level tests but were never reached through the command line,
 * so neither their names nor the wiring of -p, -q, -o, -m and -c was covered. purge-states in particular is
 * destructive and had no end-to-end test at all.
 */
@Tag(TestTiers.INTEGRATION)
public class StateHistoryIT {
    private IntegrationRepository repository;
    private FimCli fim;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        repository = new IntegrationRepository(testInfo);
        fim = repository.fim();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // log
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void logReportsEveryStateWithItsComment() throws Exception {
        givenThreeStates();

        fim.succeeds("log");
        fim.outputContains("first", "second", "third");
    }

    @Test
    public void logOnAFreshRepositoryReportsTheFirstStateOnly() throws Exception {
        repository.createFile("file-1");
        fim.succeeds("init", "-y", "-m", "only one");

        fim.succeeds("log");
        fim.outputContains("only one");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // rollback
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void rollbackRemovesTheLastStateOneAtATime() throws Exception {
        givenThreeStates();
        assertThat(repository.stateCount()).isEqualTo(3);

        fim.succeeds("rbk", "-y");
        assertThat(repository.stateCount()).isEqualTo(2);

        fim.succeeds("rbk", "-y");
        assertThat(repository.stateCount()).isEqualTo(1);
    }

    /**
     * The State that init created is the baseline and cannot be rolled back, so a repository never ends up
     * with a .fim holding no State at all.
     */
    @Test
    public void rollbackWillNotRemoveTheFirstState() throws Exception {
        givenThreeStates();

        fim.succeeds("rbk", "-y");
        fim.succeeds("rbk", "-y");
        assertThat(repository.stateCount()).isEqualTo(1);

        fim.succeeds("rbk", "-y");
        fim.outputContains("No commit to rollback");
        assertThat(repository.stateCount()).isEqualTo(1);
    }

    /**
     * What rollback discards is the record, never the files. After rolling back, status compares against the
     * earlier State again and reports the difference that had been committed.
     */
    @Test
    public void rollbackDiscardsTheRecordAndNotTheFiles() throws Exception {
        repository.createFile("file-1");
        fim.succeeds("init", "-y", "-m", "first");

        repository.createFile("file-2");
        fim.succeeds("ci", "-y", "-m", "second");
        fim.succeeds("st");
        fim.outputContains("Nothing modified");

        fim.succeeds("rbk", "-y");

        assertThat(repository.exists("file-2")).isTrue();
        fim.succeeds("st");
        fim.outputContains("1 added");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // purge-states
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void purgeStatesKeepsOnlyTheLastStateAndRenumbersIt() throws Exception {
        givenThreeStates();
        assertThat(repository.stateCount()).isEqualTo(3);

        fim.succeeds("pst", "-y");

        assertThat(repository.stateCount()).isEqualTo(1);
        assertThat(repository.stateFile(1)).exists();
        assertThat(repository.stateFile(2)).doesNotExist();

        // The surviving State is still the newest one, so nothing is reported as modified.
        fim.succeeds("st");
        fim.outputContains("Nothing modified");
    }

    @Test
    public void purgingWithNothingToPurgeSaysSo() throws Exception {
        repository.createFile("file-1");
        fim.succeeds("init", "-y", "-m", "first");

        fim.succeeds("pst", "-y");
        fim.outputContains("No State to purge");
        assertThat(repository.stateCount()).isEqualTo(1);
    }

    /** -p folds the purge into a successful commit, leaving that commit as the only State. */
    @Test
    public void commitWithPurgeLeavesOnlyTheNewState() throws Exception {
        givenThreeStates();
        assertThat(repository.stateCount()).isEqualTo(3);

        repository.createFile("file-4");
        fim.succeeds("ci", "-y", "-p", "-m", "fourth with purge");

        assertThat(repository.stateCount()).isEqualTo(1);
        fim.succeeds("log");
        fim.outputContains("fourth with purge");
        fim.outputDoesNotContain("first");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // detect-corruption
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * A file whose content changed while both of its timestamps stayed put is what a silent corruption looks
     * like. status reports it too, but dcor reports only that, which is the point of having it.
     */
    @Test
    public void corruptionIsDetectedWhenContentChangesWithoutTheTimestamps() throws Exception {
        repository.createFiles("intact.txt", "damaged.txt");
        fim.succeeds("init", "-y", "-m", "first");

        repository.corruptContentKeepingTimestamps("damaged.txt", "something else entirely");

        fim.succeeds("dcor");
        fim.outputContains("damaged.txt", "1 corrupted");
        fim.outputDoesNotContain("intact.txt");
    }

    @Test
    public void nothingIsReportedWhenNothingIsCorrupted() throws Exception {
        repository.createFiles("one.txt", "two.txt");
        fim.succeeds("init", "-y", "-m", "first");

        fim.succeeds("dcor");
        fim.outputContains("Nothing corrupted");
    }

    /**
     * A file that was edited normally is not a corruption: its modification time moved, which is exactly the
     * evidence that separates the two.
     */
    @Test
    public void anOrdinaryModificationIsNotReportedAsCorruption() throws Exception {
        repository.createFile("edited.txt");
        fim.succeeds("init", "-y", "-m", "first");

        repository.setContent("edited.txt", "edited on purpose");
        repository.touchLastModified("edited.txt");

        fim.succeeds("dcor");
        fim.outputDoesNotContain("1 corrupted");
    }

    /** Detecting corruption means comparing content, so the modes that hash less are refused. */
    @Test
    public void corruptionDetectionRequiresFullHashing() throws Exception {
        repository.createFile("file-1");
        fim.succeeds("init", "-y", "-m", "first");

        fim.failsAsBadUsage("dcor", "-n");
        fim.failsAsBadUsage("dcor", "-s");
        fim.failsAsBadUsage("dcor", "-f");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // reset-file-attrs
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Changing the permissions of a file is a change of attributes rather than of content, and rfa puts them
     * back the way the last State recorded them.
     */
    @Test
    public void resetFileAttributesPutsThePermissionsBack() throws Exception {
        repository.createFile("guarded.txt");
        fim.succeeds("init", "-y", "-m", "first");

        repository.setReadOnly("guarded.txt");

        fim.succeeds("st");
        fim.outputContains("guarded.txt");

        fim.succeeds("rfa", "-y");
        fim.outputContains("guarded.txt");

        // Once reset, the repository agrees with its last State again.
        fim.succeeds("st");
        fim.outputContains("Nothing modified");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // display-ignored
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void displayIgnoredListsWhatTheScanLeftOut() throws Exception {
        repository.createFiles("kept.txt", "skipped.log");
        repository.writeFimIgnore("", "*.log");
        fim.succeeds("init", "-y", "-m", "first");

        fim.succeeds("dign");
        fim.outputContains("skipped.log");
    }

    @Test
    public void displayIgnoredSaysSoWhenNothingWasIgnored() throws Exception {
        repository.createFile("kept.txt");
        fim.succeeds("init", "-y", "-m", "first");

        fim.succeeds("dign");
        fim.outputContains("No files or directories ignored");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Options that shape the output
    // -----------------------------------------------------------------------------------------------------------------

    /** -q keeps the summary and drops the line-per-file detail. */
    @Test
    public void quietReportsTheCountsWithoutTheDetail() throws Exception {
        repository.createFile("file-1");
        fim.succeeds("init", "-y", "-m", "first");
        repository.createFiles("added-1", "added-2", "added-3");

        fim.succeeds("st");
        fim.outputContains("Added:", "3 added");

        fim.succeeds("st", "-q");
        fim.outputContains("3 added");
        fim.outputDoesNotContain("Added:");
    }

    @Test
    public void outputMaxLinesTruncatesTheDetail() throws Exception {
        repository.createFile("file-1");
        fim.succeeds("init", "-y", "-m", "first");
        repository.createFiles("added-1", "added-2", "added-3", "added-4", "added-5");

        fim.succeeds("st", "-o", "2");
        fim.outputContains("Too many lines. Truncating the output", "3 files more", "5 added");
    }

    /** Asking for no lines at all leaves the counts, which is the summary without the listing. */
    @Test
    public void outputMaxLinesOfZeroDropsTheDetailEntirely() throws Exception {
        repository.createFile("file-1");
        fim.succeeds("init", "-y", "-m", "first");
        repository.createFiles("added-1", "added-2");

        fim.succeeds("st", "-o", "0");
        fim.outputContains("2 added");
        fim.outputDoesNotContain("Added:");
    }

    @Test
    public void theCommentIsRecordedAndShownByLog() throws Exception {
        repository.createFile("file-1");
        fim.succeeds("init", "-y", "-m", "a memorable comment");

        fim.succeeds("log");
        fim.outputContains("a memorable comment");
    }

    /** The old comment option still works and says it is on the way out. */
    @Test
    public void theDeprecatedCommentOptionStillWorksAndWarns() throws Exception {
        repository.createFile("file-1");

        fim.succeeds("init", "-y", "-c", "set the old way");
        fim.outputContains("'-c' option is deprecated");

        fim.succeeds("log");
        fim.outputContains("set the old way");
    }

    /**
     * Without -e, a failure is summarised as the kind of exception and its message, which is all a user
     * needs to see.
     */
    @Test
    public void aFailureIsSummarisedWithoutTheErrorsOption() throws Exception {
        repository.createFile("file-1");
        fim.succeeds("init", "-y", "-m", "first");

        fim.failsAsBadUsage("st", "-t", "plenty");
        fim.outputContains("Exception parsing command line", "NumberFormatException", "plenty");
        fim.outputDoesNotContain("\tat org.fim.");
    }

    /** With -e, the stack trace behind that failure is printed as well. */
    @Test
    public void theErrorsOptionAddsTheStackTraceBehindAFailure() throws Exception {
        repository.createFile("file-1");
        fim.succeeds("init", "-y", "-m", "first");

        fim.failsAsBadUsage("st", "-t", "plenty", "-e");
        fim.outputContains("Exception parsing command line", "NumberFormatException", "\tat org.fim.Fim");
    }

    /** It is accepted on a run that works too, where there is nothing to report. */
    @Test
    public void theErrorsOptionIsAcceptedWhenNothingFails() throws Exception {
        repository.createFile("file-1");
        fim.succeeds("init", "-y", "-e", "-m", "first");

        fim.succeeds("st", "-e");
        fim.outputContains("Nothing modified");
    }

    // -----------------------------------------------------------------------------------------------------------------

    private void givenThreeStates() throws Exception {
        repository.createFile("file-1");
        fim.succeeds("init", "-y", "-m", "first");

        repository.createFile("file-2");
        fim.succeeds("ci", "-y", "-m", "second");

        repository.createFile("file-3");
        fim.succeeds("ci", "-y", "-m", "third");
    }
}
