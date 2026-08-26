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

import org.fim.model.Context;
import org.fim.tooling.RepositoryTool;
import org.fim.util.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DisplayIgnoredFilesCommand verifying Requirements 5.1–5.6.
 */
public class DisplayIgnoredFilesCommandTest {

    private InitCommand initCommand;
    private DisplayIgnoredFilesCommand displayIgnoredFilesCommand;

    private RepositoryTool tool;
    private Context context;

    private ByteArrayOutputStream outputStream;
    private PrintStream originalOut;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        tool = new RepositoryTool(testInfo);
        context = tool.getContext();

        initCommand = new InitCommand();
        displayIgnoredFilesCommand = new DisplayIgnoredFilesCommand();

        // Capture Logger output
        originalOut = Logger.out;
        outputStream = new ByteArrayOutputStream();
        Logger.out = new PrintStream(outputStream);
    }

    @AfterEach
    public void tearDown() {
        Logger.out = originalOut;
    }

    /**
     * Req 5.6: If no State exists in the repository, the command returns null.
     * With no .fim directory or state files, getLastStateNumber() returns 0
     * and the state_0 file does not exist, so the command returns null.
     */
    @Test
    public void noStateFoundReturnsNull() throws Exception {
        // Context points to a fresh directory with no .fim/states — no state exists
        Object result = displayIgnoredFilesCommand.execute(context);

        assertThat(result).isNull();
    }

    /**
     * Req 5.3: When ignoredFiles is empty, display "No files or directories ignored into this State".
     */
    @Test
    @SuppressWarnings("unchecked")
    public void emptyIgnoredFilesDisplaysNoIgnoredMessage() throws Exception {
        tool.createASetOfFiles(2);
        initCommand.execute(context);

        // Reset output capture after init
        outputStream.reset();

        Object result = displayIgnoredFilesCommand.execute(context);

        String output = outputStream.toString();
        assertThat(output).contains("No files or directories ignored into this State");
        // Result should be the ignored files set (empty)
        assertThat(result).isNotNull();
        assertThat((Set<String>) result).isEmpty();
    }

    /**
     * Req 5.4: Display state number, formatted timestamp, file count, and content length.
     */
    @Test
    public void stateMetadataIsDisplayed() throws Exception {
        tool.createASetOfFiles(3);
        initCommand.execute(context);

        outputStream.reset();

        displayIgnoredFilesCommand.execute(context);

        String output = outputStream.toString();
        // Should contain "State #1" (first state)
        assertThat(output).contains("State #1");
        // Should contain file count
        assertThat(output).contains("3 files");
    }

    /**
     * Req 5.5: When the last State has a non-empty comment, display the comment.
     */
    @Test
    public void nonEmptyCommentIsDisplayed() throws Exception {
        tool.createASetOfFiles(2);
        // The context comment is set by RepositoryTool.createContext to "Using hash mode hashAll"
        initCommand.execute(context);

        outputStream.reset();

        displayIgnoredFilesCommand.execute(context);

        String output = outputStream.toString();
        assertThat(output).contains("Comment:");
        assertThat(output).contains("Using hash mode");
    }

    /**
     * Req 5.1, 5.2: When ignoredFiles has entries, display each path one per line.
     * Uses a .fimignore file to generate ignored files in the state.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void ignoredFilesPresentDisplaysEachPath() throws Exception {
        // Create files and a .fimignore that ignores some of them
        String ignoredFileName = "file01";
        tool.createASetOfFiles(3);
        tool.createFimIgnore(tool.getRootDir(), ignoredFileName);

        initCommand.execute(context);

        outputStream.reset();

        Object result = displayIgnoredFilesCommand.execute(context);

        String output = outputStream.toString();
        // Should NOT show the "no ignored" message
        assertThat(output).doesNotContain("No files or directories ignored into this State");
        // The ignored file should appear in output
        assertThat(output).contains(ignoredFileName);
        // Result should contain the ignored file
        assertThat(result).isNotNull();
        assertThat((Set<String>) result).contains(ignoredFileName);
    }
}
