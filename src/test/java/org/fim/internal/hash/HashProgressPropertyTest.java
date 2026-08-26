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

import org.fim.model.Context;
import org.fim.model.HashMode;
import org.fim.tooling.RandomInputs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for HashProgress character scale and display condition.
 * Feature: file-hashing
 */
public class HashProgressPropertyTest {

    private static final long MAX_FILE_SIZE = 2_000_000_000L;

    /** Every scale threshold, so each progress character is exercised. */
    private static final long[] SCALE_THRESHOLDS = {
        20_000_000L, 50_000_000L, 100_000_000L, 200_000_000L, 1_000_000_000L
    };

    /**
     * File sizes from 0 to 2 GB. The range is too wide to enumerate, so it is sampled from a fixed seed,
     * with the range boundaries and both sides of every scale threshold listed explicitly: uniform
     * sampling would almost never land on a threshold, and those are the interesting sizes here.
     */
    static Stream<Long> fileSizesForProgress() {
        List<Long> values = new ArrayList<>();
        values.add(0L);
        values.add(MAX_FILE_SIZE);
        for (long threshold : SCALE_THRESHOLDS) {
            values.add(threshold - 1);
            values.add(threshold);
        }

        Random random = RandomInputs.random();
        for (int i = 0; i < 100; i++) {
            values.add(RandomInputs.longBetween(random, 0L, MAX_FILE_SIZE));
        }
        return values.stream();
    }

    /** Both verbose settings crossed with every hash mode: a small domain, so all of it. */
    static Stream<Arguments> verboseAndHashModes() {
        List<Arguments> args = new ArrayList<>();
        for (Boolean verbose : List.of(true, false)) {
            for (HashMode hashMode : HashMode.values()) {
                args.add(Arguments.of(verbose, hashMode));
            }
        }
        return args.stream();
    }

    // Feature: file-hashing, Property 13: Progress character matches file size scale
    // **Validates: Requirements 11.3**
    @ParameterizedTest
    @MethodSource("fileSizesForProgress")
    void progressCharacterMatchesFileSizeScale(long fileSize) {
        Context context = new Context();
        context.setUseDynamicScaling(false);
        HashProgress progress = new HashProgress(context);

        char result = progress.getProgressChar(fileSize);

        if (fileSize >= 1_000_000_000L) {
            assertThat(result).isEqualTo('#');
        } else if (fileSize >= 200_000_000L) {
            assertThat(result).isEqualTo('@');
        } else if (fileSize >= 100_000_000L) {
            assertThat(result).isEqualTo('O');
        } else if (fileSize >= 50_000_000L) {
            assertThat(result).isEqualTo('8');
        } else if (fileSize >= 20_000_000L) {
            assertThat(result).isEqualTo('o');
        } else {
            assertThat(result).isEqualTo('.');
        }
    }

    // Feature: file-hashing, Property 14: Progress display condition
    // **Validates: Requirements 11.1, 11.5**
    @ParameterizedTest
    @MethodSource("verboseAndHashModes")
    void progressDisplayCondition(boolean verbose, HashMode hashMode) {
        Context context = new Context();
        context.setVerbose(verbose);
        context.setHashMode(hashMode);
        context.setUseDynamicScaling(false);

        HashProgress progress = new HashProgress(context);

        boolean expected = verbose && hashMode != HashMode.dontHash;
        assertThat(progress.isProgressDisplayed()).isEqualTo(expected);
    }
}
