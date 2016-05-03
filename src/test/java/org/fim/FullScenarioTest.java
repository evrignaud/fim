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
package org.fim;

import org.apache.commons.io.FileUtils;
import org.fim.command.CommitCommand;
import org.fim.command.DiffCommand;
import org.fim.command.DisplayIgnoredFilesCommand;
import org.fim.command.FindDuplicatesCommand;
import org.fim.command.InitCommand;
import org.fim.command.LogCommand;
import org.fim.command.RollbackCommand;
import org.fim.command.exception.BadFimUsageException;
import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.model.DuplicateResult;
import org.fim.model.HashMode;
import org.fim.model.LogResult;
import org.fim.model.ModificationCounts;
import org.fim.model.State;
import org.fim.tooling.RepositoryTool;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.HashMode.dontHash;
import static org.fim.model.HashMode.hashAll;
import static org.fim.model.HashMode.hashMediumBlock;
import static org.fim.model.HashMode.hashSmallBlock;

@RunWith(Parameterized.class)
public class FullScenarioTest {
    private static Path rootDir = Paths.get("target/" + FullScenarioTest.class.getSimpleName());

    private HashMode hashMode;
    private Path dir01;

    private InitCommand initCommand;
    private DiffCommand diffCommand;
    private CommitCommand commitCommand;
    private FindDuplicatesCommand findDuplicatesCommand;
    private LogCommand logCommand;
    private DisplayIgnoredFilesCommand displayIgnoredFilesCommand;
    private RollbackCommand rollbackCommand;

    private RepositoryTool tool;

    public FullScenarioTest(final HashMode hashMode) {
        this.hashMode = hashMode;
    }

    @Parameterized.Parameters(name = "Hash mode: {0}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(new Object[][]{
            {dontHash},
            {hashSmallBlock},
            {hashMediumBlock},
            {hashAll}
        });
    }

    @Before
    public void setup() throws IOException {
        FileUtils.deleteDirectory(rootDir.toFile());
        Files.createDirectories(rootDir);

        dir01 = rootDir.resolve("dir01");

        initCommand = new InitCommand();
        diffCommand = new DiffCommand();
        commitCommand = new CommitCommand();
        findDuplicatesCommand = new FindDuplicatesCommand();
        logCommand = new LogCommand();
        displayIgnoredFilesCommand = new DisplayIgnoredFilesCommand();
        rollbackCommand = new RollbackCommand();

        tool = new RepositoryTool(rootDir);
    }

    @Test
    public void fullScenario() throws Exception {
        Context context = tool.createContext(hashMode, hashMode == hashAll);

        tool.createASetOfFiles(10);

        State state = (State) initCommand.execute(context);

        assertThat(state.getModificationCounts().getAdded()).isEqualTo(10);
        assertThat(state.getFileCount()).isEqualTo(10);
        Path dotFim = rootDir.resolve(".fim");
        assertThat(Files.exists(dotFim)).isTrue();
        assertThat(Files.exists(dotFim.resolve("settings.json"))).isTrue();
        assertThat(Files.exists(dotFim.resolve("states/state_1.json.gz"))).isTrue();

        doSomeModifications();
        ModificationCounts modificationCounts;

        CompareResult compareResult = (CompareResult) diffCommand.execute(context);
        modificationCounts = compareResult.getModificationCounts();
        if (hashMode == dontHash) {
            assertThat(compareResult.modifiedCount()).isEqualTo(11);
            assertThat(modificationCounts.getRenamed()).isEqualTo(0);
            assertThat(modificationCounts.getDeleted()).isEqualTo(2);
        } else {
            assertThat(compareResult.modifiedCount()).isEqualTo(10);
            assertThat(modificationCounts.getRenamed()).isEqualTo(1);
            assertThat(modificationCounts.getDeleted()).isEqualTo(1);
        }

        assertDuplicatedFilesCountEqualsTo(context, 2);

        addIgnoredFiles(context);

        runCommandFromDirectory(context, dir01);

        compareResult = (CompareResult) commitCommand.execute(context);
        assertThat(compareResult.modifiedCount()).isEqualTo(13);
        modificationCounts = compareResult.getModificationCounts();
        assertThat(modificationCounts.getRenamed()).isEqualTo(0);
        assertThat(modificationCounts.getDeleted()).isEqualTo(2);

        // Committing once again does nothing
        commit_AndAssertFilesModifiedCountEqualsTo(context, 0);

        assertFilesModifiedCountEqualsTo(context, 0);

        LogResult logResult = (LogResult) logCommand.execute(context);
        assertThat(logResult.getLogEntries().size()).isEqualTo(3);

        Set<String> ignoredFiles = (Set<String>) displayIgnoredFilesCommand.execute(context);
        assertThat(ignoredFiles.size()).isEqualTo(6);

        assertWeCanRollbackLastCommit(context, 2, 3);

        assertFilesModifiedCountEqualsTo(context, 13);

        // We can rollback again
        assertWeCanRollbackLastCommit(context, 1, 0);

        // Nothing more to rollback
        assertWeCanRollbackLastCommit(context, 1, 0);

        if (hashMode == hashAll || hashMode == hashMediumBlock) {
            Context superFastModeContext = context.clone();
            superFastModeContext.setHashMode(hashSmallBlock);
            superFastModeContext.setComment("Using hash mode " + hashSmallBlock);

            // Commit using super-fast mode (hashSmallBlock)
            compareResult = (CompareResult) commitCommand.execute(superFastModeContext);
            assertThat(compareResult.modifiedCount()).isEqualTo(15);
            modificationCounts = compareResult.getModificationCounts();
            assertThat(modificationCounts.getAdded()).isEqualTo(6);
            assertThat(modificationCounts.getCopied()).isEqualTo(1);
            assertThat(modificationCounts.getDuplicated()).isEqualTo(3);
            assertThat(modificationCounts.getDateModified()).isEqualTo(1);
            assertThat(modificationCounts.getContentModified()).isEqualTo(2);
            assertThat(modificationCounts.getRenamed()).isEqualTo(1);
            assertThat(modificationCounts.getDeleted()).isEqualTo(1);

            // Check that using normal hashMode there is no modification detected
            commit_AndAssertFilesModifiedCountEqualsTo(context, 0);
        }
    }

    private void doSomeModifications() throws IOException {
        Files.createDirectories(dir01);

        Files.move(rootDir.resolve("file01"), dir01.resolve("file01"));

        tool.touchLastModified("file02");

        Files.copy(rootDir.resolve("file03"), rootDir.resolve("file03.dup1"));
        Files.copy(rootDir.resolve("file03"), rootDir.resolve("file03.dup2"));

        tool.setFileContent("file04", "foo");

        Files.copy(rootDir.resolve("file05"), rootDir.resolve("file11"));
        tool.setFileContent("file05", "bar");

        Files.delete(rootDir.resolve("file06"));

        Files.copy(rootDir.resolve("file07"), rootDir.resolve("file07.dup1"));

        tool.setFileContent("file12", "New file 12");
    }

    private void addIgnoredFiles(Context context) throws Exception {
        tool.createFile("ignored_type1");
        tool.createFile("ignored_type2");

        tool.createFile(dir01.resolve("ignored_type1"));
        tool.createFile(dir01.resolve("ignored_type2"));

        tool.createFile("media.mp3");
        tool.createFile("media.mp4");

        tool.createFile(dir01.resolve("media.mp3"));
        tool.createFile(dir01.resolve("media.mp4"));

        assertFilesModifiedCountEqualsTo(context, hashMode == dontHash ? 19 : 18);

        tool.createFimIgnore(rootDir, "**/*.mp3\n" + "ignored_type1");

        assertFilesModifiedCountEqualsTo(context, hashMode == dontHash ? 17 : 16);
    }

    private void runCommandFromDirectory(Context context, Path subDirectory) throws Exception {
        Context subDirectoryContext = context.clone();
        subDirectoryContext.setCurrentDirectory(subDirectory);
        subDirectoryContext.setInvokedFromSubDirectory(true);

        assertFilesModifiedCountEqualsTo(subDirectoryContext, 4);

        tool.createFimIgnore(subDirectory, "*.mp4\n" + "ignored_type2");

        assertFilesModifiedCountEqualsTo(subDirectoryContext, 3);

        assertDuplicatedFilesCountEqualsTo(subDirectoryContext, 0);

        commit_AndAssertFilesModifiedCountEqualsTo(subDirectoryContext, 3);

        assertFilesModifiedCountEqualsTo(subDirectoryContext, 0);
    }

    private void assertFilesModifiedCountEqualsTo(Context context, int expectedModifiedFileCount) throws Exception {
        CompareResult compareResult = (CompareResult) diffCommand.execute(context);
        assertThat(compareResult.modifiedCount()).isEqualTo(expectedModifiedFileCount);
    }

    private void commit_AndAssertFilesModifiedCountEqualsTo(Context context, int expectedModifiedFileCount) throws Exception {
        CompareResult compareResult = (CompareResult) commitCommand.execute(context);
        assertThat(compareResult.modifiedCount()).isEqualTo(expectedModifiedFileCount);
    }

    private void assertDuplicatedFilesCountEqualsTo(Context context, int expectedDuplicatedSetCount) throws Exception {
        try {
            DuplicateResult duplicateResult = (DuplicateResult) findDuplicatesCommand.execute(context);
            assertThat(duplicateResult.getDuplicateSets().size()).isEqualTo(expectedDuplicatedSetCount);
        } catch (BadFimUsageException ex) {
            if (context.getHashMode() != dontHash) {
                throw ex;
            }
        }
    }

    private void assertWeCanRollbackLastCommit(Context context, int expectedLogEntriesCount, int expectedIgnoredFilesCount) throws Exception {
        rollbackCommand.execute(context);

        LogResult logResult = (LogResult) logCommand.execute(context);
        assertThat(logResult.getLogEntries().size()).isEqualTo(expectedLogEntriesCount);

        Set<String> ignoredFiles = (Set<String>) displayIgnoredFilesCommand.execute(context);
        assertThat(ignoredFiles.size()).isEqualTo(expectedIgnoredFilesCount);
    }
}
