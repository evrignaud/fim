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
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import org.fim.tooling.BuildableContext;
import org.fim.tooling.RandomInputs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Property-based tests for CompareResult.
 * Feature: change-detection
 */
public class CompareResultPropertyTest {

    // ---- Helpers ----

    private BuildableContext createContext() {
        BuildableContext ctx = new BuildableContext();
        ctx.setHashMode(HashMode.hashAll);
        return ctx;
    }

    private FileState createFileState(String fileName) {
        return new FileState(fileName, fileName.length(),
                new FileTime(1000000L, 1000000L),
                new FileHash("small_" + fileName, "medium_" + fileName, "full_" + fileName),
                null);
    }

    private Difference createDifference(String fileName) {
        FileState current = createFileState(fileName);
        FileState previous = createFileState(fileName);
        return new Difference(previous, current);
    }

    private Difference createAddedDifference(String fileName) {
        FileState current = createFileState(fileName);
        return new Difference(null, current);
    }

    // Feature: change-detection, Property 15: CompareResult sorting
    // **Validates: Requirements 16.6**
    @ParameterizedTest
    @MethodSource("diffCounts")
    void compareResultSorting(int count) {
        BuildableContext context = createContext();
        CompareResult result = new CompareResult(context, null);

        // Add random differences in reverse order to each list
        for (int i = count - 1; i >= 0; i--) {
            String name = String.format("file_%03d", i);
            result.getAdded().add(createAddedDifference(name));
            result.getCopied().add(createDifference("copy_" + name));
            result.getDuplicated().add(createDifference("dup_" + name));
            result.getDateModified().add(createDifference("date_" + name));
            result.getContentModified().add(createDifference("content_" + name));
            result.getAttributesModified().add(createDifference("attr_" + name));
            result.getRenamed().add(createDifference("renamed_" + name));
            result.getDeleted().add(createAddedDifference("deleted_" + name));
            result.getCorrupted().add(createDifference("corrupted_" + name));
        }

        result.sortResults();

        // Verify all lists are sorted ascending by fileName
        assertSortedByFileName(result.getAdded());
        assertSortedByFileName(result.getCopied());
        assertSortedByFileName(result.getDuplicated());
        assertSortedByFileName(result.getDateModified());
        assertSortedByFileName(result.getContentModified());
        assertSortedByFileName(result.getAttributesModified());
        assertSortedByFileName(result.getRenamed());
        assertSortedByFileName(result.getDeleted());
        assertSortedByFileName(result.getCorrupted());
    }

    private void assertSortedByFileName(List<Difference> diffs) {
        for (int i = 1; i < diffs.size(); i++) {
            String prev = diffs.get(i - 1).getFileState().getFileName();
            String curr = diffs.get(i).getFileState().getFileName();
            assertThat(prev.compareTo(curr)).isLessThanOrEqualTo(0);
        }
    }

    // Feature: change-detection, Property 16: Output truncation
    // **Validates: Requirements 17.1, 17.3**
    @ParameterizedTest
    @MethodSource("truncateParams")
    void outputTruncation(int truncateOutput) {
        BuildableContext context = createContext();
        context.setTruncateOutput(truncateOutput);

        // Create a list of differences that exceeds truncateOutput
        int totalDiffs = truncateOutput + truncateOutput; // double the truncate value
        List<Difference> differences = new ArrayList<>();
        for (int i = 0; i < totalDiffs; i++) {
            differences.add(createAddedDifference(String.format("file_%04d", i)));
        }

        // Capture output by counting how many times the consumer is called
        final int[] displayCount = {0};
        CompareResult.displayDifferences(context, "Added: ", differences, diff -> displayCount[0]++);

        int quarter = truncateOutput / 4;
        int remaining = totalDiffs - truncateOutput;

        if (remaining > quarter) {
            // Truncation should have occurred — only truncateOutput items displayed
            assertThat(displayCount[0]).isEqualTo(truncateOutput);
        } else {
            // No truncation — all items displayed
            assertThat(displayCount[0]).isEqualTo(totalDiffs);
        }
    }

    // Feature: change-detection, Property 19: getModificationCounts consistency
    // **Validates: Requirements 17.4**
    @ParameterizedTest
    @MethodSource("smallCounts")
    void getModificationCountsConsistency(
            int addedCount,
            int copiedCount,
            int duplicatedCount,
            int dateModCount,
            int contentModCount,
            int attrModCount,
            int renamedCount,
            int deletedCount) {

        BuildableContext context = createContext();
        CompareResult result = new CompareResult(context, null);

        // Populate each list with the specified count
        for (int i = 0; i < addedCount; i++) {
            result.getAdded().add(createAddedDifference("added_" + i));
        }
        for (int i = 0; i < copiedCount; i++) {
            result.getCopied().add(createDifference("copied_" + i));
        }
        for (int i = 0; i < duplicatedCount; i++) {
            result.getDuplicated().add(createDifference("dup_" + i));
        }
        for (int i = 0; i < dateModCount; i++) {
            result.getDateModified().add(createDifference("date_" + i));
        }
        for (int i = 0; i < contentModCount; i++) {
            result.getContentModified().add(createDifference("content_" + i));
        }
        for (int i = 0; i < attrModCount; i++) {
            result.getAttributesModified().add(createDifference("attr_" + i));
        }
        for (int i = 0; i < renamedCount; i++) {
            result.getRenamed().add(createDifference("renamed_" + i));
        }
        for (int i = 0; i < deletedCount; i++) {
            result.getDeleted().add(createAddedDifference("deleted_" + i));
        }

        ModificationCounts counts = result.getModificationCounts();

        assertThat(counts.getAdded()).isEqualTo(result.getAdded().size());
        assertThat(counts.getCopied()).isEqualTo(result.getCopied().size());
        assertThat(counts.getDuplicated()).isEqualTo(result.getDuplicated().size());
        assertThat(counts.getDateModified()).isEqualTo(result.getDateModified().size());
        assertThat(counts.getContentModified()).isEqualTo(result.getContentModified().size());
        assertThat(counts.getAttributesModified()).isEqualTo(result.getAttributesModified().size());
        assertThat(counts.getRenamed()).isEqualTo(result.getRenamed().size());
        assertThat(counts.getDeleted()).isEqualTo(result.getDeleted().size());
        // Note: corrupted is NOT populated by getModificationCounts() per design
    }

    // ---- Providers ----

    /** The whole 1..15 range, exhaustively. */
    static IntStream diffCounts() {
        return IntStream.rangeClosed(1, 15);
    }

    /**
     * The whole 1..20 range, exhaustively. Starting at 1 keeps the precondition the test used to state
     * with an assumption (truncateOutput >= 1) always true, without silently skipping cases.
     */
    static IntStream truncateParams() {
        return IntStream.rangeClosed(1, 20);
    }

    /**
     * Eight counts in 0..5. The full cross product is 6^8 rows, so the extremes are listed explicitly
     * and the rest is a seeded sample.
     */
    static Stream<Arguments> smallCounts() {
        List<Arguments> values = new ArrayList<>();
        values.add(Arguments.of(0, 0, 0, 0, 0, 0, 0, 0));
        values.add(Arguments.of(5, 5, 5, 5, 5, 5, 5, 5));
        Random random = RandomInputs.random();
        for (int i = 0; i < 100; i++) {
            values.add(Arguments.of(
                    RandomInputs.intBetween(random, 0, 5),
                    RandomInputs.intBetween(random, 0, 5),
                    RandomInputs.intBetween(random, 0, 5),
                    RandomInputs.intBetween(random, 0, 5),
                    RandomInputs.intBetween(random, 0, 5),
                    RandomInputs.intBetween(random, 0, 5),
                    RandomInputs.intBetween(random, 0, 5),
                    RandomInputs.intBetween(random, 0, 5)));
        }
        return values.stream();
    }
}
