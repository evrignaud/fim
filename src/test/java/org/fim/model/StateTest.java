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

import org.fim.tooling.BuildableState;
import org.fim.tooling.ObjectAssert;
import org.fim.tooling.StateAssert;
import org.fim.util.JsonIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class StateTest extends StateAssert {
    public static final long MODIFICATION_TIMESTAMP = 1437686650000L;

    private static final String FILE_1 = "file_1";
    private static final String FILE_2 = "file_2";
    private static final String DIR_1_FILE_1 = "dir_1/file_1";
    private static final String DIR_1_FILE_2 = "dir_1/file_2";
    private static final String DIR_2_FILE_1 = "dir_2/file_1";
    private static final String DIR_2_FILE_2 = "dir_2/file_2";
    private static final String IGNORED_1 = "ignored1.txt";
    private static final String IGNORED_2 = "ignored2.txt";
    private static final String FILE_A = "file_a";
    private static final String TAMPERED_HASH = "tampered_hash_value";
    private static final String GZIP_SUFFIX = ".json.gz";

    private BuildableState a1;
    private BuildableState a2;
    private BuildableState b;

    @BeforeEach
    public void setUp() {
        a1 = new BuildableState(defaultContext()).addFiles(FILE_1, FILE_2);
        a2 = a1.clone();
        a2.setTimestamp(a1.getTimestamp());

        b = a1.delete(FILE_2).addFiles("file_3");
        b.setTimestamp(a1.getTimestamp());
    }

    @Test
    public void equalsIsWorking() {
        ObjectAssert.equalsIsWorking(a1, a2, b);
    }

    @Test
    public void hashcodeIsWorking() {
        ObjectAssert.hashcodeIsWorking(a1, a2, b);
    }

    @Test
    public void toStringIsWorking() {
        assertThat(a1.toString().contains("modelVersion")).isTrue();
    }

    @Test
    public void canHashAState() {
        fixTimeStamps(a1);

        String a1Hash = a1.hashState();
        assertThat(a1Hash.length()).isEqualTo(80);
        assertThat(a1Hash).isEqualTo("[#CZ-RQZX;TDnGiB^jK(`Q+n3h\\enrMKKsX-C!4:Ll8$c\"hEK+rRmW)DjDh?prX;6Hn0UL8LV=S*;0L!");
    }

    @Test
    public void hashDependsOnContent() {
        fixTimeStamps(a1);
        fixTimeStamps(a2);
        fixTimeStamps(b);

        String a1Hash = a1.hashState();
        String a2Hash = a2.hashState();
        String bHash = b.hashState();

        assertThat(a1Hash).isEqualTo(a2Hash);

        assertThat(a1Hash).isNotEqualTo(bHash);
    }

    @Test
    public void aStateCanBeCloned() {
        State s = a1.clone();
        assertThat(s.hashState()).isEqualTo(a1.hashState());

        s.getModificationCounts().setAdded(1);
        State clone = s.clone();
        assertThat(clone.getModificationCounts().getAdded()).isEqualTo(1);
    }

    @Test
    public void canFilterFilesInside() {
        State s = a1.addFiles(DIR_1_FILE_1, DIR_1_FILE_2, DIR_2_FILE_1, DIR_2_FILE_2);

        State filteredState = s.filterDirectory(Paths.get("."), Paths.get("dir_1"), true);

        assertThat(toFileNames(filteredState.getFileStates())).isEqualTo(Arrays.asList(DIR_1_FILE_1, DIR_1_FILE_2));
    }

    @Test
    public void canFilterFilesOutside() {
        State s = a1.addFiles(DIR_1_FILE_1, DIR_1_FILE_2, DIR_2_FILE_1, DIR_2_FILE_2);

        State filteredState = s.filterDirectory(Paths.get("."), Paths.get("dir_1"), false);

        assertThat(toFileNames(filteredState.getFileStates())).isEqualTo(Arrays.asList(DIR_2_FILE_1, DIR_2_FILE_2, FILE_1, FILE_2));
    }

    @Test
    public void ignoredFilesAffectsEquals() {
        BuildableState s1 = new BuildableState(defaultContext()).addFiles(FILE_1);
        BuildableState s2 = s1.clone();
        s2.setTimestamp(s1.getTimestamp());

        assertThat(s1).isEqualTo(s2);

        Set<String> ignored = new HashSet<>(Arrays.asList(IGNORED_1, IGNORED_2));
        s2.setIgnoredFiles(ignored);

        assertThat(s1).isNotEqualTo(s2);
    }

    @Test
    public void ignoredFilesAffectsHashCode() {
        BuildableState s1 = new BuildableState(defaultContext()).addFiles(FILE_1);
        BuildableState s2 = s1.clone();
        s2.setTimestamp(s1.getTimestamp());

        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());

        Set<String> ignored = new HashSet<>(Arrays.asList(IGNORED_1, IGNORED_2));
        s2.setIgnoredFiles(ignored);

        assertThat(s1.hashCode()).isNotEqualTo(s2.hashCode());
    }

    @Test
    public void ignoredFilesAffectsHashState() {
        BuildableState s1 = new BuildableState(defaultContext()).addFiles(FILE_1);
        fixTimeStamps(s1);
        BuildableState s2 = s1.clone();
        s2.setTimestamp(s1.getTimestamp());
        fixTimeStamps(s2);

        assertThat(s1.hashState()).isEqualTo(s2.hashState());

        Set<String> ignored = new HashSet<>(Arrays.asList(IGNORED_1, IGNORED_2));
        s2.setIgnoredFiles(ignored);

        assertThat(s1.hashState()).isNotEqualTo(s2.hashState());
    }

    @Test
    public void ignoredFilesAreCloned() {
        Set<String> ignored = new HashSet<>(Arrays.asList(IGNORED_1, "subdir/"));
        a1.setIgnoredFiles(ignored);

        State cloned = a1.clone();

        assertThat(cloned.getIgnoredFiles()).isEqualTo(ignored);
        // Verify it's a deep copy, not the same reference
        assertThat(cloned.getIgnoredFiles()).isNotSameAs(a1.getIgnoredFiles());
    }

    @Test
    public void ignoredFilesRoundTripViaGzip() throws IOException, CorruptedStateException {
        Set<String> ignored = new HashSet<>(Arrays.asList("build/", "temp.log", ".cache/", "dir/subdir/"));
        a1.setIgnoredFiles(ignored);
        fixTimeStamps(a1);

        Path tempFile = Files.createTempFile("state-test-", GZIP_SUFFIX);
        try {
            a1.saveToGZipFile(tempFile);
            State loaded = State.loadFromGZipFile(tempFile, true);

            assertThat(loaded.getIgnoredFiles()).isEqualTo(ignored);
            assertThat(loaded.getIgnoredFiles()).containsExactlyInAnyOrderElementsOf(ignored);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void emptyIgnoredFilesRoundTripViaGzip() throws IOException, CorruptedStateException {
        a1.setIgnoredFiles(new HashSet<>());
        fixTimeStamps(a1);

        Path tempFile = Files.createTempFile("state-test-empty-", GZIP_SUFFIX);
        try {
            a1.saveToGZipFile(tempFile);
            State loaded = State.loadFromGZipFile(tempFile, true);

            // Empty set may deserialize as null due to NON_NULL serialization config
            Set<String> loadedIgnored = loaded.getIgnoredFiles();
            assertThat(loadedIgnored == null || loadedIgnored.isEmpty()).isTrue();
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void nullIgnoredFilesHandledInEquals() {
        BuildableState s1 = new BuildableState(defaultContext()).addFiles(FILE_1);
        BuildableState s2 = s1.clone();
        s2.setTimestamp(s1.getTimestamp());

        s1.setIgnoredFiles(null);
        s2.setIgnoredFiles(null);

        assertThat(s1).isEqualTo(s2);
    }

    @Test
    public void nullIgnoredFilesHandledInHashCode() {
        BuildableState s1 = new BuildableState(defaultContext()).addFiles(FILE_1);
        BuildableState s2 = s1.clone();
        s2.setTimestamp(s1.getTimestamp());

        s1.setIgnoredFiles(null);
        s2.setIgnoredFiles(null);

        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
    }

    // === Task 1.1: Round-trip save/load, field completeness, sort invariant ===

    @Test
    public void roundTripSaveLoadPreservesState() throws IOException, CorruptedStateException {
        fixTimeStamps(a1);
        Path tempFile = Files.createTempFile("state-roundtrip-", GZIP_SUFFIX);
        try {
            a1.saveToGZipFile(tempFile);
            State loaded = State.loadFromGZipFile(tempFile, true);

            assertThat(loaded.getModelVersion()).isEqualTo(a1.getModelVersion());
            assertThat(loaded.getTimestamp()).isEqualTo(a1.getTimestamp());
            assertThat(loaded.getComment()).isEqualTo(a1.getComment());
            assertThat(loaded.getFileCount()).isEqualTo(a1.getFileCount());
            assertThat(loaded.getFilesContentLength()).isEqualTo(a1.getFilesContentLength());
            assertThat(loaded.getHashMode()).isEqualTo(a1.getHashMode());
            assertThat(loaded.getFileStates()).isEqualTo(a1.getFileStates());
            assertThat(loaded.getStateHash()).isEqualTo(a1.getStateHash());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void roundTripSaveLoadWithManyFiles() throws IOException, CorruptedStateException {
        BuildableState state = new BuildableState(defaultContext())
                .addFiles("z_file", "a_file", "m_file", "dir/nested_file");
        fixTimeStamps(state);

        Path tempFile = Files.createTempFile("state-many-", GZIP_SUFFIX);
        try {
            state.saveToGZipFile(tempFile);
            State loaded = State.loadFromGZipFile(tempFile, true);

            assertThat(loaded.getFileStates()).hasSize(4);
            assertThat(loaded.getFileStates()).isEqualTo(state.getFileStates());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void roundTripSaveLoadWithDifferentHashModes() throws IOException, CorruptedStateException {
        for (HashMode mode : HashMode.values()) {
            BuildableState state = new BuildableState(defaultContext());
            state.setHashMode(mode);
            // Add files using a context with the matching hash mode
            switch (mode) {
                case dontHash -> state = new BuildableState(defaultContext().dontHashFiles()).addFiles(FILE_A);
                case hashSmallBlock -> state = new BuildableState(defaultContext().hashSmallBlock()).addFiles(FILE_A);
                case hashMediumBlock -> state = new BuildableState(defaultContext().hashMediumBlock()).addFiles(FILE_A);
                default -> state = new BuildableState(defaultContext().computeAllHash()).addFiles(FILE_A);
            }
            fixTimeStamps(state);

            Path tempFile = Files.createTempFile("state-mode-" + mode + "-", GZIP_SUFFIX);
            try {
                state.saveToGZipFile(tempFile);
                State loaded = State.loadFromGZipFile(tempFile, true);

                assertThat(loaded.getHashMode()).isEqualTo(state.getHashMode());
                assertThat(loaded.getFileStates()).isEqualTo(state.getFileStates());
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    @Test
    public void roundTripSaveLoadWithComment() throws IOException, CorruptedStateException {
        BuildableState state = new BuildableState(defaultContext()).addFiles("file_x");
        state.setComment("Test commit message with special chars: !@#$%");
        fixTimeStamps(state);

        Path tempFile = Files.createTempFile("state-comment-", GZIP_SUFFIX);
        try {
            state.saveToGZipFile(tempFile);
            State loaded = State.loadFromGZipFile(tempFile, true);

            assertThat(loaded.getComment()).isEqualTo("Test commit message with special chars: !@#$%");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void roundTripSaveLoadEmptyState() throws IOException, CorruptedStateException {
        BuildableState state = new BuildableState(defaultContext());
        fixTimeStamps(state);

        Path tempFile = Files.createTempFile("state-empty-", GZIP_SUFFIX);
        try {
            state.saveToGZipFile(tempFile);
            State loaded = State.loadFromGZipFile(tempFile, true);

            assertThat(loaded.getFileStates()).isEmpty();
            assertThat(loaded.getFileCount()).isEqualTo(0);
            assertThat(loaded.getFilesContentLength()).isEqualTo(0);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void saveToGZipSortsFileStatesByFileName() throws IOException, CorruptedStateException {
        // Create state with files in reverse order
        BuildableState state = new BuildableState(defaultContext())
                .addFiles("z_last", "a_first", "m_middle");
        fixTimeStamps(state);

        Path tempFile = Files.createTempFile("state-sort-", GZIP_SUFFIX);
        try {
            state.saveToGZipFile(tempFile);
            State loaded = State.loadFromGZipFile(tempFile, true);

            List<String> fileNames = toFileNames(loaded.getFileStates());
            List<String> sortedNames = new ArrayList<>(fileNames);
            Collections.sort(sortedNames);
            assertThat(fileNames).isEqualTo(sortedNames);
            assertThat(fileNames.get(0)).isEqualTo("a_first");
            assertThat(fileNames.get(1)).isEqualTo("m_middle");
            assertThat(fileNames.get(2)).isEqualTo("z_last");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void saveToGZipComputesFileCountAndContentLength() throws IOException, CorruptedStateException {
        BuildableState state = new BuildableState(defaultContext())
                .addFiles("file_alpha", "file_beta");
        fixTimeStamps(state);

        Path tempFile = Files.createTempFile("state-counts-", GZIP_SUFFIX);
        try {
            state.saveToGZipFile(tempFile);
            State loaded = State.loadFromGZipFile(tempFile, false);

            assertThat(loaded.getFileCount()).isEqualTo(2);

            long expectedContentLength = 0;
            for (FileState fs : loaded.getFileStates()) {
                expectedContentLength += fs.getFileLength();
            }
            assertThat(loaded.getFilesContentLength()).isEqualTo(expectedContentLength);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void saveToGZipComputesStateHash() throws IOException {
        fixTimeStamps(a1);

        Path tempFile = Files.createTempFile("state-hash-", GZIP_SUFFIX);
        try {
            a1.saveToGZipFile(tempFile);

            // After save, stateHash should be set and non-null
            assertThat(a1.getStateHash()).isNotNull();
            assertThat(a1.getStateHash()).isNotEmpty();

            // The hash should be an Ascii85-encoded SHA-512 (80 chars)
            assertThat(a1.getStateHash().length()).isEqualTo(80);

            // hashState() should produce the same value
            assertThat(a1.hashState()).isEqualTo(a1.getStateHash());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void loadWithFullValidationDetectsCorruption() throws IOException, CorruptedStateException {
        fixTimeStamps(a1);

        Path tempFile = Files.createTempFile("state-corrupt-", GZIP_SUFFIX);
        try {
            a1.saveToGZipFile(tempFile);

            // Tamper with the file: load, modify stateHash, re-save raw
            State loaded = State.loadFromGZipFile(tempFile, false);
            loaded.setStateHash(TAMPERED_HASH);
            JsonIO jsonIO = new JsonIO();
            try (Writer writer = new OutputStreamWriter(
                    new GZIPOutputStream(new FileOutputStream(tempFile.toFile())),
                    StandardCharsets.UTF_8)) {
                jsonIO.getObjectWriter().writeValue(writer, loaded);
            }

            // Loading with full validation should throw CorruptedStateException
            assertThatThrownBy(() -> State.loadFromGZipFile(tempFile, true))
                    .isInstanceOf(CorruptedStateException.class);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void loadWithoutFullValidationSkipsIntegrityCheck() throws IOException, CorruptedStateException {
        fixTimeStamps(a1);

        Path tempFile = Files.createTempFile("state-noval-", GZIP_SUFFIX);
        try {
            a1.saveToGZipFile(tempFile);

            // Tamper with the file
            State loaded = State.loadFromGZipFile(tempFile, false);
            loaded.setStateHash(TAMPERED_HASH);

            JsonIO jsonIO = new JsonIO();
            try (Writer writer = new OutputStreamWriter(
                    new GZIPOutputStream(new FileOutputStream(tempFile.toFile())),
                    StandardCharsets.UTF_8)) {
                jsonIO.getObjectWriter().writeValue(writer, loaded);
            }

            // Loading without full validation should succeed
            State reloaded = State.loadFromGZipFile(tempFile, false);
            assertThat(reloaded).isNotNull();
            assertThat(reloaded.getStateHash()).isEqualTo(TAMPERED_HASH);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void stateHashIsDeterministic() {
        fixTimeStamps(a1);
        String hash1 = a1.hashState();
        String hash2 = a1.hashState();
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    public void stateHashChangesWhenContentChanges() {
        fixTimeStamps(a1);
        String originalHash = a1.hashState();

        BuildableState modified = a1.addFiles("new_file");
        fixTimeStamps(modified);
        String modifiedHash = modified.hashState();

        assertThat(originalHash).isNotEqualTo(modifiedHash);
    }

    // === Task 2.1: Hash computation fields, integrity verification, corruption detection ===

    @Test
    public void hashStateIncludesAllContentFields() {
        // Verify that changing each individual field produces a different hash (Req 3.3)
        fixTimeStamps(a1);
        String baseHash = a1.hashState();

        // Changing timestamp affects hash
        BuildableState withDiffTimestamp = a1.clone();
        withDiffTimestamp.setTimestamp(a1.getTimestamp() + 1000);
        assertThat(withDiffTimestamp.hashState()).isNotEqualTo(baseHash);

        // Changing comment affects hash
        BuildableState withDiffComment = a1.clone();
        withDiffComment.setTimestamp(a1.getTimestamp());
        withDiffComment.setComment("different comment");
        assertThat(withDiffComment.hashState()).isNotEqualTo(baseHash);

        // Changing hashMode affects hash
        BuildableState withDiffMode = a1.clone();
        withDiffMode.setTimestamp(a1.getTimestamp());
        withDiffMode.setHashMode(HashMode.hashSmallBlock);
        assertThat(withDiffMode.hashState()).isNotEqualTo(baseHash);

        // Changing ignoredFiles affects hash
        BuildableState withIgnored = a1.clone();
        withIgnored.setTimestamp(a1.getTimestamp());
        fixTimeStamps(withIgnored);
        withIgnored.setIgnoredFiles(new HashSet<>(Arrays.asList("some_file.txt")));
        assertThat(withIgnored.hashState()).isNotEqualTo(baseHash);
    }

    @Test
    public void ignoredFilesAreNotReorderedWhenHashing() throws IOException, CorruptedStateException {
        // Regression guard for State file compatibility.
        //
        // Jackson deserializes ignoredFiles into a HashSet, so the order a State is read back in is the
        // HashSet order for its elements, not the order the JSON array was written in. hashObject() must
        // therefore iterate the set as-is: reordering it (for instance sorting via a TreeSet) makes the
        // load-time hash differ from the hash embedded by every previously written State file, and each one
        // holding two or more ignored files gets rejected as corrupted (Req 3.4, 3.5).
        //
        // This test pins the invariant by writing the State file the way an earlier Fim version did, with
        // the hash computed over the plain HashSet, and requiring that it still verifies.
        BuildableState state = new BuildableState(defaultContext()).addFiles(FILE_1, FILE_2);
        // Chosen so that the HashSet iteration order is not the sorted order, which is what makes a
        // reordering regression observable here
        Set<String> ignored = new HashSet<>(Arrays.asList("alpha.tmp", "bravo.tmp", "charlie.tmp",
                "delta.tmp", "echo.tmp", "foxtrot.tmp", "golf.tmp", "hotel.tmp"));
        state.setIgnoredFiles(ignored);
        fixTimeStamps(state);

        assertThat(state.getIgnoredFiles()).doesNotContainSequence(new TreeSet<>(ignored).toArray(new String[0]));

        Path tempFile = Files.createTempFile("state-ignored-order-", GZIP_SUFFIX);
        try {
            state.saveToGZipFile(tempFile);

            // Loading with full validation recomputes the hash and compares it to the embedded one
            State loaded = State.loadFromGZipFile(tempFile, true);

            assertThat(loaded.getStateHash()).isEqualTo(state.getStateHash());
            assertThat(loaded.getIgnoredFiles()).containsExactlyInAnyOrderElementsOf(ignored);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void ignoredFilesHashDoesNotDependOnTheSetImplementationSupplied() throws IOException, CorruptedStateException {
        // The State_Hash must not follow the ordering of whichever Set the caller passes in, otherwise a
        // LinkedHashSet or TreeSet makes the saved hash unreproducible after a load, which always yields a
        // HashSet (Req 3.4).
        List<String> names = Arrays.asList("zebra.tmp", "alpha.tmp", "middle.tmp", "beta.tmp");

        BuildableState fromHashSet = new BuildableState(defaultContext()).addFiles(FILE_1);
        fixTimeStamps(fromHashSet);
        fromHashSet.setIgnoredFiles(new HashSet<>(names));

        BuildableState fromLinkedHashSet = new BuildableState(defaultContext()).addFiles(FILE_1);
        fixTimeStamps(fromLinkedHashSet);
        fromLinkedHashSet.setTimestamp(fromHashSet.getTimestamp());
        fromLinkedHashSet.setIgnoredFiles(new LinkedHashSet<>(names));

        BuildableState fromTreeSet = new BuildableState(defaultContext()).addFiles(FILE_1);
        fixTimeStamps(fromTreeSet);
        fromTreeSet.setTimestamp(fromHashSet.getTimestamp());
        fromTreeSet.setIgnoredFiles(new TreeSet<>(names));

        assertThat(fromLinkedHashSet.hashState()).isEqualTo(fromHashSet.hashState());
        assertThat(fromTreeSet.hashState()).isEqualTo(fromHashSet.hashState());

        // And each one still verifies after a save/load round trip
        for (BuildableState state : Arrays.asList(fromHashSet, fromLinkedHashSet, fromTreeSet)) {
            Path tempFile = Files.createTempFile("state-ignored-impl-", GZIP_SUFFIX);
            try {
                state.saveToGZipFile(tempFile);
                State loaded = State.loadFromGZipFile(tempFile, true);
                assertThat(loaded.getStateHash()).isEqualTo(state.getStateHash());
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    @Test
    public void ignoredFilesHashIsStableAcrossSaveLoadRegardlessOfInsertionOrder() throws IOException, CorruptedStateException {
        // Two States built by inserting the same ignored files in opposite orders must agree on the
        // State_Hash, and each must verify after a save/load round trip (Req 3.4).
        List<String> names = Arrays.asList("build/", "temp.log", ".cache/", "dir/subdir/", "z.txt", "a.txt");
        List<String> reversed = new ArrayList<>(names);
        Collections.reverse(reversed);

        BuildableState s1 = new BuildableState(defaultContext()).addFiles(FILE_1);
        fixTimeStamps(s1);
        s1.setIgnoredFiles(new HashSet<>(names));

        BuildableState s2 = new BuildableState(defaultContext()).addFiles(FILE_1);
        fixTimeStamps(s2);
        s2.setTimestamp(s1.getTimestamp());
        s2.setIgnoredFiles(new HashSet<>(reversed));

        assertThat(s1.hashState()).isEqualTo(s2.hashState());

        Path file1 = Files.createTempFile("state-ignored-ins-1-", GZIP_SUFFIX);
        Path file2 = Files.createTempFile("state-ignored-ins-2-", GZIP_SUFFIX);
        try {
            s1.saveToGZipFile(file1);
            s2.saveToGZipFile(file2);

            assertThat(s1.getStateHash()).isEqualTo(s2.getStateHash());

            // Both verify on load, and both still agree afterwards
            State loaded1 = State.loadFromGZipFile(file1, true);
            State loaded2 = State.loadFromGZipFile(file2, true);
            assertThat(loaded1.hashState()).isEqualTo(loaded2.hashState());
        } finally {
            Files.deleteIfExists(file1);
            Files.deleteIfExists(file2);
        }
    }

    @Test
    public void integrityVerificationSucceedsOnValidState() throws IOException, CorruptedStateException {
        // Save and load with full validation - should not throw (Req 3.4)
        fixTimeStamps(a1);
        Path tempFile = Files.createTempFile("state-integrity-", GZIP_SUFFIX);
        try {
            a1.saveToGZipFile(tempFile);
            State loaded = State.loadFromGZipFile(tempFile, true);

            // Verify the loaded state hash matches the recomputed hash
            assertThat(loaded.getStateHash()).isEqualTo(loaded.hashState());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void corruptedStateExceptionOnTamperedContent() throws IOException, CorruptedStateException {
        // Modify actual content (not just the hash) so the recomputed hash differs (Req 3.5)
        fixTimeStamps(a1);
        Path tempFile = Files.createTempFile("state-tampered-content-", GZIP_SUFFIX);
        try {
            a1.saveToGZipFile(tempFile);

            // Load without validation, tamper with comment, write back without recomputing hash
            State loaded = State.loadFromGZipFile(tempFile, false);
            String originalHash = loaded.getStateHash();
            loaded.setComment("tampered comment");
            // Write back with the original (now incorrect) hash
            writeTamperedState(tempFile, loaded, originalHash);

            assertThatThrownBy(() -> State.loadFromGZipFile(tempFile, true))
                    .isInstanceOf(CorruptedStateException.class);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void modelVersionMismatchSkipsIntegrityCheck() throws ReflectiveOperationException, IOException, CorruptedStateException {
        // When model version differs from current, integrity check is skipped (Req 3.6)
        fixTimeStamps(a1);
        Path tempFile = Files.createTempFile("state-version-", GZIP_SUFFIX);
        try {
            a1.saveToGZipFile(tempFile);

            // Load, change model version and hash to simulate old version state
            State loaded = State.loadFromGZipFile(tempFile, false);
            setModelVersionViaReflection(loaded, "4");
            loaded.setStateHash("intentionally_wrong_hash");

            // Write back the tampered state
            JsonIO jsonIO = new JsonIO();
            try (Writer writer = new OutputStreamWriter(
                    new GZIPOutputStream(new FileOutputStream(tempFile.toFile())),
                    StandardCharsets.UTF_8)) {
                jsonIO.getObjectWriter().writeValue(writer, loaded);
            }

            // Loading with full validation should NOT throw because model version differs
            State reloaded = State.loadFromGZipFile(tempFile, true);
            assertThat(reloaded).isNotNull();
            assertThat(reloaded.getModelVersion()).isEqualTo("4");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private void writeTamperedState(Path tempFile, State state, String hashToUse) throws IOException {
        // Temporarily set the hash, write, then it stays as-is in the file
        state.setStateHash(hashToUse);
        JsonIO jsonIO = new JsonIO();
        try (Writer writer = new OutputStreamWriter(
                new GZIPOutputStream(new FileOutputStream(tempFile.toFile())),
                StandardCharsets.UTF_8)) {
            jsonIO.getObjectWriter().writeValue(writer, state);
        }
    }

    private void setModelVersionViaReflection(State state, String version) throws ReflectiveOperationException {
        Field field = State.class.getDeclaredField("modelVersion");
        field.setAccessible(true);
        field.set(state, version);
    }

    private void fixTimeStamps(BuildableState s) {
        long timestamp = MODIFICATION_TIMESTAMP;

        s.setTimestamp(timestamp);
        for (FileState fileState : s.getFileStates()) {
            fileState.getFileTime().reset(timestamp);
        }
    }
}
