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
import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.model.LogResult;
import org.fim.model.State;
import org.fim.tooling.RepositoryTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

public class PurgeStatesCommandTest {
    private InitCommand initCommand;
    private CommitCommand commitCommand;
    private LogCommand logCommand;
    private PurgeStatesCommand purgeStatesCommand;

    private RepositoryTool tool;
    private Context context;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        tool = new RepositoryTool(testInfo);
        context = tool.getContext();

        initCommand = new InitCommand();
        commitCommand = new CommitCommand();
        purgeStatesCommand = new PurgeStatesCommand();
        logCommand = new LogCommand();
    }

    @Test
    public void canPurgePreviousStates() throws Exception {
        tool.createOneFile();
        State state = (State) initCommand.execute(context);
        assertThat(state.getModificationCounts().getAdded()).isEqualTo(1);

        assertLogSizeIsEqualTo(context, 1);

        tool.createOneFile();
        commitAndAssertModificationCountIsEqualTo(context, 1);

        assertLogSizeIsEqualTo(context, 2);

        tool.createOneFile();
        commitAndAssertModificationCountIsEqualTo(context, 1);

        assertLogSizeIsEqualTo(context, 3);

        int statesPurgedCount = (int) purgeStatesCommand.execute(context);
        assertThat(statesPurgedCount).isEqualTo(2);

        assertLogSizeIsEqualTo(context, 1);

        // We cannot purge more
        statesPurgedCount = (int) purgeStatesCommand.execute(context);
        assertThat(statesPurgedCount).isEqualTo(0);

        assertLogSizeIsEqualTo(context, 1);
    }

    /**
     * Req 6.1, 6.3: Purge with multiple states deletes all old files, renumbers last to state_1, sets state number to 1.
     */
    @Test
    public void purgeWithMultipleStatesDeletesOldFilesAndRenumbersLastToOne() throws Exception {
        Context ctx = tool.createContext(org.fim.model.HashMode.hashAll, true);
        ctx.setComment("Initial");
        tool.createASetOfFiles(2);
        initCommand.execute(ctx);

        // Second commit
        tool.setFileContent("file01", "modified v2");
        ctx.setComment("Second");
        commitCommand.execute(ctx);

        // Third commit
        tool.setFileContent("file02", "modified v2");
        ctx.setComment("Third");
        commitCommand.execute(ctx);

        StateManager stateManager = new StateManager(ctx);
        assertThat(stateManager.getLastStateNumber()).isEqualTo(3);
        assertThat(Files.exists(stateManager.getStateFile(1))).isTrue();
        assertThat(Files.exists(stateManager.getStateFile(2))).isTrue();
        assertThat(Files.exists(stateManager.getStateFile(3))).isTrue();

        // Load the last state before purge to compare content after renumbering
        State lastStateBefore = stateManager.loadState(3, false);

        int purgedCount = (int) purgeStatesCommand.execute(ctx);
        assertThat(purgedCount).isEqualTo(2);

        // Req 6.3: Only state_1.json.gz remains, state number is 1
        assertThat(stateManager.getLastStateNumber()).isEqualTo(1);
        assertThat(Files.exists(stateManager.getStateFile(1))).isTrue();
        assertThat(Files.exists(stateManager.getStateFile(2))).isFalse();
        assertThat(Files.exists(stateManager.getStateFile(3))).isFalse();

        // Verify the surviving state_1 is the former last state (same comment)
        State survivingState = stateManager.loadState(1, false);
        assertThat(survivingState.getComment()).isEqualTo(lastStateBefore.getComment());
        assertThat(survivingState.getFileCount()).isEqualTo(lastStateBefore.getFileCount());
    }

    /**
     * Req 6.5: When only one state exists, display "No State to purge" and take no action.
     */
    @Test
    public void purgeWithOneStateReturnsZeroAndDoesNothing() throws Exception {
        Context ctx = tool.createContext(org.fim.model.HashMode.hashAll, true);
        ctx.setComment("Initial");
        tool.createASetOfFiles(2);
        initCommand.execute(ctx);

        StateManager stateManager = new StateManager(ctx);
        assertThat(stateManager.getLastStateNumber()).isEqualTo(1);

        int purgedCount = (int) purgeStatesCommand.execute(ctx);
        assertThat(purgedCount).isEqualTo(0);

        // State number and file unchanged
        assertThat(stateManager.getLastStateNumber()).isEqualTo(1);
        assertThat(Files.exists(stateManager.getStateFile(1))).isTrue();
    }

    /**
     * Req 6.4: When the user declines the purge confirmation, no state files are deleted.
     */
    @Test
    public void purgeDeclinedDoesNotDeleteAnyStates() throws Exception {
        Context ctx = tool.createContext(org.fim.model.HashMode.hashAll, true);
        ctx.setComment("Initial");
        tool.createASetOfFiles(2);
        initCommand.execute(ctx);

        // Second commit
        tool.setFileContent("file01", "modified");
        ctx.setComment("Second");
        commitCommand.execute(ctx);

        StateManager stateManager = new StateManager(ctx);
        assertThat(stateManager.getLastStateNumber()).isEqualTo(2);

        // Set alwaysYes=false to trigger confirmation prompt path.
        // With no Scanner input available, this will throw NoSuchElementException,
        // simulating a declined prompt (no valid input = abort).
        ctx.setAlwaysYes(false);
        try {
            purgeStatesCommand.execute(ctx);
        } catch (java.util.NoSuchElementException e) {
            // Expected: Scanner has no input, simulating declined prompt
        }

        // State number and files unchanged
        assertThat(stateManager.getLastStateNumber()).isEqualTo(2);
        assertThat(Files.exists(stateManager.getStateFile(1))).isTrue();
        assertThat(Files.exists(stateManager.getStateFile(2))).isTrue();
    }

    /**
     * Verify PurgeStatesCommand metadata: cmdName, shortCmdName, description, constraint.
     */
    @Test
    public void commandMetadataIsCorrect() {
        assertThat(purgeStatesCommand.getCmdName()).isEqualTo("purge-states");
        assertThat(purgeStatesCommand.getShortCmdName()).isEqualTo("pst");
        assertThat(purgeStatesCommand.getDescription()).isNotEmpty();
        assertThat(purgeStatesCommand.getFimReposConstraint())
                .isEqualTo(org.fim.model.Command.FimReposConstraint.MUST_EXIST);
    }

    private void commitAndAssertModificationCountIsEqualTo(Context context, int expectedModificationCount) throws Exception {
        CompareResult compareResult = (CompareResult) commitCommand.execute(context);
        assertThat(compareResult.getModificationCounts().getAdded()).isEqualTo(expectedModificationCount);
    }

    private void assertLogSizeIsEqualTo(Context context, int expectedLogSize) throws Exception {
        LogResult logResult = (LogResult) logCommand.execute(context);
        assertThat(logResult.getLogEntries().size()).isEqualTo(expectedLogSize);
    }
}
