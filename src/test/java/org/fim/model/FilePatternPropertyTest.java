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

import org.fim.tooling.RandomInputs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for FilePattern.
 * Feature: file-ignore-system
 */
public class FilePatternPropertyTest {

    // Feature: file-ignore-system, Property 7: FilePattern identity matching
    // **Validates: Requirements 3.6**
    @ParameterizedTest
    @MethodSource("alphaNames")
    void identityMatching(String name) {
        // Any string without wildcards should match itself
        FilePattern pattern = new FilePattern(name);
        assertThat(pattern.match(name)).isTrue();
    }

    // Feature: file-ignore-system, Property 6: Glob-to-regex compilation correctness
    // **Validates: Requirements 3.1, 3.2, 3.3**
    @ParameterizedTest
    @MethodSource("globPatterns")
    void globToRegexCompilationCorrectness(String glob) {
        FilePattern fp = new FilePattern(glob);
        Pattern compiled = fp.getCompiled();

        if (compiled != null) {
            String regex = compiled.toString();

            // (c) Anchored with ^ at start and $ at end
            assertThat(regex).startsWith("^");
            assertThat(regex).endsWith("$");

            // Strip anchors for inner checks
            String inner = regex.substring(1, regex.length() - 1);

            // (a) Regex special chars should be escaped in the output
            // Check that literal special chars from the original glob are escaped in the regex
            char[] specialChars = {'\\', '^', '$', '{', '}', '[', ']', '(', ')', '.', '|', '?'};
            for (char c : specialChars) {
                String charStr = String.valueOf(c);
                if (glob.contains(charStr)) {
                    // The char should appear escaped (preceded by \) in the inner regex
                    assertThat(inner).contains("\\" + c);
                }
            }

            // (b) Multiple consecutive * should be collapsed — no ".*.*" in the regex
            assertThat(inner).doesNotContain(".*.*");
        }
    }

    // ---- Providers ----

    /** Alphabetic names of 1 to 50 characters: both ends of the length range, then a seeded sample. */
    static Stream<String> alphaNames() {
        List<String> values = new ArrayList<>();
        Random random = RandomInputs.random();
        values.add(RandomInputs.stringOf(random, RandomInputs.ALPHA, 1, 1));
        values.add(RandomInputs.stringOf(random, RandomInputs.ALPHA, 50, 50));
        for (int i = 0; i < 100; i++) {
            values.add(RandomInputs.stringOf(random, RandomInputs.ALPHA, 1, 50));
        }
        return values.stream();
    }

    static Stream<String> globPatterns() {
        // Generate glob patterns mixing alphanumeric chars, wildcards, and special chars
        String chars = "abcABC123*.$[]{}()|?^\\";
        List<String> values = new ArrayList<>();
        Random random = RandomInputs.random();
        values.add(RandomInputs.stringOf(random, chars, 1, 1));
        values.add(RandomInputs.stringOf(random, chars, 30, 30));
        // Shapes worth pinning: a bare wildcard, consecutive wildcards, and every special char at once.
        values.add("*");
        values.add("***");
        values.add("a**b");
        values.add(".$[]{}()|?^\\");
        for (int i = 0; i < 100; i++) {
            values.add(RandomInputs.stringOf(random, chars, 1, 30));
        }
        return values.stream();
    }
}
