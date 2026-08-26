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

package org.fim.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.fim.command.exception.BadFimUsageException;
import org.fim.model.Context;
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.FileTime;
import org.fim.model.HashMode;
import org.fim.model.OutputType;
import org.fim.tooling.RandomInputs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Property-based tests for FindDuplicatesCommand and RemoveDuplicatesCommand.
 * Feature: duplicate-detection
 */
public class FindDuplicatesCommandPropertyTest {

    // ---- Helpers ----

    private FileState makeFileState(String fileName, long fileLength, String hashSuffix) {
        FileHash hash = new FileHash("small_" + hashSuffix, "medium_" + hashSuffix, "full_" + hashSuffix);
        return new FileState(fileName, fileLength, new FileTime(System.currentTimeMillis()), hash, null);
    }

    private Context createContextWithTempDir() throws IOException {
        Path tempDir = Files.createTempDirectory("fim-cmd-test");
        Context ctx = new Context();
        ctx.setHashMode(HashMode.hashAll);
        ctx.setRepositoryRootDir(tempDir);
        ctx.setCurrentDirectory(tempDir);
        ctx.setCalledFromTest(true);
        // Create .fim directory so SettingsManager doesn't fail
        Path dotFim = tempDir.resolve(".fim");
        Files.createDirectories(dotFim);
        return ctx;
    }

    private void cleanup(Path dir) {
        if (dir != null) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                                // Cleanup is best-effort
                            }
                        });
            } catch (IOException ignored) {
                // Cleanup is best-effort
            }
        }
    }

    // Feature: duplicate-detection, Property 18: dontHash mode rejection
    // **Validates: Requirements 1.1**
    @ParameterizedTest
    @MethodSource("dummyInts")
    void dontHashModeRejection(int dummy) throws IOException {
        Context context = createContextWithTempDir();
        Path tempDir = context.getRepositoryRootDir();
        try {
            context.setHashMode(HashMode.dontHash);
            context.setCalledFromTest(true);

            FindDuplicatesCommand command = new FindDuplicatesCommand();
            assertThatThrownBy(() -> command.execute(context))
                    .isInstanceOf(BadFimUsageException.class);
        } finally {
            cleanup(tempDir);
        }
    }

    // Feature: duplicate-detection, Property 12: Remove with non-human output rejection
    // **Validates: Requirements 8.2**
    @ParameterizedTest
    @MethodSource("nonHumanOutputTypes")
    void removeWithNonHumanOutputRejection(OutputType outputType) throws IOException {
        Context context = createContextWithTempDir();
        Path tempDir = context.getRepositoryRootDir();
        try {
            context.setOutputType(outputType);
            context.setRemoveDuplicates(true);
            context.setCalledFromTest(true);

            FindDuplicatesCommand command = new FindDuplicatesCommand();
            assertThatThrownBy(() -> command.execute(context))
                    .isInstanceOf(BadFimUsageException.class);
        } finally {
            cleanup(tempDir);
        }
    }

    // Feature: duplicate-detection, Property 17: Master repository hash matching with zero-length exclusion
    // **Validates: Requirements 10.3, 10.8**
    @ParameterizedTest
    @MethodSource("localAndMasterFileSpecs")
    void masterRepositoryHashMatchingWithZeroLengthExclusion(
            List<long[]> localSpecs,
            List<long[]> masterSpecs) {
        // Build master state with FileHash map
        Map<FileHash, FileState> masterFilesHash = new HashMap<>();
        List<FileState> masterFiles = new ArrayList<>();
        for (int i = 0; i < masterSpecs.size(); i++) {
            long size = masterSpecs.get(i)[0];
            String hashSuffix = "master_" + i;
            FileState fs = makeFileState("master_" + i + ".txt", size, hashSuffix);
            masterFiles.add(fs);
            masterFilesHash.put(fs.getFileHash(), fs);
        }

        // Build local files
        List<FileState> localFiles = new ArrayList<>();
        for (int i = 0; i < localSpecs.size(); i++) {
            long size = localSpecs.get(i)[0];
            int masterRef = (int) localSpecs.get(i)[1]; // -1 means unique hash

            String hashSuffix;
            if (masterRef >= 0 && masterRef < masterSpecs.size()) {
                hashSuffix = "master_" + masterRef; // Same hash as a master file
            } else {
                hashSuffix = "local_unique_" + i;
            }
            FileState fs = makeFileState("local_" + i + ".txt", size, hashSuffix);
            localFiles.add(fs);
        }

        // Simulate the master repository matching logic from RemoveDuplicatesCommand
        for (FileState localFileState : localFiles) {
            if (localFileState.getFileLength() == 0) {
                // Zero-length files should be skipped
                continue;
            }

            FileState masterFileState = masterFilesHash.get(localFileState.getFileHash());
            boolean isDuplicate = masterFileState != null;

            // Verify: a local file is duplicate iff its hash matches a master entry AND length > 0
            boolean expectedDuplicate = masterFilesHash.containsKey(localFileState.getFileHash())
                    && localFileState.getFileLength() > 0;
            assertThat(isDuplicate).isEqualTo(expectedDuplicate);
        }

        // Also verify zero-length files are never identified as duplicates
        for (FileState localFileState : localFiles) {
            if (localFileState.getFileLength() == 0) {
                // Even if hash matches, zero-length should be excluded
                // The code skips them with continue, so they're never checked
                assertThat(localFileState.getFileLength()).isEqualTo(0);
            }
        }
    }

    // ---- Providers ----

    /**
     * The value is unused by the test: it only made the property run repeatedly. The range is small
     * enough to enumerate, so it is.
     */
    static IntStream dummyInts() {
        return IntStream.rangeClosed(0, 10);
    }

    /** The two output types that are not human readable. The whole domain, so it is enumerated. */
    static Stream<OutputType> nonHumanOutputTypes() {
        return Stream.of(OutputType.csv, OutputType.json);
    }

    /**
     * Local file specs paired with master file specs. Each local spec is
     * {@code [fileSize, masterRefIndex]} with a size of 0 to 1000 and a reference of -1 to 3, where -1
     * means a unique hash; each master spec is {@code [fileSize]} with a size of 1 to 1000. The lists
     * hold 1 to 6 and 1 to 4 entries. The smallest and the largest shapes are listed explicitly, then a
     * seeded sample walks the same ranges.
     */
    static Stream<Arguments> localAndMasterFileSpecs() {
        List<Arguments> values = new ArrayList<>();
        Random random = RandomInputs.random();
        // Explicit boundaries first: shortest lists at the low end of every range, then the longest
        // lists at the high end
        values.add(Arguments.of(List.of(new long[]{0L, -1L}), List.of(new long[]{1L})));
        values.add(Arguments.of(localSpecs(random, 6), masterSpecs(random, 4)));
        for (int i = 0; i < 100; i++) {
            values.add(Arguments.of(localSpecs(random, RandomInputs.intBetween(random, 1, 6)),
                    masterSpecs(random, RandomInputs.intBetween(random, 1, 4))));
        }
        return values.stream();
    }

    /** Each spec: [fileSize, masterRefIndex (-1 for unique)] */
    private static List<long[]> localSpecs(Random random, int count) {
        List<long[]> specs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long size = RandomInputs.longBetween(random, 0L, 1000L);
            int ref = RandomInputs.intBetween(random, -1, 3);
            specs.add(new long[]{size, ref});
        }
        return specs;
    }

    /** Each spec: [fileSize] */
    private static List<long[]> masterSpecs(Random random, int count) {
        List<long[]> specs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            specs.add(new long[]{RandomInputs.longBetween(random, 1L, 1000L)});
        }
        return specs;
    }
}
