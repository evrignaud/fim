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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.fim.tooling.BuildableContext;
import org.fim.tooling.BuildableState;
import org.fim.tooling.RandomInputs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Property-based tests for State.
 * Features: file-ignore-system, state-management
 */
public class StatePropertyTest {

    private static final long FIXED_TIMESTAMP = 1437686650000L;
    private static final String ALPHANUM = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final String GZIP_SUFFIX = ".json.gz";
    private static final String COMMENT_CHARS = ALPHANUM + " _-.,!";
    private static final String IGNORED_FILE_CHARS = ALPHANUM + "._-";

    // =========================================================================
    // Feature: state-management, Property 1: State serialization round-trip
    // **Validates: Requirements 7.1, 7.2**
    // =========================================================================
    @ParameterizedTest
    @MethodSource("randomState")
    void stateSerializationRoundTrip(State state) throws IOException, CorruptedStateException {
        Path tempFile = Files.createTempFile("state-prop1-", GZIP_SUFFIX);
        try {
            state.saveToGZipFile(tempFile);
            State loaded = State.loadFromGZipFile(tempFile, false);

            assertThat(loaded.getModelVersion()).isEqualTo(state.getModelVersion());
            assertThat(loaded.getTimestamp()).isEqualTo(state.getTimestamp());
            assertThat(loaded.getComment()).isEqualTo(state.getComment());
            assertThat(loaded.getHashMode()).isEqualTo(state.getHashMode());
            assertThat(loaded.getFileCount()).isEqualTo(state.getFileCount());
            assertThat(loaded.getFilesContentLength()).isEqualTo(state.getFilesContentLength());
            assertThat(loaded.getIgnoredFiles()).containsExactlyInAnyOrderElementsOf(state.getIgnoredFiles());

            // Verify each FileState entry is preserved
            assertThat(loaded.getFileStates()).hasSameSizeAs(state.getFileStates());
            for (int i = 0; i < loaded.getFileStates().size(); i++) {
                FileState original = state.getFileStates().get(i);
                FileState roundTripped = loaded.getFileStates().get(i);
                assertThat(roundTripped.getFileName()).isEqualTo(original.getFileName());
                assertThat(roundTripped.getFileLength()).isEqualTo(original.getFileLength());
                assertThat(roundTripped.getFileTime()).isEqualTo(original.getFileTime());
                assertThat(roundTripped.getFileHash()).isEqualTo(original.getFileHash());
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // =========================================================================
    // Feature: state-management, Property 2: State hash integrity verification
    // **Validates: Requirements 3.3, 3.4**
    // =========================================================================
    @ParameterizedTest
    @MethodSource("randomState")
    void stateHashIntegrityVerification(State state) throws IOException, CorruptedStateException {
        Path tempFile = Files.createTempFile("state-prop2-", GZIP_SUFFIX);
        try {
            state.saveToGZipFile(tempFile);
            // Load with full validation — should NOT throw CorruptedStateException
            State loaded = State.loadFromGZipFile(tempFile, true);
            assertThat(loaded).isNotNull();
            assertThat(loaded.getStateHash()).isNotNull();
            assertThat(loaded.getStateHash()).isNotEmpty();
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // =========================================================================
    // Feature: state-management, Property 3: Corrupted state detection
    // **Validates: Requirements 3.5**
    // =========================================================================
    @ParameterizedTest
    @MethodSource("randomState")
    void corruptedStateDetection(State state) throws IOException {
        Path tempFile = Files.createTempFile("state-prop3-", GZIP_SUFFIX);
        try {
            state.saveToGZipFile(tempFile);

            // Read the GZIP file, decompress, corrupt a byte, recompress, write back
            byte[] originalBytes = Files.readAllBytes(tempFile);
            byte[] decompressed;
            try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(originalBytes))) {
                decompressed = gis.readAllBytes();
            }

            if (decompressed.length < 2) {
                return; // Skip trivially small states
            }

            // Corrupt a byte in the middle of the JSON content
            int corruptIndex = decompressed.length / 2;
            decompressed[corruptIndex] = (byte) (decompressed[corruptIndex] ^ 0xFF);

            // Recompress
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gos = new GZIPOutputStream(baos)) {
                gos.write(decompressed);
            }
            Files.write(tempFile, baos.toByteArray());

            // Loading with full validation should throw CorruptedStateException or IOException
            // (corrupted JSON may fail to parse, or hash mismatch triggers CorruptedStateException)
            assertThatThrownBy(() -> State.loadFromGZipFile(tempFile, true))
                    .isInstanceOfAny(CorruptedStateException.class, IOException.class, com.fasterxml.jackson.core.JsonProcessingException.class);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // =========================================================================
    // Feature: state-management, Property 4: FileState sort invariant on save
    // **Validates: Requirements 3.2**
    // =========================================================================
    @ParameterizedTest
    @MethodSource("randomState")
    void fileStateSortInvariantOnSave(State state) throws IOException, CorruptedStateException {
        Path tempFile = Files.createTempFile("state-prop4-", GZIP_SUFFIX);
        try {
            state.saveToGZipFile(tempFile);
            State loaded = State.loadFromGZipFile(tempFile, false);

            List<FileState> fileStates = loaded.getFileStates();
            for (int i = 1; i < fileStates.size(); i++) {
                assertThat(fileStates.get(i).getFileName())
                        .as("FileStates should be sorted by fileName")
                        .isGreaterThanOrEqualTo(fileStates.get(i - 1).getFileName());
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // =========================================================================
    // Feature: state-management, Property 6: Comment preservation
    // **Validates: Requirements 1.3, 2.5**
    // =========================================================================
    @ParameterizedTest
    @MethodSource("commentStrings")
    void commentPreservation(String comment) throws IOException, CorruptedStateException {
        BuildableContext ctx = new BuildableContext();
        BuildableState state = new BuildableState(ctx);
        state = state.addFiles("placeholder_file");
        fixTimeStamps(state);
        state.setComment(comment);

        Path tempFile = Files.createTempFile("state-prop6-", GZIP_SUFFIX);
        try {
            state.saveToGZipFile(tempFile);
            State loaded = State.loadFromGZipFile(tempFile, false);
            assertThat(loaded.getComment()).isEqualTo(comment);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // =========================================================================
    // Feature: state-management, Property 14: State field completeness on creation
    // **Validates: Requirements 1.10**
    // =========================================================================
    @ParameterizedTest
    @MethodSource("randomState")
    void stateFieldCompletenessOnCreation(State state) throws IOException, CorruptedStateException {
        Path tempFile = Files.createTempFile("state-prop14-", GZIP_SUFFIX);
        try {
            state.saveToGZipFile(tempFile);
            State loaded = State.loadFromGZipFile(tempFile, false);

            assertThat(loaded.getModelVersion()).isEqualTo(State.CURRENT_MODEL_VERSION);
            assertThat(loaded.getTimestamp()).isGreaterThan(0);
            assertThat(loaded.getFileCount()).isEqualTo(loaded.getFileStates().size());

            long expectedContentLength = 0;
            for (FileState fs : loaded.getFileStates()) {
                expectedContentLength += fs.getFileLength();
            }
            assertThat(loaded.getFilesContentLength()).isEqualTo(expectedContentLength);
            assertThat(loaded.getHashMode()).isNotNull();
            assertThat(loaded.getStateHash()).isNotNull();
            assertThat(loaded.getStateHash()).isNotEmpty();
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // =========================================================================
    // Feature: file-ignore-system, Property 9: Subdirectory state filtering
    // **Validates: Requirements 6.8**
    // =========================================================================
    @ParameterizedTest
    @MethodSource("subDirNamesWithFileNames")
    void subdirectoryStateFiltering(
            String subDir,
            List<String> fileNames) {

        BuildableContext ctx = new BuildableContext();
        BuildableState state = new BuildableState(ctx);

        List<String> insideFiles = new ArrayList<>();
        for (int i = 0; i < fileNames.size(); i++) {
            insideFiles.add(subDir + "/" + fileNames.get(i));
        }

        List<String> outsideFiles = new ArrayList<>();
        for (int i = 0; i < fileNames.size(); i++) {
            outsideFiles.add("other_" + i + "_" + fileNames.get(i));
        }

        List<String> allFiles = new ArrayList<>();
        allFiles.addAll(insideFiles);
        allFiles.addAll(outsideFiles);
        state = state.addFiles(allFiles.toArray(String[]::new));
        fixTimeStamps(state);

        Path rootDir = Paths.get(".");
        Path currentDir = Paths.get(subDir);
        State filtered = state.filterDirectory(rootDir, currentDir, true);

        for (FileState fs : filtered.getFileStates()) {
            assertThat(fs.getFileName()).startsWith(subDir + "/");
        }

        Set<String> filteredNames = new HashSet<>();
        for (FileState fs : filtered.getFileStates()) {
            filteredNames.add(fs.getFileName());
        }
        for (String insideName : insideFiles) {
            assertThat(filteredNames).contains(insideName);
        }
        for (String outsideName : outsideFiles) {
            assertThat(filteredNames).doesNotContain(outsideName);
        }
    }

    // =========================================================================
    // Feature: file-ignore-system, Property 10: State ignoredFiles round-trip
    // **Validates: Requirements 7.2, 7.3, 7.4**
    // =========================================================================
    @ParameterizedTest
    @MethodSource("ignoredFileSets")
    void stateIgnoredFilesRoundTrip(
            Set<String> ignoredFiles) throws IOException, CorruptedStateException {

        BuildableContext ctx = new BuildableContext();
        BuildableState state = new BuildableState(ctx);
        state = state.addFiles("placeholder_file");
        fixTimeStamps(state);
        state.setIgnoredFiles(ignoredFiles);

        Path tempFile = Files.createTempFile("state-prop-roundtrip-", GZIP_SUFFIX);
        try {
            state.saveToGZipFile(tempFile);
            State loaded = State.loadFromGZipFile(tempFile, true);

            assertThat(loaded.getIgnoredFiles())
                    .containsExactlyInAnyOrderElementsOf(ignoredFiles);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // =========================================================================
    // Providers
    // =========================================================================

    /**
     * States holding 1 to 5 file states, a comment of 0 to 20 characters and one of the four hash modes.
     * The smallest and the largest shape are listed explicitly, the rest is a seeded sample that walks
     * through every hash mode in turn.
     */
    static Stream<State> randomState() {
        HashMode[] hashModes = {HashMode.hashAll, HashMode.hashSmallBlock,
            HashMode.hashMediumBlock, HashMode.dontHash};
        List<State> values = new ArrayList<>();
        Random random = RandomInputs.random();
        values.add(buildState(randomFileStates(random, 1), "", hashModes[0]));
        values.add(buildState(randomFileStates(random, 5),
                RandomInputs.stringOf(random, ALPHANUM, 20, 20), hashModes[1]));
        for (int i = 0; i < 100; i++) {
            List<FileState> fileStates = randomFileStates(random, RandomInputs.intBetween(random, 1, 5));
            String comment = RandomInputs.stringOf(random, ALPHANUM, 0, 20);
            values.add(buildState(fileStates, comment, hashModes[i % hashModes.length]));
        }
        return values.stream();
    }

    private static State buildState(List<FileState> fileStates, String comment, HashMode hashMode) {
        BuildableContext ctx = new BuildableContext();
        BuildableState state = new BuildableState(ctx);
        state.setComment(comment);
        state.setHashMode(hashMode);
        state.setTimestamp(FIXED_TIMESTAMP);
        state.getFileStates().clear();
        state.getFileStates().addAll(new ArrayList<>(fileStates));
        state.updateFileCount();
        state.updateFilesContentLength();
        return state;
    }

    private static List<FileState> randomFileStates(Random random, int count) {
        List<FileState> fileStates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            fileStates.add(randomFileState(random));
        }
        return fileStates;
    }

    private static FileState randomFileState(Random random) {
        String name = RandomInputs.stringOf(random, ALPHANUM, 1, 15) + ".dat";
        long fileLength = RandomInputs.longBetween(random, 0, 100_000);
        long timestamp = RandomInputs.longBetween(random, 1_000_000_000_000L, 2_000_000_000_000L);
        String hash = RandomInputs.stringOf(random, ALPHANUM, 5, 20);
        FileHash fh = new FileHash("sb_" + hash, "mb_" + hash, "full_" + hash);
        FileTime ft = new FileTime(timestamp, timestamp);
        return new FileState(name, fileLength, ft, fh, null);
    }

    /** Comments of 1 to 50 characters: both ends of the length range, then a seeded sample. */
    static Stream<String> commentStrings() {
        List<String> values = new ArrayList<>();
        Random random = RandomInputs.random();
        values.add(RandomInputs.stringOf(random, COMMENT_CHARS, 1, 1));
        values.add(RandomInputs.stringOf(random, COMMENT_CHARS, 50, 50));
        for (int i = 0; i < 100; i++) {
            values.add(RandomInputs.stringOf(random, COMMENT_CHARS, 1, 50));
        }
        return values.stream();
    }

    /**
     * A subdirectory name paired with 1 to 5 distinct file names. The smallest and the largest list are
     * listed explicitly, the rest is a seeded sample.
     */
    static Stream<Arguments> subDirNamesWithFileNames() {
        List<Arguments> values = new ArrayList<>();
        Random random = RandomInputs.random();
        values.add(Arguments.of(RandomInputs.stringOf(random, ALPHANUM, 1, 10),
                randomFileNames(random, 1)));
        values.add(Arguments.of(RandomInputs.stringOf(random, ALPHANUM, 1, 10),
                randomFileNames(random, 5)));
        for (int i = 0; i < 100; i++) {
            values.add(Arguments.of(RandomInputs.stringOf(random, ALPHANUM, 1, 10),
                    randomFileNames(random, RandomInputs.intBetween(random, 1, 5))));
        }
        return values.stream();
    }

    /** Distinct file names, as the {@code uniqueElements()} generator used to guarantee. */
    private static List<String> randomFileNames(Random random, int count) {
        Set<String> fileNames = new LinkedHashSet<>();
        int attempts = 0;
        while (fileNames.size() < count && attempts < count * 10) {
            fileNames.add(RandomInputs.stringOf(random, ALPHANUM, 1, 10));
            attempts++;
        }
        return new ArrayList<>(fileNames);
    }

    /**
     * Sets of 1 to 8 ignored entries, alternating between the two shapes the generator mixed: a file name
     * ending with {@code .txt} and a directory name ending with {@code /}. The smallest and the largest set
     * are listed explicitly, the rest is a seeded sample.
     */
    static Stream<Set<String>> ignoredFileSets() {
        List<Set<String>> values = new ArrayList<>();
        Random random = RandomInputs.random();
        values.add(randomIgnoredFiles(random, 1));
        values.add(randomIgnoredFiles(random, 8));
        for (int i = 0; i < 100; i++) {
            values.add(randomIgnoredFiles(random, RandomInputs.intBetween(random, 1, 8)));
        }
        return values.stream();
    }

    private static Set<String> randomIgnoredFiles(Random random, int count) {
        Set<String> entries = new LinkedHashSet<>();
        int attempts = 0;
        while (entries.size() < count && attempts < count * 10) {
            if (attempts % 2 == 0) {
                entries.add(RandomInputs.stringOf(random, IGNORED_FILE_CHARS, 1, 15) + ".txt");
            } else {
                entries.add(RandomInputs.stringOf(random, ALPHANUM, 1, 15) + "/");
            }
            attempts++;
        }
        return entries;
    }

    private void fixTimeStamps(BuildableState s) {
        s.setTimestamp(FIXED_TIMESTAMP);
        for (FileState fileState : s.getFileStates()) {
            fileState.getFileTime().reset(FIXED_TIMESTAMP);
        }
    }
}
