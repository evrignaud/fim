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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import static org.assertj.core.api.Assertions.assertThat;
import org.fim.internal.StateComparator;
import org.fim.internal.StateManager;
import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.model.HashMode;
import org.fim.model.ModificationCounts;
import org.fim.model.State;
import org.fim.tooling.BuildableContext;
import org.fim.tooling.BuildableState;
import org.fim.tooling.RandomInputs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Property-based tests for CommitCommand behavior.
 * Feature: state-management
 */
public class CommitCommandPropertyTest {

    // =========================================================================
    // Feature: state-management, Property 7: No-op commit when unchanged
    // **Validates: Requirements 2.4**
    // =========================================================================
    @ParameterizedTest
    @MethodSource("fileCountArb")
    void noOpCommitWhenUnchanged(int fileCount) throws Exception {
        Path trialDir = Files.createTempDirectory("prop7-");
        try {
            Context context = createRepoContext(trialDir);
            context.setComment("Initial");

            // Create files and init
            createFiles(trialDir, fileCount);
            InitCommand initCommand = new InitCommand();
            initCommand.execute(context);

            StateManager stateManager = new StateManager(context);
            int stateNumberBefore = stateManager.getLastStateNumber();

            // Commit again without any changes
            context.setComment("No-op commit");
            CommitCommand commitCommand = new CommitCommand();
            CompareResult result = (CompareResult) commitCommand.execute(context);

            assertThat(result.somethingModified()).isFalse();
            assertThat(stateManager.getLastStateNumber()).isEqualTo(stateNumberBefore);
            assertThat(Files.exists(stateManager.getStateFile(stateNumberBefore + 1))).isFalse();
        } finally {
            FileUtils.deleteDirectory(trialDir.toFile());
        }
    }

    // =========================================================================
    // Feature: state-management, Property 8: Subdirectory commit consolidation
    // **Validates: Requirements 2.11**
    // =========================================================================
    @ParameterizedTest
    @MethodSource("smallFileCountPairs")
    void subdirectoryCommitConsolidation(
            int rootFileCount,
            int subFileCount) throws Exception {
        Path trialDir = Files.createTempDirectory("prop8-");
        try {
            Context context = createRepoContext(trialDir);
            context.setComment("Initial");

            // Create root-level files
            createFiles(trialDir, rootFileCount);

            // Create subdirectory files
            Path subDir = trialDir.resolve("subdir");
            Files.createDirectories(subDir);
            for (int i = 1; i <= subFileCount; i++) {
                createFileWithContent(subDir.resolve("subfile" + String.format("%02d", i)),
                        "Sub content " + i);
            }

            InitCommand initCommand = new InitCommand();
            initCommand.execute(context);

            int totalInitialFiles = rootFileCount + subFileCount;
            StateManager stateManager = new StateManager(context);
            State initialState = stateManager.loadLastState();
            assertThat(initialState.getFileCount()).isEqualTo(totalInitialFiles);

            // Add a new file in the subdirectory
            createFileWithContent(subDir.resolve("subfile_new"), "New sub content");

            // Commit from subdirectory
            Context subDirContext = createRepoContext(trialDir);
            subDirContext.setCurrentDirectory(subDir);
            subDirContext.setInvokedFromSubDirectory(true);
            subDirContext.setComment("Subdir commit");

            CommitCommand commitCommand = new CommitCommand();
            CompareResult result = (CompareResult) commitCommand.execute(subDirContext);
            assertThat(result.somethingModified()).isTrue();

            // Consolidated state should contain root files + all subdir files (including new one)
            State consolidatedState = stateManager.loadLastState();
            assertThat(consolidatedState.getFileCount()).isEqualTo(totalInitialFiles + 1);

            // Verify root files are still present
            long rootFilesInConsolidated = consolidatedState.getFileStates().stream()
                    .filter(fs -> !fs.getFileName().startsWith("subdir/"))
                    .count();
            assertThat(rootFilesInConsolidated).isEqualTo(rootFileCount);

            // Verify subdir files are present (original + new)
            long subFilesInConsolidated = consolidatedState.getFileStates().stream()
                    .filter(fs -> fs.getFileName().startsWith("subdir/"))
                    .count();
            assertThat(subFilesInConsolidated).isEqualTo(subFileCount + 1);
        } finally {
            FileUtils.deleteDirectory(trialDir.toFile());
        }
    }

    // =========================================================================
    // Feature: state-management, Property 9: ModificationCounts consistency
    // **Validates: Requirements 2.14**
    // =========================================================================
    @ParameterizedTest
    @MethodSource("stateWithDifferences")
    void modificationCountsConsistency(
            StatePair statePair) {
        BuildableContext ctx = new BuildableContext();
        StateComparator comparator = new StateComparator(ctx, statePair.lastState, statePair.currentState);
        CompareResult result = comparator.compare();

        ModificationCounts counts = result.getModificationCounts();

        assertThat(counts.getAdded()).isEqualTo(result.getAdded().size());
        assertThat(counts.getDeleted()).isEqualTo(result.getDeleted().size());
        assertThat(counts.getContentModified()).isEqualTo(result.getContentModified().size());
        assertThat(counts.getDateModified()).isEqualTo(result.getDateModified().size());
        assertThat(counts.getAttributesModified()).isEqualTo(result.getAttributesModified().size());
        assertThat(counts.getRenamed()).isEqualTo(result.getRenamed().size());
        assertThat(counts.getDuplicated()).isEqualTo(result.getDuplicated().size());
        // corrupted is NOT populated by getModificationCounts()
        assertThat(counts.getCorrupted()).isEqualTo(0);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    static class StatePair {
        final State lastState;
        final State currentState;

        StatePair(State lastState, State currentState) {
            this.lastState = lastState;
            this.currentState = currentState;
        }
    }

    private Context createRepoContext(Path dir) {
        Context ctx = new Context();
        ctx.setHashMode(HashMode.hashAll);
        ctx.setCurrentDirectory(dir);
        ctx.setRepositoryRootDir(dir);
        ctx.setAlwaysYes(true);
        ctx.setVerbose(true);
        ctx.setComment("");
        return ctx;
    }

    private void createFiles(Path dir, int count) throws IOException {
        for (int i = 1; i <= count; i++) {
            createFileWithContent(dir.resolve("file" + String.format("%02d", i)),
                    "File content " + i);
        }
    }

    private void createFileWithContent(Path file, String content) throws IOException {
        // Create a file with enough content for hashing
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("line_").append(i).append(": ").append(content).append('\n');
        }
        Files.write(file, sb.toString().getBytes());
    }

    // =========================================================================
    // Providers
    // =========================================================================

    /**
     * File counts 1 to 4. Each case initialises a real Fim repository on disk, so the range is kept
     * exactly as it was and enumerated rather than sampled.
     */
    static IntStream fileCountArb() {
        return IntStream.rangeClosed(1, 4);
    }

    /**
     * The full cross product of two independent 1 to 3 file counts: nine cases, so no sampling needed.
     */
    static Stream<Arguments> smallFileCountPairs() {
        List<Arguments> pairs = new ArrayList<>();
        for (int rootFileCount = 1; rootFileCount <= 3; rootFileCount++) {
            for (int subFileCount = 1; subFileCount <= 3; subFileCount++) {
                pairs.add(Arguments.of(rootFileCount, subFileCount));
            }
        }
        return pairs.stream();
    }

    /**
     * A "last" state and a "current" state with known differences. The shapes with no differences at all
     * and with the maximum number of differences are listed explicitly, then a seeded sample walks the
     * same ranges the generator used: 1 to 5 base files, 0 to 3 added, and 0 to min(2, baseCount) each of
     * deleted and modified.
     */
    static Stream<StatePair> stateWithDifferences() {
        List<StatePair> values = new ArrayList<>();
        // Explicit boundaries first: nothing changed, then everything changed
        values.add(buildStatePair(1, 0, 0, 0));
        values.add(buildStatePair(5, 3, 2, 2));

        Random random = RandomInputs.random();
        for (int i = 0; i < 100; i++) {
            int baseCount = RandomInputs.intBetween(random, 1, 5);
            int added = RandomInputs.intBetween(random, 0, 3);
            int deleted = RandomInputs.intBetween(random, 0, Math.min(2, baseCount));
            int modified = RandomInputs.intBetween(random, 0, Math.min(2, baseCount));
            values.add(buildStatePair(baseCount, added, deleted, modified));
        }
        return values.stream();
    }

    private static StatePair buildStatePair(int baseCount, int added, int deleted, int modified) {
        // Ensure we don't try to delete+modify more files than exist
        int actualDeleted = Math.min(deleted, baseCount);
        int actualModified = Math.min(modified, baseCount - actualDeleted);

        BuildableContext ctx = new BuildableContext();
        BuildableState lastState = new BuildableState(ctx);

        // Add base files
        String[] baseFiles = new String[baseCount];
        for (int i = 0; i < baseCount; i++) {
            baseFiles[i] = "file_" + String.format("%03d", i) + ".dat";
        }
        lastState = lastState.addFiles(baseFiles);

        // Build current state from last state
        BuildableState currentState = lastState.clone();

        // Add new files
        for (int i = 0; i < added; i++) {
            currentState = currentState.addFiles("new_file_" + i + ".dat");
        }

        // Delete files
        for (int i = 0; i < actualDeleted; i++) {
            currentState = currentState.delete(baseFiles[i]);
        }

        // Modify content of remaining files
        for (int i = actualDeleted; i < actualDeleted + actualModified; i++) {
            currentState = currentState.setContent(baseFiles[i], "modified_content_" + i);
        }

        return new StatePair(lastState, currentState);
    }
}
