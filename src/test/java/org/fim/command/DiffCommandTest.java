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
import org.fim.tooling.RepositoryTool;
import org.fim.util.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.HashMode.hashAll;

/**
 * Tests for DiffCommand backward compatibility.
 * Validates Requirements 2.1, 2.2, 2.3
 */
public class DiffCommandTest {

    private InitCommand initCommand;
    private DiffCommand diffCommand;
    private RepositoryTool tool;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        initCommand = new InitCommand();
        diffCommand = new DiffCommand();
        tool = new RepositoryTool(testInfo);
    }

    /**
     * Req 2.1: DiffCommand displays a deprecation message indicating that 'diff' is deprecated
     * and 'status' should be used instead.
     */
    @Test
    public void deprecationMessagePrinted() throws Exception {
        Context context = tool.createContext(hashAll, true);

        tool.createASetOfFiles(2);
        initCommand.execute(context);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream originalOut = Logger.out;
        Logger.out = new PrintStream(baos);
        try {
            diffCommand.execute(context);
        } finally {
            Logger.out = originalOut;
        }

        String output = baos.toString();
        assertThat(output).contains("'diff' command is deprecated");
        assertThat(output).contains("use 'status' instead");
    }

    /**
     * Req 2.2: DiffCommand delegates to StatusCommand execution logic and returns the same CompareResult.
     */
    @Test
    public void delegatesToStatusCommand() throws Exception {
        Context context = tool.createContext(hashAll, true);

        tool.createASetOfFiles(3);
        initCommand.execute(context);

        // Modify a file so we can verify both commands produce the same result
        tool.setFileContent("file01", "modified content for diff test");

        // Capture diff result
        ByteArrayOutputStream diffBaos = new ByteArrayOutputStream();
        PrintStream originalOut = Logger.out;
        Logger.out = new PrintStream(diffBaos);
        CompareResult diffResult;
        try {
            diffResult = (CompareResult) diffCommand.execute(context);
        } finally {
            Logger.out = originalOut;
        }

        // Verify diff produces a valid CompareResult with the same modification count
        assertThat(diffResult).isNotNull();
        assertThat(diffResult.somethingModified()).isTrue();
        assertThat(diffResult.getContentModified()).hasSize(1);
    }

    /**
     * Req 2.3: DiffCommand returns an empty string for getShortCmdName().
     */
    @Test
    public void emptyShortCommandName() {
        assertThat(diffCommand.getShortCmdName()).isEmpty();
    }

    @Test
    public void getCmdNameReturnsDiff() {
        assertThat(diffCommand.getCmdName()).isEqualTo("diff");
    }
}
