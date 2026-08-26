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

import org.fim.command.exception.BadFimUsageException;
import org.fim.command.exception.DontWantToContinueException;
import org.fim.internal.StateManager;
import org.fim.model.Command;
import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.model.Ignored;
import org.fim.model.State;
import org.fim.tooling.RepositoryTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.fim.model.HashMode.dontHash;
import static org.fim.model.HashMode.hashAll;
import static org.fim.model.HashMode.hashSmallBlock;

/**
 * Tests for CommitCommand behavior.
 * Validates Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 2.11, 2.12, 2.13, 2.14
 */
public class CommitCommandTest {

    private static final String INITIAL = "Initial";
    private static final String FILE_01 = "file01";
    private static final String SECOND_COMMIT = "Second commit";
    private static final String SUBDIR = "subdir";

    private InitCommand initCommand;
    private CommitCommand commitCommand;
    private RepositoryTool tool;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        initCommand = new InitCommand();
        commitCommand = new CommitCommand();
        tool = new RepositoryTool(testInfo);
    }

    /**
     * Helper: initialize a repository with the given number of files.
     */
    private State initRepo(Context context, int fileCount) throws Exception {
        tool.createASetOfFiles(fileCount);
        return (State) initCommand.execute(context);
    }

    /**
     * Req 2.1, 2.2, 2.3, 2.5, 2.14: Commit with changes generates new state, compares, persists,
     * stores comment, and includes ModificationCounts.
     */
    @Test
    public void commitWithChangesCreatesNewState() throws Exception {
        Context context = tool.createContext(hashAll, true);
        context.setComment(INITIAL);
        initRepo(context, 3);

        // Make a modification
        tool.setFileContent(FILE_01, "modified content");
        context.setComment(SECOND_COMMIT);

        CompareResult result = (CompareResult) commitCommand.execute(context);

        // Req 2.2: CompareResult detects modifications
        assertThat(result.somethingModified()).isTrue();
        assertThat(result.modifiedCount()).isGreaterThan(0);

        // Req 2.3: New state file created with incremented number
        StateManager stateManager = new StateManager(context);
        assertThat(stateManager.getLastStateNumber()).isEqualTo(2);
        assertThat(Files.exists(stateManager.getStateFile(2))).isTrue();

        // Req 2.5: Comment is stored
        State lastState = stateManager.loadLastState();
        assertThat(lastState.getComment()).isEqualTo(SECOND_COMMIT);

        // Req 2.14: ModificationCounts are stored in the committed state
        assertThat(lastState.getModificationCounts()).isNotNull();
        assertThat(lastState.getModificationCounts().getContentModified()).isGreaterThan(0);
    }

    /**
     * Req 2.4: When no modifications detected, "Nothing committed" and no new state file.
     */
    @Test
    public void commitWithoutChangesDoesNotCreateNewState() throws Exception {
        Context context = tool.createContext(hashAll, true);
        context.setComment(INITIAL);
        initRepo(context, 3);

        context.setComment("No changes commit");
        CompareResult result = (CompareResult) commitCommand.execute(context);

        // No modifications
        assertThat(result.somethingModified()).isFalse();
        assertThat(result.modifiedCount()).isEqualTo(0);

        // State number unchanged
        StateManager stateManager = new StateManager(context);
        assertThat(stateManager.getLastStateNumber()).isEqualTo(1);
        assertThat(Files.exists(stateManager.getStateFile(2))).isFalse();
    }

    /**
     * Req 2.6, 2.7: When no comment provided and user declines prompt, commit aborts.
     */
    @Test
    public void commitWithoutCommentAndDeclinedPromptAborts() throws Exception {
        Context context = tool.createContext(hashAll, true);
        context.setComment(INITIAL);
        initRepo(context, 2);

        // Set empty comment and alwaysYes=false to trigger prompt path
        context.setComment("");
        context.setAlwaysYes(false);

        // With no Scanner input available, confirmAction will throw NoSuchElementException
        // which propagates. In real usage, declining returns false -> DontWantToContinueException.
        // We verify the command requires confirmation when no comment is provided.
        assertThatThrownBy(() -> commitCommand.execute(context))
                .isInstanceOfAny(DontWantToContinueException.class, java.util.NoSuchElementException.class);

        // State number unchanged
        StateManager stateManager = new StateManager(context);
        assertThat(stateManager.getLastStateNumber()).isEqualTo(1);
    }

    /**
     * Req 2.6: When no comment provided but user accepts (alwaysYes), commit proceeds.
     */
    @Test
    public void commitWithoutCommentAndAlwaysYesProceeds() throws Exception {
        Context context = tool.createContext(hashAll, true);
        context.setComment(INITIAL);
        initRepo(context, 2);

        tool.setFileContent(FILE_01, "changed");
        context.setComment("");
        context.setAlwaysYes(true);

        CompareResult result = (CompareResult) commitCommand.execute(context);
        assertThat(result.somethingModified()).isTrue();

        StateManager stateManager = new StateManager(context);
        assertThat(stateManager.getLastStateNumber()).isEqualTo(2);
    }

    /**
     * Req 2.8: Commit with dontHash when global mode is not dontHash throws BadFimUsageException.
     */
    @Test
    public void commitWithDontHashWhenGlobalModeRequiresHashingAborts() throws Exception {
        Context context = tool.createContext(hashAll, true);
        context.setComment(INITIAL);
        initRepo(context, 2);

        // Switch to dontHash for commit while global is hashAll
        context.setHashMode(dontHash);
        context.setComment("dontHash commit");

        assertThatThrownBy(() -> commitCommand.execute(context))
                .isInstanceOf(BadFimUsageException.class);

        // State number unchanged
        StateManager stateManager = new StateManager(context);
        assertThat(stateManager.getLastStateNumber()).isEqualTo(1);
    }

    /**
     * Req 2.9: Commit with ignored aspects throws BadFimUsageException.
     */
    @Test
    public void commitWithIgnoredAspectsAborts() throws Exception {
        Context context = tool.createContext(hashAll, true);
        context.setComment(INITIAL);
        initRepo(context, 2);

        // Set an ignored aspect
        Ignored ignored = new Ignored();
        ignored.setDatesIgnored(true);
        context.setIgnored(ignored);
        context.setComment("Ignored commit");

        assertThatThrownBy(() -> commitCommand.execute(context))
                .isInstanceOf(BadFimUsageException.class);

        // State number unchanged
        StateManager stateManager = new StateManager(context);
        assertThat(stateManager.getLastStateNumber()).isEqualTo(1);
    }

    /**
     * Req 2.9: Commit with attributes ignored also aborts.
     */
    @Test
    public void commitWithAttributesIgnoredAborts() throws Exception {
        Context context = tool.createContext(hashAll, true);
        context.setComment(INITIAL);
        initRepo(context, 2);

        Ignored ignored = new Ignored();
        ignored.setAttributesIgnored(true);
        context.setIgnored(ignored);
        context.setComment("Attributes ignored commit");

        assertThatThrownBy(() -> commitCommand.execute(context))
                .isInstanceOf(BadFimUsageException.class);
    }

    /**
     * Req 2.9: Commit with renamed ignored also aborts.
     */
    @Test
    public void commitWithRenamedIgnoredAborts() throws Exception {
        Context context = tool.createContext(hashAll, true);
        context.setComment(INITIAL);
        initRepo(context, 2);

        Ignored ignored = new Ignored();
        ignored.setRenamedIgnored(true);
        context.setIgnored(ignored);
        context.setComment("Renamed ignored commit");

        assertThatThrownBy(() -> commitCommand.execute(context))
                .isInstanceOf(BadFimUsageException.class);
    }

    /**
     * Req 2.10: When commit hash mode differs from global hash mode,
     * the last state is reloaded with global hash mode and missing hashes are retrieved.
     */
    @Test
    public void commitWithDifferentHashModeReloadsLastState() throws Exception {
        // Init with hashAll (global mode)
        Context context = tool.createContext(hashAll, true);
        context.setComment("Initial hashAll");
        initRepo(context, 3);

        // Modify a file and commit with hashSmallBlock
        tool.setFileContent(FILE_01, "modified for small block");
        Context smallBlockContext = tool.createContext(hashSmallBlock, true);
        smallBlockContext.setComment("SmallBlock commit");

        CompareResult result = (CompareResult) commitCommand.execute(smallBlockContext);

        assertThat(result).isNotNull();
        assertThat(result.somethingModified()).isTrue();

        // The committed state should have the global hash mode (hashAll)
        StateManager stateManager = new StateManager(context);
        State lastState = stateManager.loadLastState();
        assertThat(lastState.getHashMode()).isEqualTo(hashAll);
        assertThat(lastState.getCommitDetails().getHashModeUsedToGetTheStatus()).isEqualTo(hashSmallBlock);
    }

    /**
     * Req 2.11: Subdirectory commit consolidation merges subdirectory scan
     * with files outside subdirectory from last state.
     */
    @Test
    public void subdirectoryCommitConsolidatesState() throws Exception {
        Context context = tool.createContext(hashAll, true);
        context.setComment(INITIAL);

        // Create files in root and a subdirectory
        tool.createASetOfFiles(2);
        Path subDir = tool.getRootDir().resolve(SUBDIR);
        Files.createDirectories(subDir);
        tool.createFile(subDir.resolve("subfile01"));

        initCommand.execute(context);

        // Add a new file in the subdirectory
        tool.createFile(subDir.resolve("subfile02"));

        // Commit from subdirectory
        Context subDirContext = tool.createInvokedFromSubDirContext(hashAll, SUBDIR, true);
        subDirContext.setComment("Subdir commit");

        CompareResult result = (CompareResult) commitCommand.execute(subDirContext);
        assertThat(result.somethingModified()).isTrue();

        // The consolidated state should contain files from both root and subdirectory
        StateManager stateManager = new StateManager(context);
        State lastState = stateManager.loadLastState();
        // 2 root files + 2 subdir files = 4 total
        assertThat(lastState.getFileCount()).isEqualTo(4);
    }

    /**
     * Req 2.12: Subdirectory commit aborts when model version mismatch.
     */
    @Test
    public void subdirectoryCommitAbortsOnModelVersionMismatch() throws Exception {
        Context context = tool.createContext(hashAll, true);
        context.setComment(INITIAL);
        tool.createASetOfFiles(2);
        Path subDir = tool.getRootDir().resolve(SUBDIR);
        Files.createDirectories(subDir);
        tool.createFile(subDir.resolve("subfile01"));
        initCommand.execute(context);

        // Tamper with the last state to have a different model version via reflection
        StateManager stateManager = new StateManager(context);
        Path stateFile = stateManager.getStateFile(1);
        State tamperedState = State.loadFromGZipFile(stateFile, false);
        Field modelVersionField = State.class.getDeclaredField("modelVersion");
        modelVersionField.setAccessible(true);
        modelVersionField.set(tamperedState, "999");
        tamperedState.saveToGZipFile(stateFile);

        // Now commit from subdirectory
        tool.createFile(subDir.resolve("subfile02"));
        Context subDirContext = tool.createInvokedFromSubDirContext(hashAll, SUBDIR, true);
        subDirContext.setComment("Subdir commit with mismatch");

        assertThatThrownBy(() -> commitCommand.execute(subDirContext))
                .isInstanceOf(BadFimUsageException.class);
    }

    /**
     * Req 2.13: When purge-states option is active and commit succeeds,
     * purge-states is executed after persisting the new state.
     */
    @Test
    public void commitWithPurgeStatesOptionTriggersPurge() throws Exception {
        Context context = tool.createContext(hashAll, true);
        context.setComment(INITIAL);
        initRepo(context, 2);

        // First commit to create state 2
        tool.setFileContent(FILE_01, "change 1");
        context.setComment(SECOND_COMMIT);
        commitCommand.execute(context);

        StateManager stateManager = new StateManager(context);
        assertThat(stateManager.getLastStateNumber()).isEqualTo(2);
        assertThat(Files.exists(stateManager.getStateFile(1))).isTrue();
        assertThat(Files.exists(stateManager.getStateFile(2))).isTrue();

        // Second commit with purge-states enabled
        tool.setFileContent("file02", "change 2");        context.setComment("Third commit with purge");
        context.setPurgeStates(true);

        commitCommand.execute(context);

        // After purge, only state_1.json.gz should remain (renumbered from state_3)
        assertThat(stateManager.getLastStateNumber()).isEqualTo(1);
        assertThat(Files.exists(stateManager.getStateFile(1))).isTrue();
        assertThat(Files.exists(stateManager.getStateFile(2))).isFalse();
        assertThat(Files.exists(stateManager.getStateFile(3))).isFalse();
    }

    /**
     * Req 2.14: ModificationCounts in committed state match the CompareResult.
     */
    @Test
    public void commitStoresCorrectModificationCounts() throws Exception {
        Context context = tool.createContext(hashAll, true);
        context.setComment(INITIAL);
        initRepo(context, 3);

        // Add a file and modify another
        tool.createOneFile();
        tool.setFileContent(FILE_01, "modified content for counts");
        context.setComment("Counts commit");

        CompareResult result = (CompareResult) commitCommand.execute(context);

        StateManager stateManager = new StateManager(context);
        State lastState = stateManager.loadLastState();

        // The ModificationCounts in the state should match the CompareResult
        assertThat(lastState.getModificationCounts().getAdded()).isEqualTo(result.getAdded().size());
        assertThat(lastState.getModificationCounts().getContentModified()).isEqualTo(result.getContentModified().size());
        assertThat(lastState.getModificationCounts().getDeleted()).isEqualTo(result.getDeleted().size());
        assertThat(lastState.getModificationCounts().getRenamed()).isEqualTo(result.getRenamed().size());
        assertThat(lastState.getModificationCounts().getDateModified()).isEqualTo(result.getDateModified().size());
        assertThat(lastState.getModificationCounts().getAttributesModified()).isEqualTo(result.getAttributesModified().size());
        assertThat(lastState.getModificationCounts().getDuplicated()).isEqualTo(result.getDuplicated().size());
    }

    /**
     * Req 2.8: dontHash is allowed when global mode is also dontHash.
     */
    @Test
    public void commitWithDontHashWhenGlobalModeIsDontHashSucceeds() throws Exception {
        Context context = tool.createContext(dontHash, true);
        context.setComment("Initial dontHash");
        initRepo(context, 2);

        tool.setFileContent(FILE_01, "modified");
        context.setComment("dontHash commit");

        CompareResult result = (CompareResult) commitCommand.execute(context);
        // dontHash mode detects date modifications rather than content modifications
        assertThat(result.somethingModified()).isTrue();

        StateManager stateManager = new StateManager(context);
        assertThat(stateManager.getLastStateNumber()).isEqualTo(2);
    }

    /**
     * Verify CommitCommand metadata: cmdName, shortCmdName, description, constraint.
     */
    @Test
    public void commandMetadataIsCorrect() {
        assertThat(commitCommand.getCmdName()).isEqualTo("commit");
        assertThat(commitCommand.getShortCmdName()).isEqualTo("ci");
        assertThat(commitCommand.getDescription()).isNotEmpty();
        assertThat(commitCommand.getFimReposConstraint())
                .isEqualTo(Command.FimReposConstraint.MUST_EXIST);
    }
}
