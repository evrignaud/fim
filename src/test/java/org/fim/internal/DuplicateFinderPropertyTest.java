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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import org.fim.model.Context;
import org.fim.model.DuplicateResult;
import org.fim.model.DuplicateSet;
import org.fim.model.FileHash;
import org.fim.model.FilePattern;
import org.fim.model.FileState;
import org.fim.model.FileTime;
import org.fim.model.HashMode;
import org.fim.model.State;
import org.fim.tooling.RandomInputs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Property-based tests for DuplicateFinder.
 * Feature: duplicate-detection
 */
public class DuplicateFinderPropertyTest {

    // ---- Helpers ----

    private Path tempDir;

    private Context createContext() throws IOException {
        tempDir = Files.createTempDirectory("fim-dup-test");
        Context ctx = new Context();
        ctx.setHashMode(HashMode.hashAll);
        ctx.setRepositoryRootDir(tempDir);
        ctx.setCurrentDirectory(tempDir);
        return ctx;
    }

    private void createFileOnDisk(String fileName) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{0});
    }

    private FileState makeFileState(String fileName, long fileLength, String hashSuffix) {
        FileHash hash = new FileHash("small_" + hashSuffix, "medium_" + hashSuffix, "full_" + hashSuffix);
        return new FileState(fileName, fileLength, new FileTime(System.currentTimeMillis()), hash, null);
    }

    private State buildState(List<FileState> fileStates) {
        State state = new State();
        state.getFileStates().addAll(fileStates);
        return state;
    }

    private void cleanup() {
        if (tempDir != null) {
            try (java.util.stream.Stream<Path> stream = Files.walk(tempDir)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                // Best-effort cleanup of temp directory
                            }
                        });
            } catch (IOException e) {
                // Best-effort cleanup of temp directory
            }
        }
    }

    // Feature: duplicate-detection, Property 1: DuplicateSet structural invariant
    // **Validates: Requirements 2.2, 2.7, 12.1, 12.2, 12.3**
    @ParameterizedTest
    @MethodSource("groupConfigs")
    void duplicateSetStructuralInvariant(List<int[]> groups) throws IOException {
        Context context = createContext();
        try {
            List<FileState> allFiles = new ArrayList<>();
            int fileIndex = 0;
            for (int[] group : groups) {
                int count = group[0];
                int size = group[1];
                String hashSuffix = "hash_" + fileIndex;
                for (int i = 0; i < count; i++) {
                    String name = "file_" + fileIndex + "_" + i + ".txt";
                    allFiles.add(makeFileState(name, size, hashSuffix));
                    createFileOnDisk(name);
                }
                fileIndex++;
            }

            State state = buildState(allFiles);
            DuplicateResult result = new DuplicateFinder(context).findDuplicates(state);

            for (DuplicateSet ds : result.getDuplicateSets()) {
                // At least 2 entries
                assertThat(ds.getDuplicatedFilesCount()).isGreaterThanOrEqualTo(2);

                // All entries share same FileHash and fileLength
                FileHash firstHash = ds.getDuplicatedFiles().get(0).getFileHash();
                long firstLength = ds.getDuplicatedFiles().get(0).getFileLength();
                for (FileState fs : ds.getDuplicatedFiles()) {
                    assertThat(fs.getFileHash()).isEqualTo(firstHash);
                    assertThat(fs.getFileLength()).isEqualTo(firstLength);
                }
            }
        } finally {
            cleanup();
        }
    }

    // Feature: duplicate-detection, Property 2: Zero-length file exclusion
    // **Validates: Requirements 2.3**
    @ParameterizedTest
    @MethodSource("fileCountsForZero")
    void zeroLengthFileExclusion(int normalCount) throws IOException {
        Context context = createContext();
        try {
            List<FileState> allFiles = new ArrayList<>();
            String sharedHash = "shared_hash";

            // Add normal-length duplicate files
            for (int i = 0; i < normalCount; i++) {
                String name = "normal_" + i + ".txt";
                allFiles.add(makeFileState(name, 100, sharedHash));
                createFileOnDisk(name);
            }

            // Add zero-length files with the same hash
            for (int i = 0; i < 3; i++) {
                String name = "zero_" + i + ".txt";
                allFiles.add(makeFileState(name, 0, sharedHash));
                createFileOnDisk(name);
            }

            State state = buildState(allFiles);
            DuplicateResult result = new DuplicateFinder(context).findDuplicates(state);

            // No zero-length file should appear in any DuplicateSet
            for (DuplicateSet ds : result.getDuplicateSets()) {
                for (FileState fs : ds.getDuplicatedFiles()) {
                    assertThat(fs.getFileLength()).isGreaterThan(0);
                }
            }
        } finally {
            cleanup();
        }
    }

    // Feature: duplicate-detection, Property 3: File pattern filtering
    // **Validates: Requirements 2.5, 2.6**
    @ParameterizedTest
    @MethodSource("patternScenarios")
    void filePatternFiltering(int scenario) throws IOException {
        Context context = createContext();
        try {
            List<FileState> allFiles = new ArrayList<>();
            String sharedHash = "pattern_hash";

            // Create files with different extensions
            String[] names = {"doc1.txt", "doc2.txt", "img1.jpg", "img2.jpg"};
            for (String name : names) {
                allFiles.add(makeFileState(name, 50, sharedHash));
                createFileOnDisk(name);
            }

            if (scenario == 0) {
                // Include only *.txt
                List<FilePattern> includes = new ArrayList<>();
                includes.add(new FilePattern("*.txt"));
                context.setIncludePatterns(includes);
            } else {
                // Exclude *.jpg
                List<FilePattern> excludes = new ArrayList<>();
                excludes.add(new FilePattern("*.jpg"));
                context.setExcludePatterns(excludes);
            }

            State state = buildState(allFiles);
            DuplicateResult result = new DuplicateFinder(context).findDuplicates(state);

            for (DuplicateSet ds : result.getDuplicateSets()) {
                for (FileState fs : ds.getDuplicatedFiles()) {
                    String fileName = Path.of(fs.getFileName()).getFileName().toString();
                    if (scenario == 0) {
                        // Only included files should appear
                        assertThat(new FilePattern("*.txt").match(fileName)).isTrue();
                    } else {
                        // Excluded files should not appear
                        assertThat(new FilePattern("*.jpg").match(fileName)).isFalse();
                    }
                }
            }
        } finally {
            cleanup();
        }
    }

    // Feature: duplicate-detection, Property 7: Input order independence
    // **Validates: Requirements 2.1**
    @ParameterizedTest
    @MethodSource("groupConfigs")
    void inputOrderIndependence(List<int[]> groups) throws IOException {
        Context context = createContext();
        try {
            List<FileState> allFiles = new ArrayList<>();
            int fileIndex = 0;
            for (int[] group : groups) {
                int count = group[0];
                int size = group[1];
                String hashSuffix = "hash_" + fileIndex;
                for (int i = 0; i < count; i++) {
                    String name = "file_" + fileIndex + "_" + i + ".txt";
                    allFiles.add(makeFileState(name, size, hashSuffix));
                    createFileOnDisk(name);
                }
                fileIndex++;
            }

            // Run on original order
            State state1 = buildState(new ArrayList<>(allFiles));
            DuplicateResult result1 = new DuplicateFinder(context).findDuplicates(state1);

            // Shuffle and run again
            List<FileState> shuffled = new ArrayList<>(allFiles);
            Collections.shuffle(shuffled);
            State state2 = buildState(shuffled);
            DuplicateResult result2 = new DuplicateFinder(context).findDuplicates(state2);

            // Same number of duplicate sets
            assertThat(result1.getDuplicateSets().size()).isEqualTo(result2.getDuplicateSets().size());
            assertThat(result1.getDuplicatedFilesCount()).isEqualTo(result2.getDuplicatedFilesCount());
            assertThat(result1.getTotalWastedSpace()).isEqualTo(result2.getTotalWastedSpace());

            // Same sets of file names in each duplicate set (order within sets may differ)
            List<Set<String>> sets1 = result1.getDuplicateSets().stream()
                    .map(ds -> ds.getDuplicatedFiles().stream()
                            .map(FileState::getFileName)
                            .collect(Collectors.toSet()))
                    .sorted((a, b) -> Integer.compare(a.hashCode(), b.hashCode()))
                    .collect(Collectors.toList());
            List<Set<String>> sets2 = result2.getDuplicateSets().stream()
                    .map(ds -> ds.getDuplicatedFiles().stream()
                            .map(FileState::getFileName)
                            .collect(Collectors.toSet()))
                    .sorted((a, b) -> Integer.compare(a.hashCode(), b.hashCode()))
                    .collect(Collectors.toList());
            assertThat(new HashSet<>(sets1)).isEqualTo(new HashSet<>(sets2));
        } finally {
            cleanup();
        }
    }

    // Feature: duplicate-detection, Property 8: No duplicates when all hashes unique
    // **Validates: Requirements 12.6**
    @ParameterizedTest
    @MethodSource("uniqueFileCounts")
    void noDuplicatesWhenAllHashesUnique(int fileCount) throws IOException {
        Context context = createContext();
        try {
            List<FileState> allFiles = new ArrayList<>();
            for (int i = 0; i < fileCount; i++) {
                String name = "unique_" + i + ".txt";
                allFiles.add(makeFileState(name, 100L + i, "unique_hash_" + i));
                createFileOnDisk(name);
            }

            State state = buildState(allFiles);
            DuplicateResult result = new DuplicateFinder(context).findDuplicates(state);

            assertThat(result.getDuplicateSets()).isEmpty();
            assertThat(result.getDuplicatedFilesCount()).isEqualTo(0);
        } finally {
            cleanup();
        }
    }

    // ---- Providers ----

    /**
     * Groups of same-hash files: 1 to 4 groups, each holding 1 to 5 files of a size between 1 and 1000.
     * The file size range is too wide to enumerate, so the configurations are sampled from a fixed seed
     * with the smallest and largest shapes listed explicitly first.
     */
    static Stream<List<int[]>> groupConfigs() {
        List<List<int[]>> values = new ArrayList<>();
        // Explicit boundaries first
        values.add(List.of(new int[]{1, 1}));
        values.add(List.of(new int[]{5, 1000}));
        values.add(List.of(new int[]{1, 1000}, new int[]{5, 1},
                new int[]{1, 1}, new int[]{5, 1000}));

        Random random = RandomInputs.random();
        for (int i = 0; i < 100; i++) {
            int groupCount = RandomInputs.intBetween(random, 1, 4);
            List<int[]> groups = new ArrayList<>(groupCount);
            for (int j = 0; j < groupCount; j++) {
                int count = RandomInputs.intBetween(random, 1, 5);
                int size = RandomInputs.intBetween(random, 1, 1000);
                groups.add(new int[]{count, size});
            }
            values.add(groups);
        }
        return values.stream();
    }

    /** Small enough to enumerate: at least 2 files so a duplicate set can form. */
    static IntStream fileCountsForZero() {
        return IntStream.rangeClosed(2, 6);
    }

    /** Both scenarios: 0 exercises include patterns, 1 exercises exclude patterns. */
    static IntStream patternScenarios() {
        return IntStream.rangeClosed(0, 1);
    }

    /** Small enough to enumerate completely. */
    static IntStream uniqueFileCounts() {
        return IntStream.rangeClosed(1, 10);
    }
}
