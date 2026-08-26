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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import org.fim.model.Context;
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.FileTime;
import org.fim.model.HashMode;
import org.fim.model.LogEntry;
import org.fim.model.ModificationCounts;
import org.fim.model.State;
import org.fim.tooling.BuildableContext;
import org.fim.tooling.BuildableState;
import org.fim.tooling.RandomInputs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Property-based tests for LogCommand.
 * Feature: state-management
 */
public class LogCommandPropertyTest {

    private static final String ALPHANUM = "abcdefghijklmnopqrstuvwxyz0123456789";

    // =========================================================================
    // Feature: state-management, Property 12: Log entry completeness
    // **Validates: Requirements 5.1, 5.2, 5.3, 5.4**
    // =========================================================================
    @ParameterizedTest
    @MethodSource("randomStateWithNumber")
    void logEntryCompleteness(
            StateWithNumber stateWithNumber) {
        Context context = new BuildableContext();
        State state = stateWithNumber.state;
        int stateNumber = stateWithNumber.stateNumber;

        // Set up ModificationCounts on the state
        ModificationCounts mc = new ModificationCounts();
        mc.setAdded(state.getFileStates().size());
        state.setModificationCounts(mc);

        LogEntry logEntry = new LogEntry(context, state, stateNumber);

        // Verify state number
        assertThat(logEntry.getStateNumber()).isEqualTo(stateNumber);

        // Verify non-null timestamp
        assertThat(logEntry.getTimestamp()).isGreaterThan(0);

        // Verify file count matches state
        assertThat(logEntry.getFileCount()).isEqualTo(state.getFileCount());

        // Verify files content length
        assertThat(logEntry.getFilesContentLength()).isEqualTo(state.getFilesContentLength());

        // Verify comment
        assertThat(logEntry.getComment()).isEqualTo(state.getComment());

        // Verify non-null ModificationCounts
        assertThat(logEntry.getModificationCounts()).isNotNull();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    static class StateWithNumber {
        final State state;
        final int stateNumber;

        StateWithNumber(State state, int stateNumber) {
            this.state = state;
            this.stateNumber = stateNumber;
        }
    }

    // =========================================================================
    // Providers
    // =========================================================================

    /**
     * A state paired with its state number. The state number spans 1 to 20, the comment 0 to 30
     * characters and the file state list 1 to 5 entries, which is too wide to enumerate, so the smallest
     * and the largest shape are listed explicitly and the rest is a seeded sample over the same ranges.
     */
    static Stream<StateWithNumber> randomStateWithNumber() {
        List<StateWithNumber> values = new ArrayList<>();
        Random random = RandomInputs.random();
        values.add(buildStateWithNumber(1, "", randomFileStates(random, 1)));
        values.add(buildStateWithNumber(20, RandomInputs.stringOf(random, ALPHANUM, 30, 30),
                randomFileStates(random, 5)));
        for (int i = 0; i < 100; i++) {
            int num = RandomInputs.intBetween(random, 1, 20);
            String comment = RandomInputs.stringOf(random, ALPHANUM, 0, 30);
            List<FileState> fileStates = randomFileStates(random, RandomInputs.intBetween(random, 1, 5));
            values.add(buildStateWithNumber(num, comment, fileStates));
        }
        return values.stream();
    }

    private static StateWithNumber buildStateWithNumber(int num, String comment, List<FileState> fileStates) {
        BuildableContext ctx = new BuildableContext();
        BuildableState state = new BuildableState(ctx);
        state.setComment(comment);
        state.setHashMode(HashMode.hashAll);
        state.getFileStates().addAll(fileStates);
        state.updateFileCount();
        state.updateFilesContentLength();
        return new StateWithNumber(state, num);
    }

    private static List<FileState> randomFileStates(Random random, int count) {
        List<FileState> fileStates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            fileStates.add(randomFileState(random));
        }
        return fileStates;
    }

    static FileState randomFileState(Random random) {
        String name = RandomInputs.stringOf(random, ALPHANUM, 1, 15) + ".dat";
        long length = RandomInputs.longBetween(random, 0, 100_000);
        long ts = RandomInputs.longBetween(random, 1_000_000_000_000L, 2_000_000_000_000L);
        String hash = RandomInputs.stringOf(random, ALPHANUM, 5, 20);
        FileHash fh = new FileHash("sb_" + hash, "mb_" + hash, "full_" + hash);
        FileTime ft = new FileTime(ts, ts);
        return new FileState(name, length, ft, fh, null);
    }
}
