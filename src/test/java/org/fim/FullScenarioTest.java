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

package org.fim;

import org.fim.command.CommitCommand;
import org.fim.command.DisplayIgnoredFilesCommand;
import org.fim.command.FindDuplicatesCommand;
import org.fim.command.InitCommand;
import org.fim.command.LogCommand;
import org.fim.command.RemoveDuplicatesCommand;
import org.fim.command.RollbackCommand;
import org.fim.command.StatusCommand;
import org.fim.command.exception.BadFimUsageException;
import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.model.CorruptedStateException;
import org.fim.model.DuplicateResult;
import org.fim.model.HashMode;
import org.fim.model.LogResult;
import org.fim.model.Modification;
import org.fim.model.ModificationCounts;
import org.fim.model.State;
import org.fim.tooling.RepositoryTool;
import org.fim.util.TestAllHashModes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.internal.StateManager.STATE_EXTENSION;
import static org.fim.model.HashMode.dontHash;
import static org.fim.model.HashMode.hashAll;
import static org.fim.model.HashMode.hashMediumBlock;
import static org.fim.model.HashMode.hashSmallBlock;
import static org.fim.model.Modification.added;
import static org.fim.model.Modification.attributesModified;
import static org.fim.model.Modification.contentModified;
import static org.fim.model.Modification.copied;
import static org.fim.model.Modification.dateModified;
import static org.fim.model.Modification.deleted;
import static org.fim.model.Modification.duplicated;
import static org.fim.model.Modification.renamed;

public class FullScenarioTest {
    private Path dir01;

    private InitCommand initCommand;
    private StatusCommand statusCommand;
    private CommitCommand commitCommand;
    private FindDuplicatesCommand findDuplicatesCommand;
    private RemoveDuplicatesCommand removeDuplicatesCommand;
    private LogCommand logCommand;
    private DisplayIgnoredFilesCommand displayIgnoredFilesCommand;
    private RollbackCommand rollbackCommand;

    private RepositoryTool tool;
    private Path rootDir;

    private TestInfo testInfo;

    @BeforeEach
    void init(TestInfo testInfo) {
        this.testInfo = testInfo;
    }

    public void setUp(HashMode hashMode) throws IOException {
        tool = new RepositoryTool(testInfo, hashMode);
        rootDir = tool.getRootDir();

        dir01 = rootDir.resolve("dir01");

        initCommand = new InitCommand();
        statusCommand = new StatusCommand();
        commitCommand = new CommitCommand();
        findDuplicatesCommand = new FindDuplicatesCommand();
        removeDuplicatesCommand = new RemoveDuplicatesCommand();
        logCommand = new LogCommand();
        displayIgnoredFilesCommand = new DisplayIgnoredFilesCommand();
        rollbackCommand = new RollbackCommand();
    }

    @TestAllHashModes
    public void fullScenario(HashMode hashMode) throws Exception {
        setUp(hashMode);

        Context context = tool.getContext();

        tool.createASetOfFiles(10);
        Thread.sleep(2); // In order to detect modified dates

        State state = (State) initCommand.execute(context);

        assertThat(state.getModificationCounts().getAdded()).isEqualTo(10);
        assertThat(state.getFileCount()).isEqualTo(10);
        Path dotFim = rootDir.resolve(".fim");
        assertThat(Files.exists(dotFim)).isTrue();
        assertThat(Files.exists(dotFim.resolve("settings.json"))).isTrue();
        assertThat(Files.exists(dotFim.resolve("states/state_1.json.gz"))).isTrue();

        doSomeModifications();
        ModificationCounts modificationCounts;

        CompareResult compareResult = (CompareResult) statusCommand.execute(context);
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

        addIgnoredFiles(hashMode, context);

        runCommandFromDirectory(context, dir01);

        compareResult = (CompareResult) commitCommand.execute(context);
        assertThat(compareResult.modifiedCount()).isEqualTo(13);
        modificationCounts = compareResult.getModificationCounts();
        assertThat(modificationCounts.getRenamed()).isEqualTo(0);
        assertThat(modificationCounts.getDeleted()).isEqualTo(2);
        assertLastStateContainSameModifications(context, modificationCounts);

        // Committing once again does nothing
        commit_AndAssertFilesModifiedCountEqualsTo(context, 0);

        assertFilesModifiedCountEqualsTo(context, 0);

        LogResult logResult = (LogResult) logCommand.execute(context);
        assertThat(logResult.getLogEntries().size()).isEqualTo(3);

        Set<String> ignoredFiles = (Set<String>) displayIgnoredFilesCommand.execute(context);
        assertThat(ignoredFiles.size()).isEqualTo(6);

        assertCanRollbackLastCommit(context, 2, 3);

        assertFilesModifiedCountEqualsTo(context, 13);

        // We can rollback again
        assertCanRollbackLastCommit(context, 1, 0);

        // Nothing more to rollback
        assertCanRollbackLastCommit(context, 1, 0);

        if (hashMode == hashAll || hashMode == hashMediumBlock) {
            Context superFastModeContext = tool.createContext(hashSmallBlock, true);
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
            assertLastStateContainSameModifications(context, modificationCounts);

            // Check that the last commit command did not modify the hashMode
            assertThat(superFastModeContext.getHashMode()).isEqualTo(hashSmallBlock);

            assertThatUsingNormalHashModeNoModificationIsDetected(context);

            // Commit a new file from the dir01 sub directory
            tool.createFile(dir01.resolve("file15"));

            Context fromSubDirectoryContext = tool.createInvokedFromSubDirContext(hashSmallBlock, "dir01", hashMode == hashAll);
            superFastModeContext.setComment("From from sub directory dir01");

            compareResult = (CompareResult) commitCommand.execute(fromSubDirectoryContext);
            assertThat(compareResult.modifiedCount()).isEqualTo(1);
            modificationCounts = compareResult.getModificationCounts();
            assertThat(modificationCounts.getAdded()).isEqualTo(1);
            assertLastStateContainSameModifications(context, modificationCounts);

            // Add two files
            tool.setFileContent("file13", "New file 13");
            tool.setFileContent("file14", "New file 14");

            // Commit again using super-fast mode (hashSmallBlock)
            compareResult = (CompareResult) commitCommand.execute(superFastModeContext);
            assertThat(compareResult.modifiedCount()).isEqualTo(2);
            modificationCounts = compareResult.getModificationCounts();
            assertThat(modificationCounts.getAdded()).isEqualTo(2);
            assertLastStateContainSameModifications(context, modificationCounts);

            assertThatUsingNormalHashModeNoModificationIsDetected(context);

            logResult = (LogResult) logCommand.execute(context);
            assertThat(logResult.getLogEntries().size()).isEqualTo(4);
            modificationCounts = logResult.getLogEntries().get(2).getModificationCounts();
            assertThat(modificationCounts.getAdded()).isEqualTo(1);
            assertThat(modificationCounts.getDeleted()).isEqualTo(0);
            assertThat(modificationCounts.getCopied()).isEqualTo(0);

            assertFileExists(rootDir, "file03.dup1");
            assertFileExists(rootDir, "file03.dup2");
            assertFileExists(rootDir, "file07.dup1");
            context.setCalledFromTest(true);
            long totalFilesRemoved = (long) removeDuplicatesCommand.execute(context);
            assertThat(totalFilesRemoved).isEqualTo(3);
            assertFileDoesNotExist(rootDir, "file03.dup1");
            assertFileDoesNotExist(rootDir, "file03.dup2");
            assertFileDoesNotExist(rootDir, "file07.dup1");
        }
    }

    private void assertThatUsingNormalHashModeNoModificationIsDetected(Context context) throws Exception {
        commit_AndAssertFilesModifiedCountEqualsTo(context, 0);
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

    private void addIgnoredFiles(HashMode hashMode, Context context) throws Exception {
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
        CompareResult compareResult = (CompareResult) statusCommand.execute(context);
        assertThat(compareResult.modifiedCount()).isEqualTo(expectedModifiedFileCount);
    }

    private void commit_AndAssertFilesModifiedCountEqualsTo(Context context, int expectedModifiedFileCount) throws Exception {
        CompareResult compareResult = (CompareResult) commitCommand.execute(context);
        assertThat(compareResult.modifiedCount()).isEqualTo(expectedModifiedFileCount);

        assertLastStateHashModeEqualsTo(context, context.getHashMode());
    }

    private void assertLastStateHashModeEqualsTo(Context context, HashMode expectedHashMode) throws IOException, CorruptedStateException {
        State lastState = loadLastState(context);
        assertThat(lastState.getHashMode()).isEqualTo(expectedHashMode);
    }

    private void assertLastStateContainSameModifications(Context context, ModificationCounts modificationCounts)
            throws IOException, CorruptedStateException {
        State lastState = loadLastState(context);

        int count = countModification(lastState, added);
        assertThat(modificationCounts.getAdded()).isEqualTo(count);

        count = countModification(lastState, copied);
        assertThat(modificationCounts.getCopied()).isEqualTo(count);

        count = countModification(lastState, duplicated);
        assertThat(modificationCounts.getDuplicated()).isEqualTo(count);

        count = countModification(lastState, dateModified);
        assertThat(modificationCounts.getDateModified()).isEqualTo(count);

        count = countModification(lastState, contentModified);
        assertThat(modificationCounts.getContentModified()).isEqualTo(count);

        count = countModification(lastState, attributesModified);
        assertThat(modificationCounts.getAttributesModified()).isEqualTo(count);

        count = countModification(lastState, renamed);
        assertThat(modificationCounts.getRenamed()).isEqualTo(count);

        count = countModification(lastState, deleted);
        assertThat(modificationCounts.getDeleted()).isEqualTo(count);
    }

    private State loadLastState(Context context) throws IOException, CorruptedStateException {
        Path lastStateFile = getStateFile(context, getLastStateNumber(context));
        return State.loadFromGZipFile(lastStateFile, false);
    }

    private int countModification(State lastState, Modification modification) {
        long count = lastState.getFileStates().stream().filter(fileState -> fileState.getModification() == modification).count();
        return (int) count;
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

    private void assertCanRollbackLastCommit(Context context, int expectedLogEntriesCount, int expectedIgnoredFilesCount) throws Exception {
        rollbackCommand.execute(context);

        LogResult logResult = (LogResult) logCommand.execute(context);
        assertThat(logResult.getLogEntries().size()).isEqualTo(expectedLogEntriesCount);

        Set<String> ignoredFiles = (Set<String>) displayIgnoredFilesCommand.execute(context);
        assertThat(ignoredFiles.size()).isEqualTo(expectedIgnoredFilesCount);
    }

    private void assertFileExists(Path directory, String fileName) {
        assertThat(Files.exists(directory.resolve(fileName))).isTrue();
    }

    private void assertFileDoesNotExist(Path directory, String fileName) {
        assertThat(Files.exists(directory.resolve(fileName))).isFalse();
    }

    private Path getStateFile(Context context, int stateNumber) {
        return context.getRepositoryStatesDir().resolve("state_" + stateNumber + STATE_EXTENSION);
    }

    private int getLastStateNumber(Context context) {
        for (int index = 1; ; index++) {
            if (!Files.exists(getStateFile(context, index))) {
                return index - 1;
            }
        }
    }
}
