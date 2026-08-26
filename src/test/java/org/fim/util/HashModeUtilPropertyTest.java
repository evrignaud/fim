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

package org.fim.util;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import org.fim.model.HashMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Property-based tests for HashModeUtil compatibility transitivity.
 * Feature: file-hashing
 */
public class HashModeUtilPropertyTest {

    /**
     * Every triple of hash modes. Four modes give 64 combinations, so the whole domain is enumerated
     * instead of being sampled.
     */
    static Stream<Arguments> hashModeTriples() {
        List<Arguments> triples = new ArrayList<>();
        for (HashMode a : HashMode.values()) {
            for (HashMode b : HashMode.values()) {
                for (HashMode x : HashMode.values()) {
                    triples.add(Arguments.of(a, b, x));
                }
            }
        }
        return triples.stream();
    }

    // Feature: file-hashing, Property 3: Hash mode compatibility is transitive downward
    // **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5**
    @ParameterizedTest
    @MethodSource("hashModeTriples")
    void hashModeCompatibilityIsTransitiveDownward(
            HashMode a,
            HashMode b,
            HashMode x) {
        // Only test when A.ordinal() >= B.ordinal() (A is a higher or equal mode)
        if (a.ordinal() >= b.ordinal()) {
            // If B is compatible with X, then A must also be compatible with X
            if (HashModeUtil.isCompatible(b, x)) {
                assertThat(HashModeUtil.isCompatible(a, x))
                    .as("If %s is compatible with %s, then %s (higher mode) must also be compatible with %s", b, x, a, x)
                    .isTrue();
            }
        }
    }
}
