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

package org.fim.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;

import org.apache.commons.io.FileUtils;
import static org.assertj.core.api.Assertions.assertThat;
import org.fim.model.Context;
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.FileTime;
import org.fim.model.HashMode;
import org.fim.model.State;
import org.fim.tooling.BuildableContext;
import org.fim.tooling.BuildableState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Property-based tests for StateManager.
 * Feature: state-management
 */
public class StateManagerPropertyTest {

    private static final long FIXED_TIMESTAMP = 1437686650000L;
    private static final String FIM_STATES_DIR = ".fim/states";
    private Path rootDir;

    @BeforeEach
    void setUp() throws IOException {
        rootDir = Files.createTempDirectory("state-mgr-prop-");
        Files.createDirectories(rootDir.resolve(FIM_STATES_DIR));
    }

    @AfterEach
    void tearDown() throws IOException {
        if (rootDir != null) {
            FileUtils.deleteDirectory(rootDir.toFile());
        }
    }

    private State createSimpleState(int fileIndex) {
        BuildableContext ctx = new BuildableContext();
        BuildableState state = new BuildableState(ctx);
        state.setComment("State " + fileIndex);
        state.setHashMode(HashMode.hashAll);
        FileHash fh = new FileHash("sb_hash_" + fileIndex, "mb_hash_" + fileIndex, "full_hash_" + fileIndex);
        FileTime ft = new FileTime(FIXED_TIMESTAMP, FIXED_TIMESTAMP);
        FileState fs = new FileState("file_" + fileIndex + ".dat", fileIndex * 100L, ft, fh, null);
        state.getFileStates().add(fs);
        state.updateFileCount();
        state.updateFilesContentLength();
        return state;
    }

    // =========================================================================
    // Feature: state-management, Property 5: State number monotonic increment
    // **Validates: Requirements 2.3, 3.7**
    // =========================================================================
    @ParameterizedTest
    @MethodSource("smallN")
    void stateNumberMonotonicIncrement(int n) throws IOException {
        // Fresh directory for each trial
        Path trialDir = Files.createTempDirectory("prop5-");
        try {
            Files.createDirectories(trialDir.resolve(FIM_STATES_DIR));
            Context ctx = new Context();
            ctx.setHashMode(HashMode.hashAll);
            ctx.setCurrentDirectory(trialDir);
            ctx.setRepositoryRootDir(trialDir);
            ctx.setAlwaysYes(true);

            // Initialize settings with lastStateNumber = 0
            SettingsManager settings = new SettingsManager(ctx);
            settings.setLastStateNumber(0);
            settings.setGlobalHashMode(HashMode.hashAll);
            settings.save();

            StateManager stateManager = new StateManager(ctx);
            int initialNumber = stateManager.getLastStateNumber();

            for (int i = 1; i <= n; i++) {
                State state = createSimpleState(i);
                stateManager.createNewState(state);
            }

            assertThat(stateManager.getLastStateNumber()).isEqualTo(initialNumber + n);

            // Verify all state files exist
            for (int i = 1; i <= initialNumber + n; i++) {
                assertThat(Files.exists(stateManager.getStateFile(i)))
                        .as("State file %d should exist", i)
                        .isTrue();
            }
        } finally {
            FileUtils.deleteDirectory(trialDir.toFile());
        }
    }

    // =========================================================================
    // Feature: state-management, Property 13: State number self-healing
    // **Validates: Requirements 3.8**
    // =========================================================================
    @ParameterizedTest
    @MethodSource("smallN")
    void stateNumberSelfHealing(int n) throws IOException {
        Path trialDir = Files.createTempDirectory("prop13-");
        try {
            Files.createDirectories(trialDir.resolve(FIM_STATES_DIR));
            Context ctx = new Context();
            ctx.setHashMode(HashMode.hashAll);
            ctx.setCurrentDirectory(trialDir);
            ctx.setRepositoryRootDir(trialDir);
            ctx.setAlwaysYes(true);

            // Initialize settings
            SettingsManager settings = new SettingsManager(ctx);
            settings.setLastStateNumber(0);
            settings.setGlobalHashMode(HashMode.hashAll);
            settings.save();

            StateManager stateManager = new StateManager(ctx);

            // Create n states
            for (int i = 1; i <= n; i++) {
                State state = createSimpleState(i);
                stateManager.createNewState(state);
            }

            // Desynchronize: set lastStateNumber to a wrong value
            int wrongNumber = n + 5;
            stateManager.saveLastStateNumber(wrongNumber);

            // Verify self-healing: getLastStateNumber should return the actual highest
            int healed = stateManager.getLastStateNumber();
            assertThat(healed).isEqualTo(n);
        } finally {
            FileUtils.deleteDirectory(trialDir.toFile());
        }
    }

    // =========================================================================
    // Providers
    // =========================================================================

    /**
     * State counts small enough to enumerate completely, so every count in range runs on every build.
     */
    static IntStream smallN() {
        return IntStream.rangeClosed(1, 5);
    }
}
