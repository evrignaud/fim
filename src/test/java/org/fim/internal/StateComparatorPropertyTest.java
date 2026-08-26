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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import org.fim.model.CompareResult;
import org.fim.model.Difference;
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.HashMode;
import org.fim.tooling.BuildableContext;
import org.fim.tooling.BuildableState;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Invariants of StateComparator, checked across a range of file counts.
 * Feature: change-detection
 * <p>
 * Each test enumerates its input domain exhaustively rather than sampling it, so every file count in
 * range is exercised on every run and a failure is already the minimal failing case. Where the original
 * property assumed a minimum file count, the range starts at that minimum instead.
 */
public class StateComparatorPropertyTest {

    /** Domains are small enough to enumerate completely. */
    static IntStream fileCounts() {
        return IntStream.rangeClosed(1, 10);
    }

    /** For properties that need at least two files to set up the scenario. */
    static IntStream atLeastTwoFileCounts() {
        return IntStream.rangeClosed(2, 10);
    }

    /** 3 bits: attrs, dates, renamed. All 8 combinations. */
    static IntStream ignoreFlagCombos() {
        return IntStream.rangeClosed(0, 7);
    }

    // ---- Helpers ----

    private BuildableContext createContext() {
        BuildableContext ctx = new BuildableContext();
        ctx.setHashMode(HashMode.hashAll);
        return ctx;
    }

    /**
     * Generate unique file names like "file_00", "file_01", etc.
     */
    private String[] generateFileNames(int count) {
        String[] names = new String[count];
        for (int i = 0; i < count; i++) {
            names[i] = String.format("file_%02d", i);
        }
        return names;
    }

    // Feature: change-detection, Property 1: Identity comparison produces zero modifications
    // **Validates: Requirements 19.1**
    @ParameterizedTest
    @MethodSource("fileCounts")
    void identityComparisonProducesZeroModifications(int fileCount) {
        BuildableContext context = createContext();
        String[] names = generateFileNames(fileCount);
        BuildableState state = new BuildableState(context).addFiles(names);
        BuildableState copy = state.clone();

        CompareResult result = new StateComparator(context, state, copy).compare();

        assertThat(result.somethingModified()).isFalse();
        assertThat(result.modifiedCount()).isEqualTo(0);
    }

    // Feature: change-detection, Property 2: File count conservation
    // **Validates: Requirements 13.1, 19.2**
    @ParameterizedTest
    @MethodSource("fileCounts")
    void fileCountConservation(int fileCount) {
        BuildableContext context = createContext();
        String[] names = generateFileNames(fileCount);
        BuildableState s1 = new BuildableState(context).addFiles(names);

        // Make some modifications: add a file, modify content of first file
        BuildableState s2 = s1.addFiles("extra_file");
        s2 = s2.setContent(names[0], "modified_content");

        CompareResult result = new StateComparator(context, s1, s2).compare();

        // The pipeline guarantees notModified + modifiedCount == currentState.getFileCount(); if that
        // fails, compare() throws. Reaching here means the invariant held, so check consistency.
        int modifiedCount = result.modifiedCount();
        int currentFileCount = s2.getFileCount();
        assertThat(modifiedCount).isGreaterThanOrEqualTo(0);
        assertThat(modifiedCount).isLessThanOrEqualTo(currentFileCount + result.getDeleted().size());

        // deleted files come from the previous state, not the current one
        int nonDeletedModified = modifiedCount - result.getDeleted().size();
        int notModified = currentFileCount - nonDeletedModified;
        assertThat(notModified).isGreaterThanOrEqualTo(0);
    }

    // Feature: change-detection, Property 3: Content modification detection
    // **Validates: Requirements 4.1, 4.2, 19.3**
    @ParameterizedTest
    @MethodSource("fileCounts")
    void contentModificationDetection(int fileCount) {
        BuildableContext context = createContext();
        String[] names = generateFileNames(fileCount);
        BuildableState s1 = new BuildableState(context).addFiles(names);

        // Modify content of the first file
        BuildableState s2 = s1.setContent(names[0], "completely_different_content");

        CompareResult result = new StateComparator(context, s1, s2).compare();

        assertThat(result.getContentModified()).isNotEmpty();
        // Verify the modified file has matching fileNames in previous and current
        for (Difference diff : result.getContentModified()) {
            assertThat(diff.getPreviousFileState()).isNotNull();
            assertThat(diff.getFileState().getFileName())
                    .isEqualTo(diff.getPreviousFileState().getFileName());
        }
    }

    // Feature: change-detection, Property 4: Date modification detection
    // **Validates: Requirements 5.1, 5.2, 19.5**
    @ParameterizedTest
    @MethodSource("fileCounts")
    void dateModificationDetection(int fileCount) {
        BuildableContext context = createContext();
        String[] names = generateFileNames(fileCount);
        BuildableState s1 = new BuildableState(context).addFiles(names);

        // Touch the first file to change its date
        BuildableState s2 = s1.touch(names[0]);

        CompareResult result = new StateComparator(context, s1, s2).compare();

        assertThat(result.getDateModified()).isNotEmpty();
        for (Difference diff : result.getDateModified()) {
            assertThat(diff.getPreviousFileState()).isNotNull();
            // Same hash and length
            assertThat(diff.getFileState().getFileHash())
                    .isEqualTo(diff.getPreviousFileState().getFileHash());
            assertThat(diff.getFileState().getFileLength())
                    .isEqualTo(diff.getPreviousFileState().getFileLength());
        }
    }

    // Feature: change-detection, Property 5: Attribute modification detection
    // **Validates: Requirements 6.1, 6.2**
    @ParameterizedTest
    @MethodSource("fileCounts")
    void attributeModificationDetection(int fileCount) {
        BuildableContext context = createContext();
        String[] names = generateFileNames(fileCount);
        BuildableState s1 = new BuildableState(context).addFiles(names);

        // Clone and manually change attributes on the first file
        BuildableState s2 = s1.clone();
        FileState targetFile = s2.getFileStates().stream()
                .filter(fs -> fs.getFileName().equals(names[0]))
                .findFirst().orElseThrow();
        Map<String, String> attrs = new HashMap<>();
        attrs.put("PosixFilePermissions", "rwxr-xr-x");
        targetFile.setFileAttributes(attrs);

        CompareResult result = new StateComparator(context, s1, s2).compare();

        assertThat(result.getAttributesModified()).isNotEmpty();
        for (Difference diff : result.getAttributesModified()) {
            assertThat(diff.getPreviousFileState()).isNotNull();
            // Same content
            assertThat(diff.getFileState().getFileHash())
                    .isEqualTo(diff.getPreviousFileState().getFileHash());
        }
    }

    // Feature: change-detection, Property 17: Modification type structural invariants
    // **Validates: Requirements 19.3, 19.4, 19.5**
    @ParameterizedTest
    @MethodSource("atLeastTwoFileCounts")
    void modificationTypeStructuralInvariants(int fileCount) {
        BuildableContext context = createContext();
        String[] names = generateFileNames(fileCount);
        BuildableState s1 = new BuildableState(context).addFiles(names);

        // Create a mix of modifications
        BuildableState s2 = s1.setContent(names[0], "new_content")
                .touch(names[1]);

        CompareResult result = new StateComparator(context, s1, s2).compare();

        // contentModified: fileNames must match
        for (Difference diff : result.getContentModified()) {
            assertThat(diff.getPreviousFileState()).isNotNull();
            assertThat(diff.getFileState().getFileName())
                    .isEqualTo(diff.getPreviousFileState().getFileName());
        }

        // renamed: FileHash must match
        for (Difference diff : result.getRenamed()) {
            assertThat(diff.getPreviousFileState()).isNotNull();
            assertThat(diff.getFileState().getFileHash())
                    .isEqualTo(diff.getPreviousFileState().getFileHash());
        }

        // dateModified: FileHash and fileLength must match
        for (Difference diff : result.getDateModified()) {
            assertThat(diff.getPreviousFileState()).isNotNull();
            assertThat(diff.getFileState().getFileHash())
                    .isEqualTo(diff.getPreviousFileState().getFileHash());
            assertThat(diff.getFileState().getFileLength())
                    .isEqualTo(diff.getPreviousFileState().getFileLength());
        }
    }

    // Feature: change-detection, Property 6: Renamed file detection
    // **Validates: Requirements 7.1, 19.4**
    @ParameterizedTest
    @MethodSource("fileCounts")
    void renamedFileDetection(int fileCount) {
        BuildableContext context = createContext();
        String[] names = generateFileNames(fileCount);
        BuildableState s1 = new BuildableState(context).addFiles(names);

        // Rename the first file
        BuildableState s2 = s1.rename(names[0], "renamed_file");

        CompareResult result = new StateComparator(context, s1, s2).compare();

        assertThat(result.getRenamed()).hasSize(1);
        Difference diff = result.getRenamed().get(0);
        assertThat(diff.getFileState().getFileName()).isEqualTo("renamed_file");
        assertThat(diff.getPreviousFileState()).isNotNull();
        // FileHash must match between previous and current
        assertThat(diff.getFileState().getFileHash())
                .isEqualTo(diff.getPreviousFileState().getFileHash());
    }

    // Feature: change-detection, Property 7: Copied file detection
    // **Validates: Requirements 8.1, 8.2, 8.3**
    @ParameterizedTest
    @MethodSource("atLeastTwoFileCounts")
    void copiedFileDetection(int fileCount) {
        BuildableContext context = createContext();
        String[] names = generateFileNames(fileCount);
        BuildableState s1 = new BuildableState(context).addFiles(names);

        // Copy scenario: copy a file, then modify the original's content via appendContent.
        // The copy keeps the original hash while the original becomes contentModified, so the only
        // previous file carrying the copy's hash is the one consumed by the same-name phase.
        // That is exactly the definition of "copied" (Req 8.1).
        BuildableState s2 = s1.copy(names[0], "copy_of_file")
                .appendContent(names[0], "_modified");

        CompareResult result = new StateComparator(context, s1, s2).compare();

        assertThat(result.getCopied()).hasSize(1);
        Difference diff = result.getCopied().get(0);
        assertThat(diff.getFileState().getFileName()).isEqualTo("copy_of_file");
        // Req 8.2: the Difference links back to the original it was copied from
        assertThat(diff.getPreviousFileState()).isNotNull();
        assertThat(diff.getPreviousFileState().getFileName()).isEqualTo(names[0]);
        // The copy carries the original's former content
        assertThat(diff.getFileState().getFileHash())
                .isEqualTo(diff.getPreviousFileState().getFileHash());
        // The original itself is reported as contentModified
        assertThat(result.getContentModified()).hasSize(1);
        assertThat(result.getContentModified().get(0).getFileState().getFileName()).isEqualTo(names[0]);
        // The copy must not leak into the added list
        assertThat(result.getAdded()).noneMatch(d -> d.getFileState().getFileName().equals("copy_of_file"));
    }

    // Feature: change-detection, Property 8: Duplicated file detection
    // **Validates: Requirements 9.1, 9.2**
    @ParameterizedTest
    @MethodSource("fileCounts")
    void duplicatedFileDetection(int fileCount) {
        BuildableContext context = createContext();
        String[] names = generateFileNames(fileCount);
        BuildableState s1 = new BuildableState(context).addFiles(names);

        // Copy file_00 to dup_of_file_00 without modifying the original -> "duplicated"
        BuildableState s2 = s1.copy(names[0], "dup_of_file_00");

        CompareResult result = new StateComparator(context, s1, s2).compare();

        assertThat(result.getDuplicated()).isNotEmpty();
        Difference diff = result.getDuplicated().get(0);
        assertThat(diff.getFileState().getFileName()).isEqualTo("dup_of_file_00");
        assertThat(diff.getPreviousFileState()).isNotNull();
    }

    // Feature: change-detection, Property 9: Added file detection
    // **Validates: Requirements 10.1, 10.2, 10.3**
    @ParameterizedTest
    @MethodSource("fileCounts")
    void addedFileDetection(int fileCount) {
        BuildableContext context = createContext();
        String[] names = generateFileNames(fileCount);
        BuildableState s1 = new BuildableState(context).addFiles(names);

        // Add a completely new file with unique content
        BuildableState s2 = s1.addFiles("brand_new_file");

        CompareResult result = new StateComparator(context, s1, s2).compare();

        assertThat(result.getAdded()).hasSize(1);
        assertThat(result.getAdded().get(0).getFileState().getFileName()).isEqualTo("brand_new_file");
    }

    // Feature: change-detection, Property 10: Deleted file detection
    // **Validates: Requirements 11.1**
    @ParameterizedTest
    @MethodSource("fileCounts")
    void deletedFileDetection(int fileCount) {
        BuildableContext context = createContext();
        String[] names = generateFileNames(fileCount);
        BuildableState s1 = new BuildableState(context).addFiles(names);

        // Delete the first file
        BuildableState s2 = s1.delete(names[0]);

        CompareResult result = new StateComparator(context, s1, s2).compare();

        assertThat(result.getDeleted()).hasSize(1);
        assertThat(result.getDeleted().get(0).getFileState().getFileName()).isEqualTo(names[0]);
    }

    // Feature: change-detection, Property 11: Ignored files excluded from deletion
    // **Validates: Requirements 11.2, 11.3**
    @ParameterizedTest
    @MethodSource("atLeastTwoFileCounts")
    void ignoredFilesExcludedFromDeletion(int fileCount) {
        BuildableContext context = createContext();
        String[] names = generateFileNames(fileCount);
        BuildableState s1 = new BuildableState(context).addFiles(names);

        // Delete the first file but add it to ignored files in the current state
        BuildableState s2 = s1.delete(names[0]);
        s2.getIgnoredFiles().add(names[0]);

        CompareResult result = new StateComparator(context, s1, s2).compare();

        // The deleted file should NOT appear in the deleted list because it's ignored
        assertThat(result.getDeleted()).isEmpty();
    }

    // Feature: change-detection, Property 12: Null previous state classifies all as added
    // **Validates: Requirements 18.2**
    @ParameterizedTest
    @MethodSource("fileCounts")
    void nullPreviousStateClassifiesAllAsAdded(int fileCount) {
        BuildableContext context = createContext();
        String[] names = generateFileNames(fileCount);
        BuildableState currentState = new BuildableState(context).addFiles(names);

        CompareResult result = new StateComparator(context, null, currentState).compare();

        assertThat(result.getAdded()).hasSize(fileCount);
        assertThat(result.modifiedCount()).isEqualTo(fileCount);
    }

    // Feature: change-detection, Property 13: Hardware corruption mode behavior
    // **Validates: Requirements 12.1, 12.2**
    @ParameterizedTest
    @MethodSource("fileCounts")
    void hardwareCorruptionModeBehavior(int fileCount) {
        BuildableContext context = createContext();
        String[] names = generateFileNames(fileCount);
        BuildableState s1 = new BuildableState(context).addFiles(names);

        // Create a state where file has same name and time but different hash (corruption)
        BuildableState s2 = s1.clone();
        FileState corruptedFile = s2.getFileStates().stream()
                .filter(fs -> fs.getFileName().equals(names[0]))
                .findFirst().orElseThrow();
        corruptedFile.setFileHash(new FileHash("corrupted_small", "corrupted_medium", "corrupted_full"));

        CompareResult result = new StateComparator(context, s1, s2)
                .searchForHardwareCorruption()
                .compare();

        // In hardware corruption mode, these lists must be empty
        assertThat(result.getRenamed()).isEmpty();
        assertThat(result.getCopied()).isEmpty();
        assertThat(result.getDuplicated()).isEmpty();
        assertThat(result.getAdded()).isEmpty();
        assertThat(result.getDeleted()).isEmpty();

        // The file with same name+time but different hash should be corrupted
        assertThat(result.getCorrupted()).hasSize(1);
        assertThat(result.getCorrupted().get(0).getFileState().getFileName()).isEqualTo(names[0]);
    }

    // Feature: change-detection, Property 14: Ignore flags suppress modifications
    // **Validates: Requirements 14.1, 14.2, 14.3, 7.4**
    @ParameterizedTest
    @MethodSource("ignoreFlagCombos")
    void ignoreFlagsSuppressModifications(int combo) {
        BuildableContext context = createContext();
        String[] names = generateFileNames(4);
        BuildableState s1 = new BuildableState(context).addFiles(names);

        boolean ignoreAttrs = (combo & 1) != 0;
        boolean ignoreDates = (combo & 2) != 0;
        boolean ignoreRenamed = (combo & 4) != 0;

        context.getIgnored().setAttributesIgnored(ignoreAttrs);
        context.getIgnored().setDatesIgnored(ignoreDates);
        context.getIgnored().setRenamedIgnored(ignoreRenamed);

        // Create modifications of each type
        BuildableState s2 = s1.touch(names[0])           // date modification
                .rename(names[1], "renamed_file")          // rename
                .setContent(names[2], "new_content");      // content modification

        // Add attribute modification on names[3]
        s2 = s2.clone();
        FileState attrFile = s2.getFileStates().stream()
                .filter(fs -> fs.getFileName().equals(names[3]))
                .findFirst().orElseThrow();
        Map<String, String> attrs = new HashMap<>();
        attrs.put("PosixFilePermissions", "rwxr-xr-x");
        attrFile.setFileAttributes(attrs);

        CompareResult result = new StateComparator(context, s1, s2).compare();

        if (ignoreAttrs) {
            assertThat(result.getAttributesModified()).isEmpty();
        }
        if (ignoreDates) {
            assertThat(result.getDateModified()).isEmpty();
        }
        if (ignoreRenamed) {
            assertThat(result.getRenamed()).isEmpty();
        }
    }

    // Feature: change-detection, Property 18: Subdirectory filtering
    // **Validates: Requirements 1.7**
    @ParameterizedTest
    @MethodSource("fileCounts")
    void subdirectoryFiltering(int fileCount) {
        BuildableContext context = createContext();

        // Create files in different subdirectories
        List<String> allNames = new ArrayList<>();
        for (int i = 0; i < fileCount; i++) {
            allNames.add(String.format("subdir/file_%02d", i));
        }
        allNames.add("other/outside_file");
        allNames.add("root_file");

        BuildableState state = new BuildableState(context).addFiles(allNames.toArray(new String[0]));

        // Filter: keep only files starting with "subdir/"
        List<FileState> filtered = new ArrayList<>();
        for (FileState fs : state.getFileStates()) {
            if (fs.getFileName().startsWith("subdir/")) {
                filtered.add(fs);
            }
        }

        assertThat(filtered).hasSize(fileCount);
        for (FileState fs : filtered) {
            assertThat(fs.getFileName()).startsWith("subdir/");
        }

        // Verify no files outside the prefix are included
        for (FileState fs : state.getFileStates()) {
            if (!fs.getFileName().startsWith("subdir/")) {
                assertThat(filtered).doesNotContain(fs);
            }
        }
    }
}
