/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2017  Etienne Vrignaud
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

import org.fim.command.exception.BadFimUsageException;
import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.model.FileState;
import org.fim.model.State;
import org.fim.tooling.RepositoryTool;
import org.fim.util.TimeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;

import static java.nio.file.StandardOpenOption.CREATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.HashMode.dontHash;
import static org.fim.model.HashMode.hashAll;
import static org.fim.model.HashMode.hashMediumBlock;
import static org.fim.model.HashMode.hashSmallBlock;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DetectCorruptionCommandTest {
    private InitCommand initCommand;
    private StatusCommand statusCommand;
    private DetectCorruptionCommand detectCorruptionCommand;

    private RepositoryTool tool;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        initCommand = new InitCommand();
        statusCommand = new StatusCommand();
        detectCorruptionCommand = new DetectCorruptionCommand();

        tool = new RepositoryTool(testInfo);
    }

    @Test
    public void dontHash_NotAllowed() throws Exception {
        Context context = tool.createContext(dontHash, false);
        assertThrows(BadFimUsageException.class, () -> {
            detectCorruptionCommand.execute(context);
        });
    }

    @Test
    public void hashSmallBlock_NotAllowed() throws Exception {
        Context context = tool.createContext(hashSmallBlock, false);
        assertThrows(BadFimUsageException.class, () -> {
            detectCorruptionCommand.execute(context);
        });
    }

    @Test
    public void hashMediumBlock_NotAllowed() throws Exception {
        Context context = tool.createContext(hashMediumBlock, false);
        assertThrows(BadFimUsageException.class, () -> {
            detectCorruptionCommand.execute(context);
        });
    }

    @Test
    public void canDetectHardwareCorruption() throws Exception {
        Context context = tool.createContext(hashAll, true);

        tool.createASetOfFiles(5);

        State state = (State) initCommand.execute(context);
        assertThat(state.getModificationCounts().getAdded()).isEqualTo(5);

        CompareResult compareResult = (CompareResult) detectCorruptionCommand.execute(context);
        assertThat(compareResult.getCorrupted().size()).isEqualTo(0);

        doSomeModifications();

        compareResult = (CompareResult) statusCommand.execute(context);
        assertThat(compareResult.modifiedCount()).isEqualTo(3);

        compareResult = (CompareResult) detectCorruptionCommand.execute(context);
        assertThat(compareResult.getCorrupted().size()).isEqualTo(1);
        FileState fileState = compareResult.getCorrupted().getFirst().getFileState();
        assertThat(fileState.getFileName()).isEqualTo("file03");
    }

    private void doSomeModifications() throws IOException {
        TimeUtil.sleepSafely(1_000); // Ensure to increase lastModified at least of 1 second

        tool.touchLastModified("file01");

        tool.setFileContent("file02", "file02 new content");

        simulateHardwareCorruption("file03");

        // Do nothing on file04 and file05
    }

    private void simulateHardwareCorruption(String fileName) throws IOException {
        Path file = tool.getRootDir().resolve(fileName);
        // Keep original timestamps
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);

        // A zero byte appears in the middle of the file
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length / 2] = 0;

        Files.delete(file);
        Files.write(file, bytes, CREATE);

        // Restore the original timestamps
        Files.getFileAttributeView(file, BasicFileAttributeView.class)
                .setTimes(attributes.lastModifiedTime(), attributes.lastAccessTime(), attributes.creationTime());
    }
}
