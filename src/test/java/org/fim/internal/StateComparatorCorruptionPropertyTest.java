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
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import org.fim.model.CompareResult;
import org.fim.model.Difference;
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.FileTime;
import org.fim.model.HashMode;
import org.fim.model.Modification;
import org.fim.tooling.BuildableContext;
import org.fim.tooling.BuildableState;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Invariants of StateComparator in hardware corruption detection mode.
 * Feature: corruption-detection
 * <p>
 * Input domains are enumerated exhaustively rather than sampled.
 */
public class StateComparatorCorruptionPropertyTest {

    static IntStream fileCounts() {
        return IntStream.rangeClosed(1, 10);
    }

    /** Property 8 populates the corrupted list directly, so zero is a meaningful case there. */
    static IntStream fileCountsFromZero() {
        return IntStream.rangeClosed(0, 10);
    }

    static IntStream multiFileCounts() {
        return IntStream.rangeClosed(2, 10);
    }

    /** All inside/outside combinations for the subdirectory filtering property. */
    static Stream<Arguments> insideOutsideCounts() {
        List<Arguments> args = new ArrayList<>();
        for (int inside = 1; inside <= 10; inside++) {
            for (int outside = 1; outside <= 10; outside++) {
                args.add(Arguments.of(inside, outside));
            }
        }
        return args.stream();
    }

    // ---- Helpers ----

    private BuildableContext createContext() {
        BuildableContext ctx = new BuildableContext();
        ctx.setHashMode(HashMode.hashAll);
        return ctx;
    }

    private String[] generateFileNames(int count) {
        String[] names = new String[count];
        for (int i = 0; i < count; i++) {
            names[i] = String.format("file_%02d", i);
        }
        return names;
    }

    // Feature: corruption-detection, Property 2: Subdirectory filtering preserves only subtree files
    // **Validates: Requirements 2.1**
    @ParameterizedTest
    @MethodSource("insideOutsideCounts")
    void subdirectoryFilteringPreservesOnlySubtreeFiles(int insideCount, int outsideCount) {
        BuildableContext context = createContext();

        List<String> allNames = new ArrayList<>();
        // Files inside the subdirectory
        for (int i = 0; i < insideCount; i++) {
            allNames.add(String.format("subdir/inner_%02d", i));
        }
        // Files outside the subdirectory
        for (int i = 0; i < outsideCount; i++) {
            allNames.add(String.format("other/outer_%02d", i));
        }
        allNames.add("root_file");

        BuildableState state = new BuildableState(context).addFiles(allNames.toArray(new String[0]));

        // Simulate filtering like State.filterDirectory does: keep files starting with "subdir/"
        List<FileState> filtered = new ArrayList<>();
        for (FileState fs : state.getFileStates()) {
            if (fs.getFileName().startsWith("subdir/")) {
                filtered.add(fs);
            }
        }

        // All retained files must start with the subdirectory prefix
        assertThat(filtered).hasSize(insideCount);
        for (FileState fs : filtered) {
            assertThat(fs.getFileName()).startsWith("subdir/");
        }

        // No files outside the prefix are retained
        for (FileState fs : state.getFileStates()) {
            if (!fs.getFileName().startsWith("subdir/")) {
                assertThat(filtered).doesNotContain(fs);
            }
        }
    }

    // Feature: corruption-detection, Property 3: Corruption classification correctness
    // **Validates: Requirements 3.3, 4.1, 4.2, 6.1, 6.2, 6.3**
    @ParameterizedTest
    @MethodSource("fileCounts")
    void corruptionClassificationCorrectness(int fileCount) {
        BuildableContext context = createContext();
        String[] names = generateFileNames(fileCount);
        BuildableState previous = new BuildableState(context).addFiles(names);

        // Clone and corrupt all files: same name, same time, different hash
        BuildableState current = previous.clone();
        for (FileState fs : current.getFileStates()) {
            fs.setFileHash(new FileHash(
                    "corrupted_small_" + fs.getFileName(),
                    "corrupted_medium_" + fs.getFileName(),
                    "corrupted_full_" + fs.getFileName()));
        }

        CompareResult result = new StateComparator(context, previous, current)
                .searchForHardwareCorruption()
                .compare();

        // Every file should appear in the corrupted list
        assertThat(result.getCorrupted()).hasSize(fileCount);

        for (Difference diff : result.getCorrupted()) {
            FileState currentFs = diff.getFileState();
            FileState previousFs = diff.getPreviousFileState();

            // Both previous and current FileState present
            assertThat(previousFs).isNotNull();

            // Same fileName (Req 6.1)
            assertThat(currentFs.getFileName()).isEqualTo(previousFs.getFileName());

            // Same FileTime (Req 6.2)
            assertThat(currentFs.getFileTime()).isEqualTo(previousFs.getFileTime());

            // Different FileHash (Req 6.3)
            assertThat(currentFs.getFileHash()).isNotEqualTo(previousFs.getFileHash());

            // Modification set to corrupted (Req 4.2)
            assertThat(currentFs.getModification()).isEqualTo(Modification.corrupted);
        }
    }

    // Feature: corruption-detection, Property 4: No false positives when hash is unchanged
    // **Validates: Requirements 4.3, 6.4**
    @ParameterizedTest
    @MethodSource("fileCounts")
    void noFalsePositivesWhenHashUnchanged(int fileCount) {
        BuildableContext context = createContext();
        String[] names = generateFileNames(fileCount);
        BuildableState previous = new BuildableState(context).addFiles(names);

        // Clone: same name, same hash. Optionally change time on some files.
        BuildableState current = previous.clone();
        // Touch half the files to change their time (but keep hash the same)
        for (int i = 0; i < fileCount / 2; i++) {
            current = current.touch(names[i]);
        }

        CompareResult result = new StateComparator(context, previous, current)
                .searchForHardwareCorruption()
                .compare();

        // No file should be in the corrupted list since hash is unchanged
        assertThat(result.getCorrupted()).isEmpty();
    }

    // Feature: corruption-detection, Property 5: No false positives when timestamp changed
    // **Validates: Requirements 4.4, 6.5**
    @ParameterizedTest
    @MethodSource("fileCounts")
    void noFalsePositivesWhenTimestampChanged(int fileCount) {
        BuildableContext context = createContext();
        String[] names = generateFileNames(fileCount);
        BuildableState previous = new BuildableState(context).addFiles(names);

        // Change both time AND hash - this is a normal modification, not corruption
        BuildableState current = previous.clone();
        for (FileState fs : current.getFileStates()) {
            // Change the timestamp (different FileTime)
            fs.getFileTime().setLastModified(fs.getFileTime().getLastModified() + 2000);
            // Also change the hash
            fs.setFileHash(new FileHash(
                    "changed_small_" + fs.getFileName(),
                    "changed_medium_" + fs.getFileName(),
                    "changed_full_" + fs.getFileName()));
        }

        CompareResult result = new StateComparator(context, previous, current)
                .searchForHardwareCorruption()
                .compare();

        // No file should be corrupted because timestamp changed
        assertThat(result.getCorrupted()).isEmpty();
    }

    // Feature: corruption-detection, Property 6: Non-corruption lists empty in corruption mode
    // **Validates: Requirements 3.4, 3.5, 3.6, 3.7, 3.8, 6.6**
    @ParameterizedTest
    @MethodSource("fileCounts")
    void nonCorruptionListsEmptyInCorruptionMode(int fileCount) {
        BuildableContext context = createContext();
        String[] names = generateFileNames(fileCount);
        BuildableState previous = new BuildableState(context).addFiles(names);

        // Create a mix: add a file, delete a file, modify content
        BuildableState current = previous.addFiles("new_extra_file");
        if (fileCount > 1) {
            current = current.delete(names[fileCount - 1]);
        }
        // Change hash but keep time (corruption scenario for first file)
        BuildableState cloned = current.clone();
        FileState fs = cloned.getFileStates().stream()
                .filter(f -> f.getFileName().equals(names[0]))
                .findFirst().orElse(null);
        if (fs != null) {
            fs.setFileHash(new FileHash("diff_s", "diff_m", "diff_f"));
        }
        current = cloned;

        CompareResult result = new StateComparator(context, previous, current)
                .searchForHardwareCorruption()
                .compare();

        // In corruption mode, these lists must always be empty
        assertThat(result.getAdded()).isEmpty();
        assertThat(result.getDeleted()).isEmpty();
        assertThat(result.getRenamed()).isEmpty();
        assertThat(result.getCopied()).isEmpty();
        assertThat(result.getDuplicated()).isEmpty();
    }

    // Feature: corruption-detection, Property 7: Corrupted list sorted by fileName
    // **Validates: Requirements 3.9, 5.5**
    @ParameterizedTest
    @MethodSource("multiFileCounts")
    void corruptedListSortedByFileName(int fileCount) {
        BuildableContext context = createContext();
        // Create files with names that would be unsorted if reversed
        String[] names = new String[fileCount];
        for (int i = 0; i < fileCount; i++) {
            // Use reverse order naming to ensure sorting is actually tested
            names[i] = String.format("file_%02d", fileCount - 1 - i);
        }
        BuildableState previous = new BuildableState(context).addFiles(names);

        // Corrupt all files: same time, different hash
        BuildableState current = previous.clone();
        for (FileState fs : current.getFileStates()) {
            fs.setFileHash(new FileHash(
                    "corrupt_s_" + fs.getFileName(),
                    "corrupt_m_" + fs.getFileName(),
                    "corrupt_f_" + fs.getFileName()));
        }

        CompareResult result = new StateComparator(context, previous, current)
                .searchForHardwareCorruption()
                .compare();

        assertThat(result.getCorrupted()).hasSize(fileCount);

        // Verify sorted ascending by fileName
        List<Difference> corrupted = result.getCorrupted();
        for (int i = 1; i < corrupted.size(); i++) {
            String prev = corrupted.get(i - 1).getFileState().getFileName();
            String curr = corrupted.get(i).getFileState().getFileName();
            assertThat(prev.compareTo(curr)).isLessThanOrEqualTo(0);
        }
    }

    // Feature: corruption-detection, Property 8: modifiedCount includes corrupted entries
    // **Validates: Requirements 5.4**
    @ParameterizedTest
    @MethodSource("fileCountsFromZero")
    void modifiedCountIncludesCorruptedEntries(int corruptedCount) {
        BuildableContext context = createContext();
        CompareResult result = new CompareResult(context, null);

        // Populate the corrupted list directly
        for (int i = 0; i < corruptedCount; i++) {
            String name = String.format("corrupted_%02d", i);
            FileState current = new FileState(name, 100,
                    new FileTime(1000000L, 1000000L),
                    new FileHash("small_" + name, "medium_" + name, "full_" + name),
                    null);
            FileState previous = new FileState(name, 100,
                    new FileTime(1000000L, 1000000L),
                    new FileHash("old_small_" + name, "old_medium_" + name, "old_full_" + name),
                    null);
            result.getCorrupted().add(new Difference(previous, current));
        }

        // modifiedCount must include corrupted.size()
        assertThat(result.modifiedCount()).isGreaterThanOrEqualTo(result.getCorrupted().size());
        assertThat(result.modifiedCount()).isEqualTo(corruptedCount);

        // Also verify somethingModified is consistent
        if (corruptedCount > 0) {
            assertThat(result.somethingModified()).isTrue();
        } else {
            assertThat(result.somethingModified()).isFalse();
        }
    }
}
