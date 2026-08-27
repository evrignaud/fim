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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The questions fim asks before doing something that cannot be undone, and what each answer leads to.
 *
 * Every one of these commands destroys something: a State file, the attributes of a file, the chance to
 * record a comment. The confirmation is the last thing standing between a user and that, so the answer that
 * matters most is no. Until now the suites all passed -y, which is precisely the flag that skips the question,
 * so no test had ever seen one asked.
 *
 * The answer is read from standard input, and the check throughout is not only the exit status but that the
 * thing in question is still there afterwards.
 */
@Tag(TestTiers.INTEGRATION)
public class ConfirmationPromptIT {
    private IntegrationRepository repository;
    private FimCli fim;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        repository = new IntegrationRepository(testInfo);
        fim = repository.fim();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Committing with no comment
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void decliningACommitWithoutACommentRecordsNothing() throws Exception {
        givenARepository();
        repository.createFile("file-3");

        assertThat(fim.answering("n").run("ci")).isZero();
        fim.outputContains("No comment provided", "Do you really want to continue (y/n/A)?");

        assertThat(repository.stateCount()).isEqualTo(1);
        fim.succeeds("st");
        fim.outputContains("1 added");
    }

    @Test
    public void acceptingACommitWithoutACommentRecordsIt() throws Exception {
        givenARepository();
        repository.createFile("file-3");

        assertThat(fim.answering("y").run("ci")).isZero();

        assertThat(repository.stateCount()).isEqualTo(2);
        fim.succeeds("st");
        fim.outputContains("Nothing modified");
    }

    /** Giving a comment answers the question in advance, so it is not asked. */
    @Test
    public void aCommentMeansNoQuestion() throws Exception {
        givenARepository();
        repository.createFile("file-3");

        fim.succeeds("ci", "-m", "with a comment");
        fim.outputDoesNotContain("Do you really want to");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Rolling back the last State
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void decliningARollbackKeepsTheState() throws Exception {
        givenARepository();
        repository.createFile("file-3");
        fim.succeeds("ci", "-y", "-m", "second");

        assertThat(fim.answering("n").run("rbk")).isZero();
        fim.outputContains("Do you really want to remove it (y/n/A)?");

        assertThat(repository.stateCount()).isEqualTo(2);
        assertThat(repository.stateFile(2)).exists();
    }

    @Test
    public void acceptingARollbackRemovesTheState() throws Exception {
        givenARepository();
        repository.createFile("file-3");
        fim.succeeds("ci", "-y", "-m", "second");

        assertThat(fim.answering("y").run("rbk")).isZero();

        assertThat(repository.stateFile(2)).doesNotExist();
        assertThat(repository.stateCount()).isEqualTo(1);

        // Back on the first State, the file added by the second commit is new again.
        fim.succeeds("st");
        fim.outputContains("1 added", "file-3");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Purging the previous States
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void decliningAPurgeKeepsEveryState() throws Exception {
        givenThreeStates();

        assertThat(fim.answering("n").run("pst")).isZero();
        fim.outputContains("You are going to delete the 2 previous State files",
                "Do you really want to remove them (y/n/A)?");

        assertThat(repository.stateCount()).isEqualTo(3);
    }

    /**
     * The State that survives a purge is renumbered to be the first one, rather than keeping the number it
     * had and leaving a gap. Two and three are gone and one now holds what three held.
     */
    @Test
    public void acceptingAPurgeKeepsOnlyTheLastStateRenumberedAsTheFirst() throws Exception {
        givenThreeStates();

        assertThat(fim.answering("y").run("pst")).isZero();

        assertThat(repository.stateCount()).isEqualTo(1);
        assertThat(repository.stateFile(1)).exists();
        assertThat(repository.stateFile(2)).doesNotExist();
        assertThat(repository.stateFile(3)).doesNotExist();

        // Only the third comment is left, under the first number.
        fim.succeeds("log");
        fim.outputContains("third");
        fim.outputDoesNotContain("first", "second");

        // The surviving State is still usable, which is the whole point of keeping it.
        fim.succeeds("st");
        fim.outputContains("Nothing modified");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Resetting file attributes
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void decliningAnAttributeResetLeavesTheFileAlone() throws Exception {
        givenARepository();
        repository.setReadOnly("file-1");

        assertThat(fim.answering("n").run("rfa")).isZero();
        fim.outputContains("You are going to reset files attributes", "Do you really want to continue (y/n/A)?");

        // The attribute is still wrong, so status still has something to report.
        fim.succeeds("st");
        fim.outputContains("file-1");
    }

    @Test
    public void acceptingAnAttributeResetPutsThePermissionsBack() throws Exception {
        givenARepository();
        repository.setReadOnly("file-1");

        assertThat(fim.answering("y").run("rfa")).isZero();

        fim.succeeds("st");
        fim.outputContains("Nothing modified");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // How an answer is read
    // -----------------------------------------------------------------------------------------------------------------

    /** Yes is not case sensitive, so a capital Y works as well as a small one. */
    @ParameterizedTest
    @ValueSource(strings = { "y", "Y" })
    public void yesIsAcceptedInEitherCase(String answer) throws Exception {
        givenARepository();
        repository.createFile("file-3");
        fim.succeeds("ci", "-y", "-m", "second");

        assertThat(fim.answering(answer).run("rbk")).isZero();
        assertThat(repository.stateFile(2)).doesNotExist();
    }

    /**
     * Anything that is not a yes is a no. A confirmation that took an unrecognised answer as agreement would
     * be the worst kind of bug in a prompt guarding a deletion, so each of these has to leave the State alone.
     *
     * A lone capital A is left out on purpose: it means yes, and is covered below.
     */
    @ParameterizedTest
    @ValueSource(strings = { "n", "N", "no", "yes", "maybe", "1", "a", "-" })
    public void anythingThatIsNotYesLeavesTheStateAlone(String answer) throws Exception {
        givenARepository();
        repository.createFile("file-3");
        fim.succeeds("ci", "-y", "-m", "second");

        assertThat(fim.answering(answer).run("rbk")).isZero();
        assertThat(repository.stateFile(2)).exists();
    }

    /** A capital A means yes, and also that nothing more should be asked for the rest of the run. */
    @Test
    public void aCapitalAMeansYes() throws Exception {
        givenARepository();
        repository.createFile("file-3");
        fim.succeeds("ci", "-y", "-m", "second");

        assertThat(fim.answering("A").run("rbk")).isZero();
        assertThat(repository.stateFile(2)).doesNotExist();
    }

    /** Declining is not a failure. The user was asked, and answered, so fim reports success. */
    @Test
    public void decliningIsReportedAsSuccessNotAsFailure() throws Exception {
        givenARepository();
        repository.createFile("file-3");
        fim.succeeds("ci", "-y", "-m", "second");

        assertThat(fim.answering("n").run("rbk")).isZero();
    }

    /** With -y nothing is asked, whatever is waiting on standard input. */
    @Test
    public void alwaysYesSkipsTheQuestionEntirely() throws Exception {
        givenThreeStates();

        assertThat(fim.answering("n").run("pst", "-y")).isZero();
        fim.outputDoesNotContain("Do you really want to");
        assertThat(repository.stateCount()).isEqualTo(1);
    }

    // -----------------------------------------------------------------------------------------------------------------

    private void givenARepository() throws Exception {
        repository.createFiles("file-1", "file-2");
        fim.succeeds("init", "-y", "-m", "first");
    }

    private void givenThreeStates() throws Exception {
        givenARepository();
        repository.createFile("file-3");
        fim.succeeds("ci", "-y", "-m", "second");
        repository.createFile("file-4");
        fim.succeeds("ci", "-y", "-m", "third");
        assertThat(repository.stateCount()).isEqualTo(3);
    }
}
