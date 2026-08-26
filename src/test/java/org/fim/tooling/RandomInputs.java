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

package org.fim.tooling;

import java.util.Random;

/**
 * Deterministic input generation for the parameterized tests that stand in for the former
 * property-based tests.
 * <p>
 * Where a test ranges over a small domain it should enumerate that domain exhaustively with
 * {@code IntStream.rangeClosed(..)} or {@code @EnumSource} instead of using this class. This helper is
 * for the cases that genuinely need varied input: random strings, wide numeric ranges, and composite
 * objects.
 * <p>
 * Every generator is seeded from {@link #SEED}, so a failing case is always reproducible: the same run
 * produces the same inputs on every machine. That replaces the random-plus-shrinking model of a
 * property-based library with something simpler, at the cost of not automatically minimising a failing
 * input. Boundary values are therefore included explicitly by the callers rather than being discovered.
 */
public final class RandomInputs {

    /** Fixed so that a failure is reproducible. Change it deliberately to explore other inputs. */
    public static final long SEED = 20260826L;

    public static final String ALPHA = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private RandomInputs() {
        // Utility class
    }

    /** A fresh generator seeded from {@link #SEED}, so each provider method is independent. */
    public static Random random() {
        return new Random(SEED);
    }

    public static int intBetween(Random random, int minInclusive, int maxInclusive) {
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }

    public static long longBetween(Random random, long minInclusive, long maxInclusive) {
        return minInclusive + (long) (random.nextDouble() * (maxInclusive - minInclusive + 1));
    }

    /** A string of {@code minLength..maxLength} characters drawn from {@code chars}. */
    public static String stringOf(Random random, String chars, int minLength, int maxLength) {
        int length = intBetween(random, minLength, maxLength);
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(chars.charAt(random.nextInt(chars.length())));
        }
        return builder.toString();
    }
}
