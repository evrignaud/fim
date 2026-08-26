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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import org.fim.model.Context;
import org.fim.tooling.RandomInputs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * DynamicScaling thread count bounds.
 * Feature: file-hashing
 */
public class DynamicScalingPropertyTest {

    /**
     * Sequences of file sizes to feed through the scaler. Sizes span the full range the scaler sees, so
     * these are sampled from a fixed seed rather than enumerated. The boundary sizes are included
     * explicitly as the first two sequences.
     */
    static Stream<List<Long>> fileSizeSequences() {
        List<List<Long>> sequences = new ArrayList<>();
        // Explicit boundaries first
        sequences.add(List.of(1L));
        sequences.add(List.of(100_000_000L));
        sequences.add(List.of(1L, 100_000_000L, 1L));

        Random random = RandomInputs.random();
        for (int i = 0; i < 100; i++) {
            int size = RandomInputs.intBetween(random, 1, 30);
            List<Long> sequence = new ArrayList<>(size);
            for (int j = 0; j < size; j++) {
                sequence.add(RandomInputs.longBetween(random, 1L, 100_000_000L));
            }
            sequences.add(sequence);
        }
        return sequences.stream();
    }

    // Feature: file-hashing, Property 16: DynamicScaling thread count stays within bounds
    // **Validates: Requirements 13.3, 13.5, 13.6, 13.7**
    @ParameterizedTest
    @MethodSource("fileSizeSequences")
    void dynamicScalingThreadCountStaysWithinBounds(List<Long> fileSizes) {
        Context context = new Context();
        context.setUseDynamicScaling(true);
        context.setThreadCount(1);

        DynamicScaling scaling = new DynamicScaling(context);

        int minThreads = scaling.getMinThreads();
        int maxThreads = scaling.getMaxThreads();

        // Verify bounds are correct
        assertThat(minThreads).isEqualTo(1);
        assertThat(maxThreads).isEqualTo(Math.max(4, Runtime.getRuntime().availableProcessors() * 2));

        // Process files and verify thread count stays within bounds after each call
        for (long fileSize : fileSizes) {
            scaling.fileProcessed(fileSize);

            int currentThreadCount = scaling.getCurrentThreadCount();
            assertThat(currentThreadCount)
                    .as("Thread count must be >= %d and <= %d, but was %d", minThreads, maxThreads, currentThreadCount)
                    .isGreaterThanOrEqualTo(minThreads)
                    .isLessThanOrEqualTo(maxThreads);
        }
    }
}
