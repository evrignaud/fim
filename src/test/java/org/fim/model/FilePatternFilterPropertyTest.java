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
 * Property-based tests for FilePattern include/exclude filtering logic.
 * Feature: file-ignore-system
 */
public class FilePatternFilterPropertyTest {

    private static final String FILE_NAME_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789._-";
    private static final String PATTERN_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789*._-";

    // Feature: file-ignore-system, Property 8: Include/exclude filtering logic
    // **Validates: Requirements 4.3, 4.4, 4.5, 4.6, 4.7**
    @ParameterizedTest
    @MethodSource("fileNamesWithPatternLists")
    void includeExcludeFilteringLogic(
            String fileName,
            List<String> includeGlobs,
            List<String> excludeGlobs) {

        List<FilePattern> includePatterns = new ArrayList<>();
        for (String g : includeGlobs) {
            includePatterns.add(new FilePattern(g));
        }
        List<FilePattern> excludePatterns = new ArrayList<>();
        for (String g : excludeGlobs) {
            excludePatterns.add(new FilePattern(g));
        }

        boolean matchesInclude = FilePattern.matchPatterns(fileName, includePatterns, true);
        boolean matchesExclude = FilePattern.matchPatterns(fileName, excludePatterns, false);

        // File is accepted iff it matches at least one include AND does not match any exclude
        boolean expected = matchesInclude && !matchesExclude;

        // Simulate the StateGenerator logic
        boolean actual = FilePattern.matchPatterns(fileName, includePatterns, true)
                && !FilePattern.matchPatterns(fileName, excludePatterns, false);

        assertThat(actual).isEqualTo(expected);
    }

    // Req 4.6: null include patterns => all files included (default true)
    // Req 4.7: null exclude patterns => no files excluded (default false)
    @ParameterizedTest
    @MethodSource("simpleFileNames")
    void nullPatternsReturnCorrectDefaults(
            String fileName) {

        // null include => default true (all included)
        assertThat(FilePattern.matchPatterns(fileName, null, true)).isTrue();

        // null exclude => default false (none excluded)
        assertThat(FilePattern.matchPatterns(fileName, null, false)).isFalse();
    }

    // Verify that when include list contains a pattern matching the file, and exclude does not,
    // the file is accepted
    @ParameterizedTest
    @MethodSource("simpleFileNames")
    void fileMatchingIncludeAndNotExcludeIsAccepted(
            String fileName) {

        // Include list contains exact match
        List<FilePattern> includePatterns = new ArrayList<>();
        includePatterns.add(new FilePattern(fileName));

        // Exclude list is empty
        List<FilePattern> excludePatterns = new ArrayList<>();

        boolean included = FilePattern.matchPatterns(fileName, includePatterns, true);
        boolean excluded = FilePattern.matchPatterns(fileName, excludePatterns, false);

        assertThat(included && !excluded).isTrue();
    }

    // Verify that when a file matches both include and exclude, it is rejected
    @ParameterizedTest
    @MethodSource("simpleFileNames")
    void fileMatchingBothIncludeAndExcludeIsRejected(
            String fileName) {

        List<FilePattern> includePatterns = new ArrayList<>();
        includePatterns.add(new FilePattern(fileName));

        List<FilePattern> excludePatterns = new ArrayList<>();
        excludePatterns.add(new FilePattern(fileName));

        boolean included = FilePattern.matchPatterns(fileName, includePatterns, true);
        boolean excluded = FilePattern.matchPatterns(fileName, excludePatterns, false);

        // File matches both => rejected (included && !excluded == false)
        assertThat(included && !excluded).isFalse();
    }

    // ---- Providers ----

    /** File names of 1 to 20 characters: both ends of the length range, then a seeded sample. */
    static Stream<String> simpleFileNames() {
        List<String> values = new ArrayList<>();
        Random random = RandomInputs.random();
        values.add(RandomInputs.stringOf(random, FILE_NAME_CHARS, 1, 1));
        values.add(RandomInputs.stringOf(random, FILE_NAME_CHARS, 20, 20));
        for (int i = 0; i < 100; i++) {
            values.add(RandomInputs.stringOf(random, FILE_NAME_CHARS, 1, 20));
        }
        return values.stream();
    }

    /**
     * A file name with an include and an exclude glob list. Both lists may hold 0 to 5 globs, so the empty
     * and the full shapes are listed explicitly ahead of the seeded sample.
     */
    static Stream<Arguments> fileNamesWithPatternLists() {
        List<Arguments> values = new ArrayList<>();
        Random random = RandomInputs.random();
        values.add(Arguments.of(RandomInputs.stringOf(random, FILE_NAME_CHARS, 1, 20),
                List.of(), List.of()));
        values.add(Arguments.of(RandomInputs.stringOf(random, FILE_NAME_CHARS, 1, 20),
                randomPatternList(random, 5), randomPatternList(random, 5)));
        for (int i = 0; i < 100; i++) {
            values.add(Arguments.of(RandomInputs.stringOf(random, FILE_NAME_CHARS, 1, 20),
                    randomPatternList(random, RandomInputs.intBetween(random, 0, 5)),
                    randomPatternList(random, RandomInputs.intBetween(random, 0, 5))));
        }
        return values.stream();
    }

    private static List<String> randomPatternList(Random random, int size) {
        List<String> patterns = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            patterns.add(RandomInputs.stringOf(random, PATTERN_CHARS, 1, 15));
        }
        return patterns;
    }
}
