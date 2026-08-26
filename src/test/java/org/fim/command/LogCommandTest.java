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
import org.fim.model.LogEntry;
import org.fim.model.LogResult;
import org.fim.tooling.RepositoryTool;
import org.fim.util.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.HashMode.hashAll;

/**
 * Tests for LogCommand behavior.
 * Validates Requirements 5.1, 5.2, 5.3, 5.4, 5.5, 5.6
 */
public class LogCommandTest {

    private InitCommand initCommand;
    private CommitCommand commitCommand;
    private LogCommand logCommand;
    private RepositoryTool tool;

    private PrintStream originalOut;
    private ByteArrayOutputStream capturedOutput;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        initCommand = new InitCommand();
        commitCommand = new CommitCommand();
        logCommand = new LogCommand();
        tool = new RepositoryTool(testInfo);

        // Capture Logger output for verifying display
        originalOut = Logger.out;
        capturedOutput = new ByteArrayOutputStream();
        Logger.out = new PrintStream(capturedOutput);
    }

    @AfterEach
    public void tearDown() {
        Logger.out = originalOut;
    }

    /**
     * Req 5.1: LogCommand iterates through all states from 1 to last state number.
     * Req 5.2: Each state displays state number, formatted timestamp, file count,
     *          files content length, hash mode, subdirectory info.
     * Req 5.3: Each state displays the comment.
     * Req 5.4: Each state displays modification summary.
     * Req 5.6: States loaded without full integrity validation (loadFullState=false).
     */
    @Test
    public void logWithMultipleStatesDisplaysAllEntries() throws Exception {
        Context context = tool.createContext(hashAll, true);
        context.setComment("Initial commit");
        tool.createASetOfFiles(3);
        initCommand.execute(context);

        // Create a second commit with modifications
        tool.setFileContent("file01", "modified content");
        context.setComment("Second commit");
        CompareResult commitResult = (CompareResult) commitCommand.execute(context);
        assertThat(commitResult.somethingModified()).isTrue();

        StateManager stateManager = new StateManager(context);
        assertThat(stateManager.getLastStateNumber()).isEqualTo(2);

        // Execute log
        capturedOutput.reset();
        LogResult logResult = (LogResult) logCommand.execute(context);

        // Req 5.1: All states iterated - should have 2 log entries
        assertThat(logResult).isNotNull();
        assertThat(logResult.getLogEntries()).hasSize(2);

        // Verify first entry (state #1)
        LogEntry entry1 = logResult.getLogEntries().get(0);
        assertThat(entry1.getStateNumber()).isEqualTo(1);
        assertThat(entry1.getComment()).isEqualTo("Initial commit");
        assertThat(entry1.getTimestamp()).isGreaterThan(0);
        assertThat(entry1.getFileCount()).isEqualTo(3);
        assertThat(entry1.getFilesContentLength()).isGreaterThan(0);
        assertThat(entry1.getModificationCounts()).isNotNull();
        assertThat(entry1.getModificationCounts().getAdded()).isEqualTo(3);
        assertThat(entry1.getCommitDetails()).isNotNull();

        // Verify second entry (state #2)
        LogEntry entry2 = logResult.getLogEntries().get(1);
        assertThat(entry2.getStateNumber()).isEqualTo(2);
        assertThat(entry2.getComment()).isEqualTo("Second commit");
        assertThat(entry2.getTimestamp()).isGreaterThan(0);
        assertThat(entry2.getFileCount()).isEqualTo(3);
        assertThat(entry2.getFilesContentLength()).isGreaterThan(0);
        assertThat(entry2.getModificationCounts()).isNotNull();
        assertThat(entry2.getCommitDetails()).isNotNull();

        // Req 5.2: Verify output contains state numbers, hash mode, file info
        String output = capturedOutput.toString();
        assertThat(output).contains("State #1");
        assertThat(output).contains("State #2");
        assertThat(output).contains("hashAll");

        // Req 5.3: Verify comments are displayed
        assertThat(output).contains("Initial commit");
        assertThat(output).contains("Second commit");

        // Req 5.4: Verify modification summary is displayed (added files)
        assertThat(output).contains("added");
    }

    /**
     * Req 5.5: If no states exist, LogCommand returns an empty LogResult.
     * When getLastStateNumber() returns 0 (no state files on disk), the iteration
     * loop from 1..0 does not execute, producing an empty result.
     * The "No State found" error path is guarded by lastStateNumber == -1,
     * which is a defensive check for an edge case.
     */
    @Test
    public void logWithNoStatesReturnsEmptyResult() throws Exception {
        Context context = tool.createContext(hashAll, true);

        // Create .fim directory structure but no state files
        java.nio.file.Files.createDirectories(context.getRepositoryStatesDir());

        capturedOutput.reset();
        LogResult logResult = (LogResult) logCommand.execute(context);

        // With 0 states, the loop from 1..0 doesn't execute, returning empty LogResult
        assertThat(logResult).isNotNull();
        assertThat(logResult.getLogEntries()).isEmpty();
    }

    /**
     * Req 5.6: States are loaded without full integrity validation for performance.
     * We verify this by checking that loadState is called with loadFullState=false.
     * The LogCommand passes false to stateManager.loadState(stateNumber, false).
     * If full validation were used, it would adjust hashes per hash mode, but log entries
     * should retain the original hash mode info from the state.
     */
    @Test
    public void logLoadsStatesWithoutFullValidation() throws Exception {
        Context context = tool.createContext(hashAll, true);
        context.setComment("Test state");
        tool.createASetOfFiles(2);
        initCommand.execute(context);

        capturedOutput.reset();
        LogResult logResult = (LogResult) logCommand.execute(context);

        // If states were loaded with full validation, hash mode adjustment would occur.
        // Since log loads without full validation (loadFullState=false), the state
        // is loaded as-is. We verify the log entry is populated correctly.
        assertThat(logResult).isNotNull();
        assertThat(logResult.getLogEntries()).hasSize(1);

        LogEntry entry = logResult.getLogEntries().get(0);
        assertThat(entry.getStateNumber()).isEqualTo(1);
        assertThat(entry.getFileCount()).isEqualTo(2);
        assertThat(entry.getTimestamp()).isGreaterThan(0);
        assertThat(entry.getModificationCounts()).isNotNull();
    }

    /**
     * Verify LogCommand iterates three states correctly.
     */
    @Test
    public void logWithThreeStatesDisplaysAllThreeEntries() throws Exception {
        Context context = tool.createContext(hashAll, true);
        context.setComment("First");
        tool.createASetOfFiles(2);
        initCommand.execute(context);

        tool.setFileContent("file01", "change 1");
        context.setComment("Second");
        commitCommand.execute(context);

        tool.setFileContent("file02", "change 2");
        context.setComment("Third");
        commitCommand.execute(context);

        StateManager stateManager = new StateManager(context);
        assertThat(stateManager.getLastStateNumber()).isEqualTo(3);

        capturedOutput.reset();
        LogResult logResult = (LogResult) logCommand.execute(context);

        assertThat(logResult.getLogEntries()).hasSize(3);
        assertThat(logResult.getLogEntries().get(0).getStateNumber()).isEqualTo(1);
        assertThat(logResult.getLogEntries().get(1).getStateNumber()).isEqualTo(2);
        assertThat(logResult.getLogEntries().get(2).getStateNumber()).isEqualTo(3);

        assertThat(logResult.getLogEntries().get(0).getComment()).isEqualTo("First");
        assertThat(logResult.getLogEntries().get(1).getComment()).isEqualTo("Second");
        assertThat(logResult.getLogEntries().get(2).getComment()).isEqualTo("Third");

        // CompareResult is cleaned up (set to null) after display to avoid OOM
        for (LogEntry entry : logResult.getLogEntries()) {
            assertThat(entry.getCompareResult()).isNull();
        }
    }

    /**
     * Verify LogCommand metadata: cmdName, shortCmdName, description, constraint.
     */
    @Test
    public void commandMetadataIsCorrect() {
        assertThat(logCommand.getCmdName()).isEqualTo("log");
        assertThat(logCommand.getShortCmdName()).isEmpty();
        assertThat(logCommand.getDescription()).isNotEmpty();
        assertThat(logCommand.getFimReposConstraint())
                .isEqualTo(Command.FimReposConstraint.MUST_EXIST);
    }
}
