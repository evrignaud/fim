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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every command reaches its implementation from the command line, under both its name and its short name.
 *
 * Command lookup, and the repository check that runs before a command does, are only reachable through the
 * command line. A test that constructs a command object and calls execute on it skips both, so a command
 * missing from the registry, or a short name that resolves to the wrong thing, would not be noticed.
 */
@Tag(TestTiers.INTEGRATION)
public class CommandDispatchIT {
    private IntegrationRepository repository;
    private FimCli fim;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        repository = new IntegrationRepository(testInfo);
        fim = repository.fim();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Every command runs, under either name
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Each command, given by its full name and then by its short name, against a repository that has
     * something in it. The pair is checked together so that the two names cannot drift apart.
     */
    @ParameterizedTest(name = "{0} / {1}")
    @CsvSource({
            "status,st",
            "commit,ci",
            "reset-file-attrs,rfa",
            "detect-corruption,dcor",
            "find-duplicates,fdup",
            "log,",
            "display-ignored,dign",
            "purge-states,pst",
    })
    public void commandRunsUnderItsNameAndItsShortName(String name, String shortName) throws Exception {
        givenARepositoryWithSomeFiles();

        fim.succeeds(name, "-y");
        if (shortName != null && !shortName.isEmpty()) {
            fim.succeeds(shortName, "-y");
        }
    }

    /**
     * rollback is checked on its own: it removes a State, so running it twice in one test would not be
     * comparing like with like.
     */
    @Test
    public void rollbackRunsUnderBothNames() throws Exception {
        givenARepositoryWithSomeFiles();

        repository.createFile("added-1");
        fim.succeeds("commit", "-y", "-m", "second");
        assertThat(repository.stateCount()).isEqualTo(2);

        fim.succeeds("rollback", "-y");
        assertThat(repository.stateCount()).isEqualTo(1);

        repository.createFile("added-2");
        fim.succeeds("commit", "-y", "-m", "third");
        fim.succeeds("rbk", "-y");
        assertThat(repository.stateCount()).isEqualTo(1);
    }

    /**
     * remove-duplicates declares DONT_CARE, so it is the one command the repository check lets through
     * whatever state the directory is in. Worth covering by name and short name for that reason.
     */
    @Test
    public void removeDuplicatesRunsUnderBothNames() throws Exception {
        givenARepositoryWithSomeFiles();

        fim.succeeds("remove-duplicates", "-y");
        fim.succeeds("rdup", "-y");
    }

    @Test
    public void diffStillRunsAndSaysItIsDeprecated() throws Exception {
        givenARepositoryWithSomeFiles();

        fim.succeeds("diff", "-y");
        fim.outputContains("deprecated");
    }

    /**
     * help and version need no repository, and are reachable both as a command and as an option.
     */
    @ParameterizedTest
    @ValueSource(strings = { "help", "version", "-h", "-v" })
    public void helpAndVersionNeedNoRepository(String argument) throws Exception {
        fim.succeeds(argument);
        assertThat(fim.output()).isNotEmpty();
    }

    @Test
    public void helpListsEveryCommand() throws Exception {
        fim.succeeds("help");

        // If a command is added to the registry without a description, or dropped from it, this notices.
        fim.outputContains("init", "commit", "status", "diff", "reset-file-attrs", "detect-corruption",
                "find-duplicates", "remove-duplicates", "log", "display-ignored", "rollback", "purge-states",
                "help", "version");
    }

    @Test
    public void versionReportsAVersion() throws Exception {
        fim.succeeds("version");
        fim.outputContains("Fim version");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Exit statuses
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void succeedingReportsZero() throws Exception {
        repository.createFile("file-1");
        assertThat(fim.run("init", "-y", "-m", "first")).isZero();
    }

    @Test
    public void anUnknownCommandIsRefused() throws Exception {
        fim.failsAsBadUsage("not-a-command");
        fim.outputContains("is not a fim command");
    }

    @Test
    public void anUnknownOptionIsRefused() throws Exception {
        givenARepositoryWithSomeFiles();
        fim.failsAsBadUsage("status", "--no-such-option");
    }

    @Test
    public void noArgumentAtAllIsRefused() throws Exception {
        fim.failsAsBadUsage();
        fim.outputContains("You must specify the command to run");
    }

    @Test
    public void aCommandNeedingARepositoryIsRefusedWithoutOne() throws Exception {
        repository.createFile("file-1");

        fim.failsAsBadUsage("status");
        fim.outputContains("Fim repository does not exist");
    }

    @Test
    public void initIsRefusedOnAnExistingRepository() throws Exception {
        givenARepositoryWithSomeFiles();

        fim.failsAsBadUsage("init", "-y");
        fim.outputContains("Fim repository already exist");
    }

    /**
     * Declining a confirmation is not a failure. The user asked a question, answered no, and nothing happened,
     * which fim reports as a success so that a script is not derailed by it.
     */
    @Test
    public void decliningAConfirmationReportsZero() throws Exception {
        repository.createFile("file-1");

        // With no comment given, init asks whether to continue. Answering no leaves no repository behind.
        assertThat(fim.answering("n").run("init")).isZero();

        assertThat(repository.dotFimDir()).doesNotExist();
    }

    // -----------------------------------------------------------------------------------------------------------------

    private void givenARepositoryWithSomeFiles() throws Exception {
        repository.createFiles("file-1", "file-2", "sub/file-3");
        fim.succeeds("init", "-y", "-m", "first");
    }
}
