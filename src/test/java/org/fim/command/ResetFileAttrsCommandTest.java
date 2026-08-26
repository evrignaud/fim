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
import org.fim.model.HashMode;
import org.fim.model.State;
import org.fim.tooling.RepositoryTool;
import org.fim.util.Logger;
import org.fim.util.TimeUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.assertj.core.api.Assertions.assertThat;

public class ResetFileAttrsCommandTest {
    private InitCommand initCommand;
    private StatusCommand statusCommand;
    private ResetFileAttributesCommand resetFileAttributesCommand;

    private RepositoryTool tool;
    private Context context;

    private ByteArrayOutputStream outputStream;
    private PrintStream originalOut;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        tool = new RepositoryTool(testInfo);
        context = tool.getContext();

        initCommand = new InitCommand();
        statusCommand = new StatusCommand();
        resetFileAttributesCommand = new ResetFileAttributesCommand();

        // Capture Logger output for verifying messages
        originalOut = Logger.out;
        outputStream = new ByteArrayOutputStream();
        Logger.out = new PrintStream(outputStream);
    }

    @AfterEach
    public void tearDown() {
        Logger.out = originalOut;
    }

    @Test
    public void canResetFileAttributes() throws Exception {
        tool.createASetOfFiles(5);

        State state = (State) initCommand.execute(context);
        assertThat(state.getModificationCounts().getAdded()).isEqualTo(5);

        int fileResetCount = (int) resetFileAttributesCommand.execute(context);
        assertThat(fileResetCount).isEqualTo(0);

        doSomeModifications();

        CompareResult compareResult = (CompareResult) statusCommand.execute(context);
        assertThat(compareResult.modifiedCount()).isEqualTo(3);
        assertThat(compareResult.getDateModified().size()).isEqualTo(IS_OS_WINDOWS ? 3 : 2);
        assertThat(compareResult.getAttributesModified().size()).isEqualTo(IS_OS_WINDOWS ? 0 : 1);

        fileResetCount = (int) resetFileAttributesCommand.execute(context);
        assertThat(fileResetCount).isEqualTo(3);

        compareResult = (CompareResult) statusCommand.execute(context);
        assertThat(compareResult.modifiedCount()).isEqualTo(0);
    }

    /**
     * Req 6.10: When no file attributes require resetting, display
     * "No file attributes have been reset".
     */
    @Test
    public void noAttributesToResetDisplaysMessage() throws Exception {
        tool.createASetOfFiles(3);
        initCommand.execute(context);

        outputStream.reset();

        int fileResetCount = (int) resetFileAttributesCommand.execute(context);

        assertThat(fileResetCount).isEqualTo(0);
        String output = outputStream.toString();
        assertThat(output).contains("No file attributes have been reset");
    }

    /**
     * Req 6.11: When attribute resets complete, display a summary indicating
     * the total number of files whose attributes were reset.
     */
    @Test
    public void summaryDisplaysResetCount() throws Exception {
        tool.createASetOfFiles(3);
        initCommand.execute(context);

        doSomeModifications();

        outputStream.reset();

        int fileResetCount = (int) resetFileAttributesCommand.execute(context);

        assertThat(fileResetCount).isEqualTo(3);
        String output = outputStream.toString();
        assertThat(output).contains("The attributes of 3");
        assertThat(output).contains("have been reset");
    }

    /**
     * Req 6.8: When invoked from a subdirectory, filter the state to include
     * only files within the current subdirectory before performing resets.
     */
    @Test
    public void subdirectoryFilteringResetsOnlySubdirFiles() throws Exception {
        // Create files in root
        tool.createASetOfFiles(2);

        // Create a subdirectory with files
        Path subDir = tool.getRootDir().resolve("subdir");
        Files.createDirectories(subDir);
        tool.createFile(subDir.resolve("file03"));
        tool.createFile(subDir.resolve("file04"));

        initCommand.execute(context);

        // Modify files in both root and subdir
        TimeUtil.sleepSafely(1_000);
        tool.touchLastModified("file01");
        Files.setLastModifiedTime(subDir.resolve("file03"),
                java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 2_000));

        // Create a subdirectory context
        Context subDirContext = tool.createInvokedFromSubDirContext(HashMode.hashAll, "subdir", true);

        outputStream.reset();

        int fileResetCount = (int) resetFileAttributesCommand.execute(subDirContext);

        // Only the file in subdir should be reset, not the root file
        assertThat(fileResetCount).isEqualTo(1);
    }

    /**
     * Req 6.12: If a file listed in the last State no longer exists on disk,
     * skip that file without reporting an error.
     */
    @Test
    public void nonExistentFileIsSkipped() throws Exception {
        tool.createASetOfFiles(3);
        initCommand.execute(context);

        // Modify all files
        doSomeModifications();

        // Delete file02 so it no longer exists on disk
        Path file02 = tool.getRootDir().resolve("file02");
        Files.deleteIfExists(file02);

        outputStream.reset();

        int fileResetCount = (int) resetFileAttributesCommand.execute(context);

        // file02 should be skipped; only file01 and file03 should be reset
        assertThat(fileResetCount).isEqualTo(2);
        String output = outputStream.toString();
        // The output should not contain error messages about file02
        assertThat(output).doesNotContain("Error");
    }

    private void doSomeModifications() throws IOException {
        TimeUtil.sleepSafely(1_000); // Ensure to increase lastModified at least of 1 second

        tool.touchCreationTime("file01");
        tool.setPermissions("file01", "rwx------", "AHRS");

        tool.touchLastModified("file02");
        tool.setPermissions("file02", "r-x------", "H");

        tool.touchLastModified("file03");
        tool.setPermissions("file03", "r-xr-x---", "R");
    }
}
