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
 * Property-based tests for DuplicateSet and DuplicateResult.
 * Feature: duplicate-detection
 */
public class DuplicateResultPropertyTest {

    // ---- Helpers ----

    private FileState makeFileState(String fileName, long fileLength, String hashSuffix) {
        FileHash hash = new FileHash("small_" + hashSuffix, "medium_" + hashSuffix, "full_" + hashSuffix);
        return new FileState(fileName, fileLength, new FileTime(System.currentTimeMillis()), hash, null);
    }

    private List<FileState> makeDuplicateGroup(int count, long fileSize, String hashSuffix) {
        List<FileState> files = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            files.add(makeFileState("file_" + hashSuffix + "_" + i + ".txt", fileSize, hashSuffix));
        }
        return files;
    }

    // Feature: duplicate-detection, Property 4: Wasted space calculation per set
    // **Validates: Requirements 3.1, 12.7**
    @ParameterizedTest
    @MethodSource("fileCountsAndSizes")
    void wastedSpaceCalculationPerSet(int fileCount, long fileSize) {
        String hashSuffix = "ws_test";
        List<FileState> files = makeDuplicateGroup(fileCount, fileSize, hashSuffix);
        DuplicateSet ds = new DuplicateSet(files);

        assertThat(ds.getWastedSpace())
                .isEqualTo((long) (ds.getDuplicatedFilesCount() - 1) * ds.getDuplicatedFileSize());
    }

    // Feature: duplicate-detection, Property 5: Aggregate statistics consistency
    // **Validates: Requirements 3.2, 3.3, 12.4, 12.5**
    @ParameterizedTest
    @MethodSource("groupSpecs")
    void aggregateStatisticsConsistency(List<int[]> groupSpecs) {
        Context context = new Context();
        DuplicateResult result = new DuplicateResult(context);

        for (int i = 0; i < groupSpecs.size(); i++) {
            int count = groupSpecs.get(i)[0];
            int size = groupSpecs.get(i)[1];
            List<FileState> files = makeDuplicateGroup(count, size, "agg_" + i);
            result.addDuplicatedFiles(files);
        }

        long expectedDuplicatedCount = 0;
        long expectedWastedSpace = 0;
        for (DuplicateSet ds : result.getDuplicateSets()) {
            expectedDuplicatedCount += ds.getDuplicatedFilesCount() - 1;
            expectedWastedSpace += ds.getWastedSpace();
        }

        assertThat(result.getDuplicatedFilesCount()).isEqualTo(expectedDuplicatedCount);
        assertThat(result.getTotalWastedSpace()).isEqualTo(expectedWastedSpace);
    }

    // Feature: duplicate-detection, Property 6: Sort correctness
    // **Validates: Requirements 2.8, 4.1, 4.2, 4.3, 4.4, 4.5**
    @ParameterizedTest
    @MethodSource("sortScenarios")
    void sortCorrectness(List<int[]> groupSpecs, SortMethod sortMethod, boolean ascending) {
        Context context = new Context();
        context.setSortMethod(sortMethod);
        context.setSortAscending(ascending);
        DuplicateResult result = new DuplicateResult(context);

        for (int i = 0; i < groupSpecs.size(); i++) {
            int count = groupSpecs.get(i)[0];
            int size = groupSpecs.get(i)[1];
            List<FileState> files = makeDuplicateGroup(count, size, "sort_" + i);
            result.addDuplicatedFiles(files);
        }

        result.sortDuplicateSets();

        List<DuplicateSet> sets = result.getDuplicateSets();
        for (int i = 0; i < sets.size() - 1; i++) {
            DuplicateSet current = sets.get(i);
            DuplicateSet next = sets.get(i + 1);

            long currentVal = extractSortValue(current, sortMethod);
            long nextVal = extractSortValue(next, sortMethod);

            if (ascending) {
                assertThat(currentVal).isLessThanOrEqualTo(nextVal);
            } else {
                assertThat(currentVal).isGreaterThanOrEqualTo(nextVal);
            }
        }
    }

    private long extractSortValue(DuplicateSet ds, SortMethod method) {
        return switch (method) {
            case wasted -> ds.getWastedSpace();
            case number -> ds.getDuplicatedFilesCount();
            case size -> ds.getDuplicatedFileSize();
        };
    }

    // Feature: duplicate-detection, Property 13: AlwaysYes marks all except first for removal
    // **Validates: Requirements 8.3**
    @ParameterizedTest
    @MethodSource("fileCountsAndSizes")
    void alwaysYesMarksAllExceptFirst(int fileCount, long fileSize) {
        Context context = new Context();
        context.setAlwaysYes(true);
        context.setRemoveDuplicates(true);
        DuplicateResult result = new DuplicateResult(context);

        List<FileState> files = makeDuplicateGroup(fileCount, fileSize, "ay_test");
        result.selectFilesToRemove(files);

        // First file should NOT be marked for removal
        assertThat(files.get(0).isToRemove()).isFalse();

        // All other files should be marked for removal
        for (int i = 1; i < files.size(); i++) {
            assertThat(files.get(i).isToRemove()).isTrue();
        }
    }

    // Feature: duplicate-detection, Property 14: Preserve by index
    // **Validates: Requirements 8.5, 8.6**
    @ParameterizedTest
    @MethodSource("preserveScenarios")
    void preserveByIndex(int[] scenario) {
        int fileCount = scenario[0];
        int preserveIndex = scenario[1]; // 1-based index

        Context context = new Context();
        DuplicateResult result = new DuplicateResult(context);

        List<FileState> files = makeDuplicateGroup(fileCount, 100, "pbi_test");
        result.manageAnswers(files, String.valueOf(preserveIndex));

        // The preserved file should have toRemove=false
        assertThat(files.get(preserveIndex - 1).isToRemove()).isFalse();

        // All other files should have toRemove=true
        for (int i = 0; i < files.size(); i++) {
            if (i != preserveIndex - 1) {
                assertThat(files.get(i).isToRemove()).isTrue();
            }
        }
    }

    // Feature: duplicate-detection, Property 15: "all" preserves all files
    // **Validates: Requirements 8.7**
    @ParameterizedTest
    @MethodSource("fileCountsAndAllInputs")
    void allPreservesAllFiles(int fileCount, String input) {
        Context context = new Context();
        DuplicateResult result = new DuplicateResult(context);

        List<FileState> files = makeDuplicateGroup(fileCount, 100, "all_test");
        result.manageAnswers(files, input);

        for (FileState fs : files) {
            assertThat(fs.isToRemove()).isFalse();
        }
    }

    // Feature: duplicate-detection, Property 16: "none" removes all files
    // **Validates: Requirements 8.8**
    @ParameterizedTest
    @MethodSource("fileCountsAndNoneInputs")
    void noneRemovesAllFiles(int fileCount, String input) {
        Context context = new Context();
        DuplicateResult result = new DuplicateResult(context);

        List<FileState> files = makeDuplicateGroup(fileCount, 100, "none_test");
        result.manageAnswers(files, input);

        for (FileState fs : files) {
            assertThat(fs.isToRemove()).isTrue();
        }
    }

    // ---- Providers ----

    /**
     * File counts in 2..8 paired with file sizes in 1..100000. Every count is paired with both ends of the
     * size range, then the pair is sampled with a fixed seed.
     */
    static Stream<Arguments> fileCountsAndSizes() {
        List<Arguments> values = new ArrayList<>();
        for (int fileCount = 2; fileCount <= 8; fileCount++) {
            values.add(Arguments.of(fileCount, 1L));
            values.add(Arguments.of(fileCount, 100_000L));
        }
        Random random = RandomInputs.random();
        for (int i = 0; i < 100; i++) {
            values.add(Arguments.of(RandomInputs.intBetween(random, 2, 8),
                    RandomInputs.longBetween(random, 1L, 100_000L)));
        }
        return values.stream();
    }

    /**
     * Groups of {@code {fileCount, fileSize}}, 1 to 5 groups per list, file counts in 2..6 and sizes in
     * 1..10000. The smallest and largest shapes are listed explicitly, the rest is a seeded sample.
     */
    static Stream<List<int[]>> groupSpecs() {
        List<List<int[]>> values = new ArrayList<>();
        values.add(List.of(new int[] {2, 1}));
        values.add(List.of(new int[] {6, 10_000}, new int[] {6, 10_000}, new int[] {6, 10_000},
                new int[] {6, 10_000}, new int[] {6, 10_000}));
        Random random = RandomInputs.random();
        for (int i = 0; i < 100; i++) {
            values.add(randomGroupSpecs(random));
        }
        return values.stream();
    }

    /**
     * Every sort method crossed with both sort directions, each combination given its own set of sampled
     * group shapes.
     */
    static Stream<Arguments> sortScenarios() {
        List<Arguments> values = new ArrayList<>();
        Random random = RandomInputs.random();
        for (SortMethod sortMethod : SortMethod.values()) {
            for (boolean ascending : new boolean[] {true, false}) {
                for (int i = 0; i < 17; i++) {
                    values.add(Arguments.of(randomGroupSpecs(random), sortMethod, ascending));
                }
            }
        }
        return values.stream();
    }

    private static List<int[]> randomGroupSpecs(Random random) {
        int groupCount = RandomInputs.intBetween(random, 1, 5);
        List<int[]> groups = new ArrayList<>();
        for (int g = 0; g < groupCount; g++) {
            int count = RandomInputs.intBetween(random, 2, 6);
            int size = RandomInputs.intBetween(random, 1, 10_000);
            groups.add(new int[] {count, size});
        }
        return groups;
    }

    /** Every {@code {fileCount, preserveIndex}} pair for counts 2..8, exhaustively. */
    static Stream<Arguments> preserveScenarios() {
        List<Arguments> values = new ArrayList<>();
        for (int fileCount = 2; fileCount <= 8; fileCount++) {
            for (int preserveIndex = 1; preserveIndex <= fileCount; preserveIndex++) {
                values.add(Arguments.of(new int[] {fileCount, preserveIndex}));
            }
        }
        return values.stream();
    }

    /** File counts 2..8 crossed with both spellings of the "keep everything" answer. */
    static Stream<Arguments> fileCountsAndAllInputs() {
        return fileCountsCrossedWith("all", "a");
    }

    /** File counts 2..8 crossed with both spellings of the "keep nothing" answer. */
    static Stream<Arguments> fileCountsAndNoneInputs() {
        return fileCountsCrossedWith("none", "n");
    }

    private static Stream<Arguments> fileCountsCrossedWith(String... inputs) {
        List<Arguments> values = new ArrayList<>();
        for (int fileCount = 2; fileCount <= 8; fileCount++) {
            for (String input : inputs) {
                values.add(Arguments.of(fileCount, input));
            }
        }
        return values.stream();
    }
}
