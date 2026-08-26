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

package org.fim.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import org.fim.tooling.RandomInputs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Property-based tests for FileHash equality, ordering, clone, and string interning.
 * Feature: file-hashing
 */
public class FileHashPropertyTest {

    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 20;

    /** One hash component: 1 to 20 alphabetic characters. */
    private static String hashString(Random random) {
        return RandomInputs.stringOf(random, RandomInputs.ALPHA, MIN_LENGTH, MAX_LENGTH);
    }

    private static String hashStringOfLength(Random random, int length) {
        return RandomInputs.stringOf(random, RandomInputs.ALPHA, length, length);
    }

    /** Three hash components: both ends of the length range, then a seeded sample. */
    static Stream<Arguments> hashStringTriples() {
        List<Arguments> values = new ArrayList<>();
        Random random = RandomInputs.random();
        values.add(Arguments.of(hashStringOfLength(random, MIN_LENGTH),
                hashStringOfLength(random, MIN_LENGTH),
                hashStringOfLength(random, MIN_LENGTH)));
        values.add(Arguments.of(hashStringOfLength(random, MAX_LENGTH),
                hashStringOfLength(random, MAX_LENGTH),
                hashStringOfLength(random, MAX_LENGTH)));
        for (int i = 0; i < 100; i++) {
            values.add(Arguments.of(hashString(random), hashString(random), hashString(random)));
        }
        return values.stream();
    }

    /**
     * Two hash triples. Random sampling alone would essentially never produce two triples that are equal
     * or that differ in exactly one component, so those cases are listed explicitly ahead of the sample.
     */
    static Stream<Arguments> hashStringTriplePairs() {
        List<Arguments> values = new ArrayList<>();
        Random random = RandomInputs.random();

        String small = hashString(random);
        String medium = hashString(random);
        String full = hashString(random);
        values.add(Arguments.of(small, medium, full, small, medium, full));
        values.add(Arguments.of(small, medium, full, small, medium, hashString(random)));
        values.add(Arguments.of(small, medium, full, small, hashString(random), full));
        values.add(Arguments.of(small, medium, full, hashString(random), medium, full));

        values.add(Arguments.of(hashStringOfLength(random, MIN_LENGTH), hashStringOfLength(random, MIN_LENGTH),
                hashStringOfLength(random, MIN_LENGTH), hashStringOfLength(random, MIN_LENGTH),
                hashStringOfLength(random, MIN_LENGTH), hashStringOfLength(random, MIN_LENGTH)));
        values.add(Arguments.of(hashStringOfLength(random, MAX_LENGTH), hashStringOfLength(random, MAX_LENGTH),
                hashStringOfLength(random, MAX_LENGTH), hashStringOfLength(random, MAX_LENGTH),
                hashStringOfLength(random, MAX_LENGTH), hashStringOfLength(random, MAX_LENGTH)));

        for (int i = 0; i < 100; i++) {
            values.add(Arguments.of(hashString(random), hashString(random), hashString(random),
                    hashString(random), hashString(random), hashString(random)));
        }
        return values.stream();
    }

    // Feature: file-hashing, Property 9: FileHash equality compares all three fields
    // **Validates: Requirements 8.3, 8.5**
    @ParameterizedTest
    @MethodSource("hashStringTriplePairs")
    void fileHashEqualityComparesAllThreeFields(
            String s1, String m1, String f1,
            String s2, String m2, String f2) {
        FileHash hash1 = new FileHash(s1, m1, f1);
        FileHash hash2 = new FileHash(s2, m2, f2);

        boolean allFieldsEqual = s1.equals(s2) && m1.equals(m2) && f1.equals(f2);

        // equals() returns true iff all three fields match
        assertThat(hash1.equals(hash2)).isEqualTo(allFieldsEqual);

        // hashCode consistency: equal objects must have equal hashCodes
        if (hash1.equals(hash2)) {
            assertThat(hash1.hashCode()).isEqualTo(hash2.hashCode());
        }
    }

    // Feature: file-hashing, Property 10: FileHash ordering is lexicographic across three fields
    // **Validates: Requirements 8.4**
    @ParameterizedTest
    @MethodSource("hashStringTriplePairs")
    void fileHashOrderingIsLexicographic(
            String s1, String m1, String f1,
            String s2, String m2, String f2) {
        FileHash hash1 = new FileHash(s1, m1, f1);
        FileHash hash2 = new FileHash(s2, m2, f2);

        int expected;
        int smallCmp = s1.compareTo(s2);
        if (smallCmp != 0) {
            expected = smallCmp;
        } else {
            int medCmp = m1.compareTo(m2);
            if (medCmp != 0) {
                expected = medCmp;
            } else {
                expected = f1.compareTo(f2);
            }
        }

        assertThat(Integer.signum(hash1.compareTo(hash2))).isEqualTo(Integer.signum(expected));

        // Consistency with equals: compareTo returns 0 iff equals returns true (for same strings)
        if (hash1.compareTo(hash2) == 0) {
            assertThat(hash1.equals(hash2)).isTrue();
        }
    }

    // Feature: file-hashing, Property 11: FileHash clone produces equal but distinct object
    // **Validates: Requirements 8.6**
    @ParameterizedTest
    @MethodSource("hashStringTriples")
    void fileHashCloneProducesEqualButDistinctObject(String s, String m, String f) {
        FileHash original = new FileHash(s, m, f);
        FileHash cloned = original.clone();

        assertThat(cloned).isEqualTo(original);
        assertThat(cloned.hashCode()).isEqualTo(original.hashCode());
        assertThat(cloned).isNotSameAs(original);
    }

    // Feature: file-hashing, Property 12: FileHash string interning
    // **Validates: Requirements 8.2**
    @ParameterizedTest
    @MethodSource("hashStringTriples")
    void fileHashStringInterning(String s, String m, String f) {
        // Create new String instances to ensure they are NOT already interned
        String sCopy = new String(s);
        String mCopy = new String(m);
        String fCopy = new String(f);

        FileHash fileHash = new FileHash(sCopy, mCopy, fCopy);

        // Stored strings should be reference-equal to their intern() result
        assertThat(fileHash.getSmallBlockHash()).isSameAs(fileHash.getSmallBlockHash().intern());
        assertThat(fileHash.getMediumBlockHash()).isSameAs(fileHash.getMediumBlockHash().intern());
        assertThat(fileHash.getFullHash()).isSameAs(fileHash.getFullHash().intern());
    }
}
