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

import org.apache.commons.io.FileUtils;
import org.fim.command.exception.BadFimUsageException;
import org.fim.model.Context;
import org.fim.model.State;
import org.fim.tooling.RepositoryTool;
import org.fim.tooling.TestConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.StandardOpenOption.APPEND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.Command.FimReposConstraint.DONT_CARE;
import static org.fim.model.HashMode.hashSmallBlock;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RemoveDuplicatesCommandTest {
    private InitCommand initCommand;
    private RemoveDuplicatesCommand removeDuplicatesCommand;

    private RepositoryTool tool;
    private Context context;
    private Path rootDir;
    private Path rootDirCopy;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        tool = new RepositoryTool(testInfo);
        rootDir = tool.getRootDir();
        context = tool.getContext();

        String testClassName = testInfo.getTestClass().get().getSimpleName();
        String testName = testInfo.getTestMethod().get().getName();
        String dirName = TestConstants.BUILD_TEST_OUTPUTS + "/" + testClassName + "-" + testName + "-copy";
        rootDirCopy = Paths.get(dirName);
        FileUtils.deleteDirectory(rootDirCopy.toFile());
        Files.createDirectories(rootDirCopy);

        initCommand = new InitCommand();
        removeDuplicatesCommand = new RemoveDuplicatesCommand();
        assertThat(removeDuplicatesCommand.getFimReposConstraint()).isEqualTo(DONT_CARE);
    }

    @Test
    public void canRemoveDuplicates() throws Exception {
        tool.createASetOfFiles(5);
        // Create an empty file that wont be seen as duplicate
        Files.createFile(rootDir.resolve("empty_file_01"));

        State state = (State) initCommand.execute(context);
        assertThat(state.getModificationCounts().getAdded()).isEqualTo(6);

        // Setup the context to use a master directory
        context.setCurrentDirectory(rootDirCopy);
        context.setMasterFimRepositoryDir(rootDir.toString());
        long totalFilesRemoved = (long) removeDuplicatesCommand.execute(context);
        assertThat(totalFilesRemoved).isEqualTo(0);

        Files.copy(rootDir.resolve("file01"), rootDirCopy.resolve("dup_file01"));
        Files.copy(rootDir.resolve("file02"), rootDirCopy.resolve("dup_file02"));
        Files.copy(rootDir.resolve("file03"), rootDirCopy.resolve("dup_file03"));
        Files.copy(rootDir.resolve("file04"), rootDirCopy.resolve("dup_file04"));
        Files.copy(rootDir.resolve("file05"), rootDirCopy.resolve("dup_file05"));
        Files.copy(rootDir.resolve("empty_file_01"), rootDirCopy.resolve("dup_empty_file_01"));

        // Modify file03
        Files.write(rootDirCopy.resolve("dup_file03"), "appended content".getBytes(), APPEND);

        totalFilesRemoved = (long) removeDuplicatesCommand.execute(context);
        // Only 4 files are duplicated
        assertThat(totalFilesRemoved).isEqualTo(4);
    }

    @Test
    public void masterDirectoryMustExist() throws Exception {
        context.setMasterFimRepositoryDir("dummy");
        assertThrows(BadFimUsageException.class, () -> {
            removeDuplicatesCommand.execute(context);
        });
    }

    @Test
    public void weMustRunAFullHash() throws Exception {
        context.setHashMode(hashSmallBlock);
        context.setMasterFimRepositoryDir("dummy");
        assertThrows(BadFimUsageException.class, () -> {
            removeDuplicatesCommand.execute(context);
        });
    }

    @Test
    public void cannotRemoveDuplicatesIntoTheMasterDirectory() throws Exception {
        context.setCurrentDirectory(rootDir);
        context.setMasterFimRepositoryDir(rootDir.toString());
        assertThrows(BadFimUsageException.class, () -> {
            removeDuplicatesCommand.execute(context);
        });
    }

    @Test
    public void cannotRemoveDuplicatesIntoASubDirOfTheMasterDirectory() throws Exception {
        Path subDir = rootDir.resolve("subDir");
        Files.createDirectories(subDir);
        context.setCurrentDirectory(subDir);
        context.setMasterFimRepositoryDir(rootDir.toString());
        assertThrows(BadFimUsageException.class, () -> {
            removeDuplicatesCommand.execute(context);
        });
    }

    @Test
    public void masterDirectoryMustBeAFimRepository() throws Exception {
        context.setCurrentDirectory(rootDirCopy);
        context.setMasterFimRepositoryDir(rootDir.toString());
        assertThrows(BadFimUsageException.class, () -> {
            removeDuplicatesCommand.execute(context);
        });
    }
}
