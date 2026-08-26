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
import java.util.stream.IntStream;

import org.apache.commons.io.FileUtils;
import static org.assertj.core.api.Assertions.assertThat;
import org.fim.internal.StateManager;
import org.fim.model.Context;
import org.fim.model.HashMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Property-based tests for RollbackCommand.
 * Feature: state-management
 */
public class RollbackCommandPropertyTest {

    // =========================================================================
    // Feature: state-management, Property 10: Rollback decrements state count
    // **Validates: Requirements 4.3**
    // =========================================================================
    @ParameterizedTest
    @MethodSource("stateCount")
    void rollbackDecrementsStateCount(int n) throws Exception {
        Path trialDir = Files.createTempDirectory("prop10-");
        try {
            Context context = createRepoContext(trialDir);
            context.setComment("Initial");

            // Create initial files and init
            createFileWithContent(trialDir.resolve("file01"), "Content 1");
            InitCommand initCommand = new InitCommand();
            initCommand.execute(context);

            // Create additional commits
            CommitCommand commitCommand = new CommitCommand();
            for (int i = 2; i <= n; i++) {
                createFileWithContent(trialDir.resolve("file" + String.format("%02d", i)),
                        "Content " + i);
                context.setComment("Commit " + i);
                commitCommand.execute(context);
            }

            StateManager stateManager = new StateManager(context);
            assertThat(stateManager.getLastStateNumber()).isEqualTo(n);

            // Perform rollback
            RollbackCommand rollbackCommand = new RollbackCommand();
            rollbackCommand.execute(context);

            // Verify: state number decremented, last state file deleted, previous still exists
            assertThat(stateManager.getLastStateNumber()).isEqualTo(n - 1);
            assertThat(Files.exists(stateManager.getStateFile(n))).isFalse();
            assertThat(Files.exists(stateManager.getStateFile(n - 1))).isTrue();
        } finally {
            FileUtils.deleteDirectory(trialDir.toFile());
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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

    private void createFileWithContent(Path file, String content) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("line_").append(i).append(": ").append(content).append('\n');
        }
        Files.write(file, sb.toString().getBytes());
    }

    // =========================================================================
    // Providers
    // =========================================================================

    /** Small enough to enumerate: every state count from 2 to 5 is exercised on every run. */
    static IntStream stateCount() {
        return IntStream.rangeClosed(2, 5);
    }
}
