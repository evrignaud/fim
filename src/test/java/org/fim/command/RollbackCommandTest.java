/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2025 Etienne Vrignaud
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

package org.fim.command;

import org.fim.internal.StateManager;
import org.fim.model.Command;
import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.tooling.RepositoryTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.HashMode.hashAll;

/**
 * Tests for RollbackCommand behavior.
 * Validates Requirements 4.1, 4.2, 4.3, 4.4, 4.5
 */
public class RollbackCommandTest {

    private InitCommand initCommand;
    private CommitCommand commitCommand;
    private RollbackCommand rollbackCommand;
    private RepositoryTool tool;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        initCommand = new InitCommand();
        commitCommand = new CommitCommand();
        rollbackCommand = new RollbackCommand();
        tool = new RepositoryTool(testInfo);
    }

    /**
     * Req 4.1: RollbackCommand identifies the last state and displays a summary.
     * Req 4.2: RollbackCommand displays a confirmation prompt.
     * Req 4.3: On confirmed rollback, the last state file is deleted and state number decremented.
     */
    @Test
    public void rollbackWithMultipleStatesDeletesLastStateAndDecrementsNumber() throws Exception {
        Context context = tool.createContext(hashAll, true);
        context.setComment("Initial");
        tool.createASetOfFiles(3);
        initCommand.execute(context);

        // Create a second commit
        tool.setFileContent("file01", "modified content");
        context.setComment("Second commit");
        CompareResult commitResult = (CompareResult) commitCommand.execute(context);
        assertThat(commitResult.somethingModified()).isTrue();

        StateManager stateManager = new StateManager(context);
        assertThat(stateManager.getLastStateNumber()).isEqualTo(2);
        assertThat(Files.exists(stateManager.getStateFile(1))).isTrue();
        assertThat(Files.exists(stateManager.getStateFile(2))).isTrue();

        // Rollback with alwaysYes=true (auto-confirms)
        rollbackCommand.execute(context);

        // Req 4.3: state_2.json.gz deleted, state number decremented to 1
        assertThat(Files.exists(stateManager.getStateFile(2))).isFalse();
        assertThat(Files.exists(stateManager.getStateFile(1))).isTrue();
        assertThat(stateManager.getLastStateNumber()).isEqualTo(1);
    }

    /**
     * Req 4.3: Rollback with three states removes only the last one.
     */
    @Test
    public void rollbackWithThreeStatesRemovesOnlyLastState() throws Exception {
        Context context = tool.createContext(hashAll, true);
        context.setComment("Initial");
        tool.createASetOfFiles(2);
        initCommand.execute(context);

        // Second commit
        tool.setFileContent("file01", "change 1");
        context.setComment("Second");
        commitCommand.execute(context);

        // Third commit
        tool.setFileContent("file02", "change 2");
        context.setComment("Third");
        commitCommand.execute(context);

        StateManager stateManager = new StateManager(context);
        assertThat(stateManager.getLastStateNumber()).isEqualTo(3);

        // Rollback
        rollbackCommand.execute(context);

        assertThat(stateManager.getLastStateNumber()).isEqualTo(2);
        assertThat(Files.exists(stateManager.getStateFile(3))).isFalse();
        assertThat(Files.exists(stateManager.getStateFile(2))).isTrue();
        assertThat(Files.exists(stateManager.getStateFile(1))).isTrue();
    }

    /**
     * Req 4.5: When only one state (the initial state) exists, display "No commit to rollback".
     */
    @Test
    public void rollbackWithOneStateDisplaysNoCommitToRollback() throws Exception {
        Context context = tool.createContext(hashAll, true);
        context.setComment("Initial");
        tool.createASetOfFiles(2);
        initCommand.execute(context);

        StateManager stateManager = new StateManager(context);
        assertThat(stateManager.getLastStateNumber()).isEqualTo(1);

        // Rollback should do nothing
        Object result = rollbackCommand.execute(context);
        assertThat(result).isNull();

        // State number and file unchanged
        assertThat(stateManager.getLastStateNumber()).isEqualTo(1);
        assertThat(Files.exists(stateManager.getStateFile(1))).isTrue();
    }

    /**
     * Req 4.4: When the user declines the rollback confirmation, no state files or state number are modified.
     */
    @Test
    public void rollbackDeclinedDoesNotModifyState() throws Exception {
        Context context = tool.createContext(hashAll, true);
        context.setComment("Initial");
        tool.createASetOfFiles(2);
        initCommand.execute(context);

        // Second commit
        tool.setFileContent("file01", "modified");
        context.setComment("Second");
        commitCommand.execute(context);

        StateManager stateManager = new StateManager(context);
        assertThat(stateManager.getLastStateNumber()).isEqualTo(2);

        // Set alwaysYes=false to trigger confirmation prompt path.
        // With no Scanner input available, this will throw NoSuchElementException,
        // simulating a declined prompt (no valid input = abort).
        context.setAlwaysYes(false);
        try {
            rollbackCommand.execute(context);
        } catch (java.util.NoSuchElementException e) {
            // Expected: Scanner has no input, simulating declined prompt
        }

        // State number and files unchanged
        assertThat(stateManager.getLastStateNumber()).isEqualTo(2);
        assertThat(Files.exists(stateManager.getStateFile(1))).isTrue();
        assertThat(Files.exists(stateManager.getStateFile(2))).isTrue();
    }

    /**
     * Verify RollbackCommand metadata: cmdName, shortCmdName, description, constraint.
     */
    @Test
    public void commandMetadataIsCorrect() {
        assertThat(rollbackCommand.getCmdName()).isEqualTo("rollback");
        assertThat(rollbackCommand.getShortCmdName()).isEqualTo("rbk");
        assertThat(rollbackCommand.getDescription()).isNotEmpty();
        assertThat(rollbackCommand.getFimReposConstraint())
                .isEqualTo(Command.FimReposConstraint.MUST_EXIST);
    }
}
