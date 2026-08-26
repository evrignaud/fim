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

import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.model.State;
import org.fim.tooling.RepositoryTool;
import org.fim.util.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.HashMode.dontHash;
import static org.fim.model.HashMode.hashAll;
import static org.fim.model.HashMode.hashMediumBlock;
import static org.fim.model.HashMode.hashSmallBlock;

/**
 * Tests for StatusCommand execution flow.
 * Validates Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8
 */
public class StatusCommandTest {

    private InitCommand initCommand;
    private StatusCommand statusCommand;
    private RepositoryTool tool;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        initCommand = new InitCommand();
        statusCommand = new StatusCommand();
        tool = new RepositoryTool(testInfo);
    }

    /**
     * Req 1.1: StatusCommand generates a new State from the current file tree.
     * Req 1.2: StatusCommand loads the last committed State and passes both to StateComparator.
     * Verifies that status produces a valid CompareResult after init + status cycle.
     */
    @Test
    public void statusGeneratesCurrentStateAndLoadsLastState(TestInfo testInfo) throws Exception {
        Context context = tool.createContext(hashAll, true);

        tool.createASetOfFiles(3);
        State state = (State) initCommand.execute(context);
        assertThat(state.getModificationCounts().getAdded()).isEqualTo(3);

        // Running status should compare current state with last committed state
        CompareResult result = (CompareResult) statusCommand.execute(context);
        assertThat(result).isNotNull();
        // No modifications since init
        assertThat(result.somethingModified()).isFalse();
    }

    /**
     * Req 1.3: When CompareResult contains no modifications, display "Nothing modified".
     */
    @Test
    public void nothingModifiedMessageDisplayed(TestInfo testInfo) throws Exception {
        Context context = tool.createContext(hashAll, true);

        tool.createASetOfFiles(2);
        initCommand.execute(context);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream originalOut = Logger.out;
        Logger.out = new PrintStream(baos);
        try {
            statusCommand.execute(context);
        } finally {
            Logger.out = originalOut;
        }

        String output = baos.toString();
        assertThat(output).contains("Nothing modified");
    }

    /**
     * Req 1.4: The `-f` option uses hashMediumBlock as the Hash_Mode.
     * We verify that status with hashMediumBlock produces a valid result.
     */
    @Test
    public void statusWithFastHashMode(TestInfo testInfo) throws Exception {
        Context context = tool.createContext(hashMediumBlock, true);

        tool.createASetOfFiles(2);
        initCommand.execute(context);

        CompareResult result = (CompareResult) statusCommand.execute(context);
        assertThat(result).isNotNull();
        assertThat(result.somethingModified()).isFalse();
    }

    /**
     * Req 1.5: The `-s` option uses hashSmallBlock as the Hash_Mode.
     * We verify that status with hashSmallBlock produces a valid result.
     */
    @Test
    public void statusWithSuperFastHashMode(TestInfo testInfo) throws Exception {
        Context context = tool.createContext(hashSmallBlock, true);

        tool.createASetOfFiles(2);
        initCommand.execute(context);

        CompareResult result = (CompareResult) statusCommand.execute(context);
        assertThat(result).isNotNull();
        assertThat(result.somethingModified()).isFalse();
    }

    /**
     * Req 1.6: The `-n` option uses dontHash as the Hash_Mode.
     * We verify that status with dontHash produces a valid result.
     */
    @Test
    public void statusWithDontHashMode(TestInfo testInfo) throws Exception {
        Context context = tool.createContext(dontHash, true);

        tool.createASetOfFiles(2);
        initCommand.execute(context);

        CompareResult result = (CompareResult) statusCommand.execute(context);
        assertThat(result).isNotNull();
        assertThat(result.somethingModified()).isFalse();
    }

    /**
     * Req 1.7: When invoked from a subdirectory, StatusCommand filters the last committed State
     * to include only FileState entries within that subdirectory.
     */
    @Test
    public void subdirectoryFiltering(TestInfo testInfo) throws Exception {
        Context context = tool.createContext(hashAll, true);

        // Create files in root
        tool.createASetOfFiles(2);

        // Create a subdirectory with files
        Path subDir = tool.getRootDir().resolve("subdir");
        Files.createDirectories(subDir);
        tool.createFile(subDir.resolve("subfile01"));
        tool.createFile(subDir.resolve("subfile02"));

        initCommand.execute(context);

        // Modify a file in root only
        tool.setFileContent("file01", "modified content");

        // Run status from subdirectory - should only see subdir files
        Context subDirContext = tool.createInvokedFromSubDirContext(hashAll, "subdir", true);
        CompareResult result = (CompareResult) statusCommand.execute(subDirContext);
        assertThat(result).isNotNull();
        // No modifications in subdir
        assertThat(result.somethingModified()).isFalse();
    }

    /**
     * Req 1.8: When Hash_Mode differs from Global_Hash_Mode and is compatible,
     * StatusCommand logs an informational message and proceeds.
     */
    @Test
    public void compatibleHashModeInfoMessage(TestInfo testInfo) throws Exception {
        // Init with hashMediumBlock to set global hash mode
        Context initContext = tool.createContext(hashMediumBlock, true);
        tool.createASetOfFiles(2);
        initCommand.execute(initContext);

        // Run status with hashSmallBlock (compatible with hashMediumBlock)
        Context statusContext = tool.createContext(hashSmallBlock, true);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream originalOut = Logger.out;
        Logger.out = new PrintStream(baos);
        try {
            CompareResult result = (CompareResult) statusCommand.execute(statusContext);
            assertThat(result).isNotNull();
        } finally {
            Logger.out = originalOut;
        }

        String output = baos.toString();
        assertThat(output).contains("compatible");
    }

    @Test
    public void getCmdNameReturnsStatus() {
        assertThat(statusCommand.getCmdName()).isEqualTo("status");
    }

    @Test
    public void getShortCmdNameReturnsSt() {
        assertThat(statusCommand.getShortCmdName()).isEqualTo("st");
    }
}
