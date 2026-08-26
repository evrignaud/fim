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

package org.fim.internal.hash;

import org.fim.tooling.RandomInputs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for ThroughputKeeper minimum duration prevents division by zero.
 * Feature: file-hashing
 */
public class ThroughputPropertyTest {

    private static final long MAX_UPDATE_SIZE = 10_000_000L;
    private static final int MAX_SEQUENCE_LENGTH = 20;

    /**
     * Sequences of update sizes. Both the sequence length and the sizes are sampled from a fixed seed,
     * with the shortest and longest sequences, and the smallest and largest sizes, listed explicitly.
     */
    static Stream<List<Long>> updateSequences() {
        List<List<Long>> sequences = new ArrayList<>();
        sequences.add(List.of(1L));
        sequences.add(List.of(MAX_UPDATE_SIZE));

        List<Long> longest = new ArrayList<>(MAX_SEQUENCE_LENGTH);
        for (int i = 0; i < MAX_SEQUENCE_LENGTH; i++) {
            longest.add(MAX_UPDATE_SIZE);
        }
        sequences.add(longest);

        Random random = RandomInputs.random();
        for (int i = 0; i < 100; i++) {
            int length = RandomInputs.intBetween(random, 1, MAX_SEQUENCE_LENGTH);
            List<Long> sequence = new ArrayList<>(length);
            for (int j = 0; j < length; j++) {
                sequence.add(RandomInputs.longBetween(random, 1L, MAX_UPDATE_SIZE));
            }
            sequences.add(sequence);
        }
        return sequences.stream();
    }

    // Feature: file-hashing, Property 15: ThroughputKeeper minimum duration prevents division by zero
    // **Validates: Requirements 12.5**
    @ParameterizedTest
    @MethodSource("updateSequences")
    void throughputKeeperMinimumDurationPreventsDivisionByZero(List<Long> updates) {
        ThroughputKeeper keeper = new ThroughputKeeper();

        for (long size : updates) {
            keeper.update(size);
        }

        // getInstantThroughput() must never throw and must return non-negative
        long throughput = keeper.getInstantThroughput();
        assertThat(throughput).isGreaterThanOrEqualTo(0L);
    }
}
