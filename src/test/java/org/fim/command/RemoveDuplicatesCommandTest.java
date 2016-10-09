/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2016  Etienne Vrignaud
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
 * along with Fim.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.fim.command;

import org.apache.commons.io.FileUtils;
import org.fim.Fim;
import org.fim.command.exception.BadFimUsageException;
import org.fim.model.Context;
import org.fim.model.HashMode;
import org.fim.model.State;
import org.fim.tooling.RepositoryTool;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.createFile;
import static java.nio.file.Files.write;
import static java.nio.file.StandardOpenOption.APPEND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.Command.FimReposConstraint.DONT_CARE;
import static org.fim.model.HashMode.hashAll;
import static org.fim.model.HashMode.hashSmallBlock;

public class RemoveDuplicatesCommandTest {
    private static Path rootDir = Paths.get("target/" + RemoveDuplicatesCommandTest.class.getSimpleName());
    private static Path rootDirCopy = Paths.get("target/" + RemoveDuplicatesCommandTest.class.getSimpleName() + "-copy");

    private InitCommand initCommand;
    private StatusCommand statusCommand;
    private RemoveDuplicatesCommand removeDuplicatesCommand;

    private RepositoryTool tool;
    private Context context;

    @Before
    public void setup() throws IOException {
        FileUtils.deleteDirectory(rootDir.toFile());
        createDirectories(rootDir);

        FileUtils.deleteDirectory(rootDirCopy.toFile());
        createDirectories(rootDirCopy);

        initCommand = new InitCommand();
        statusCommand = new StatusCommand();
        removeDuplicatesCommand = new RemoveDuplicatesCommand(new Fim());
        assertThat(removeDuplicatesCommand.getFimReposConstraint()).isEqualTo(DONT_CARE);

        tool = new RepositoryTool(rootDir);
        context = tool.createContext(HashMode.hashAll, true);
    }

    @Test
    public void weCanRemoveDuplicates() throws Exception {
        Context context = tool.createContext(hashAll, false);

        tool.createASetOfFiles(5);
        // Create an empty file that wont be seen as duplicate
        createFile(rootDir.resolve("empty_file_01"));

        State state = (State) initCommand.execute(context);
        assertThat(state.getModificationCounts().getAdded()).isEqualTo(6);

        // Setup the context to use a master directory
        context.setCurrentDirectory(rootDirCopy);
        context.setMasterFimRepositoryDir(rootDir.toString());
        long totalFilesRemoved = (long) removeDuplicatesCommand.execute(context);
        assertThat(totalFilesRemoved).isEqualTo(0);

        copy(rootDir.resolve("file01"), rootDirCopy.resolve("dup_file01"));
        copy(rootDir.resolve("file02"), rootDirCopy.resolve("dup_file02"));
        copy(rootDir.resolve("file03"), rootDirCopy.resolve("dup_file03"));
        copy(rootDir.resolve("file04"), rootDirCopy.resolve("dup_file04"));
        copy(rootDir.resolve("file05"), rootDirCopy.resolve("dup_file05"));
        copy(rootDir.resolve("empty_file_01"), rootDirCopy.resolve("dup_empty_file_01"));

        // Modify file03
        write(rootDirCopy.resolve("dup_file03"), "appended content".getBytes(), APPEND);

        totalFilesRemoved = (long) removeDuplicatesCommand.execute(context);
        // Only 4 files are duplicated
        assertThat(totalFilesRemoved).isEqualTo(4);
    }

    @Test(expected = BadFimUsageException.class)
    public void masterDirectoryMustExist() throws Exception {
        context.setMasterFimRepositoryDir("dummy");
        removeDuplicatesCommand.execute(context);
    }

    @Test(expected = BadFimUsageException.class)
    public void weMustRunAFullHash() throws Exception {
        context.setHashMode(hashSmallBlock);
        context.setMasterFimRepositoryDir("dummy");
        removeDuplicatesCommand.execute(context);
    }

    @Test(expected = BadFimUsageException.class)
    public void CannotRemoveDuplicatesIntoTheMasterDirectory() throws Exception {
        context.setCurrentDirectory(rootDir);
        context.setMasterFimRepositoryDir(rootDir.toString());
        removeDuplicatesCommand.execute(context);
    }

    @Test(expected = BadFimUsageException.class)
    public void CannotRemoveDuplicatesIntoASubDirOfTheMasterDirectory() throws Exception {
        Path subDir = rootDir.resolve("subDir");
        createDirectories(subDir);
        context.setCurrentDirectory(subDir);
        context.setMasterFimRepositoryDir(rootDir.toString());
        removeDuplicatesCommand.execute(context);
    }

    @Test(expected = BadFimUsageException.class)
    public void masterDirectoryMustBeAFimRepository() throws Exception {
        context.setCurrentDirectory(rootDirCopy);
        context.setMasterFimRepositoryDir(rootDir.toString());
        removeDuplicatesCommand.execute(context);
    }
}
