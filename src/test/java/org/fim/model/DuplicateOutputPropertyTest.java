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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.fim.internal.DuplicateOutputGenerator;
import org.fim.tooling.RandomInputs;
import org.fim.util.JsonIO;
import org.fim.util.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Property-based tests for DuplicateOutputGenerator output formats.
 * Feature: duplicate-detection
 */
public class DuplicateOutputPropertyTest {

    private static final String LOWER_ALPHA = "abcdefghijklmnopqrstuvwxyz";

    // ---- Helpers ----

    private FileState makeFileState(String fileName, long fileLength, String hashSuffix) {
        FileHash hash = new FileHash("small_" + hashSuffix, "medium_" + hashSuffix, "full_" + hashSuffix);
        return new FileState(fileName, fileLength, new FileTime(System.currentTimeMillis()), hash, null);
    }

    private List<FileState> makeDuplicateGroup(int count, long fileSize, String hashSuffix, String pathPrefix) {
        List<FileState> files = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String name = pathPrefix + "/file_" + hashSuffix + "_" + i + ".txt";
            files.add(makeFileState(name, fileSize, hashSuffix));
        }
        return files;
    }

    private DuplicateResult buildResult(Context context, List<int[]> groupSpecs) {
        DuplicateResult result = new DuplicateResult(context);
        for (int i = 0; i < groupSpecs.size(); i++) {
            int count = groupSpecs.get(i)[0];
            int size = groupSpecs.get(i)[1];
            List<FileState> files = makeDuplicateGroup(count, size, "out_" + i, "dir" + i);
            result.addDuplicatedFiles(files);
        }
        return result;
    }

    private String captureOutput(Runnable action) {
        PrintStream originalOut = Logger.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream captureStream = new PrintStream(baos);
        Logger.out = captureStream;
        try {
            action.run();
        } finally {
            Logger.out = originalOut;
        }
        return baos.toString();
    }

    @Test
    void humanFormatIsNotRenderedByTheGenerator() {
        // The generator only handles the machine-readable formats. The human format is produced by
        // DuplicateResult.displayAndRemoveDuplicates(), which is why it never delegates here for it.
        // Pinning this keeps a second, divergent human renderer from being reintroduced.
        Context context = new Context();
        context.setOutputType(OutputType.human);
        DuplicateResult result = buildResult(context, List.of(new int[] {2, 100}));
        DuplicateOutputGenerator generator = new DuplicateOutputGenerator(context);

        assertThatThrownBy(() -> generator.generate(result))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DuplicateResult");
    }

    @Test
    void humanFormatGoesThroughDuplicateResultInstead() {
        // Counterpart to the above: with the human output type, DuplicateResult renders the summary itself
        // rather than delegating to the generator, so no exception escapes.
        Context context = new Context();
        context.setOutputType(OutputType.human);
        DuplicateResult result = buildResult(context, List.of(new int[] {2, 100}));

        String output = captureOutput(result::displayAndRemoveDuplicates);

        assertThat(output).contains("duplicate");
    }

    // Feature: duplicate-detection, Property 9: CSV output row count
    // **Validates: Requirements 6.1, 6.2**
    @ParameterizedTest
    @MethodSource("groupSpecs")
    void csvOutputRowCount(List<int[]> groupSpecs) {
        Context context = new Context();
        context.setOutputType(OutputType.csv);
        DuplicateResult result = buildResult(context, groupSpecs);

        String output = captureOutput(() -> {
            DuplicateOutputGenerator generator = new DuplicateOutputGenerator(context);
            generator.generate(result);
        });

        String[] lines = output.split("\n");

        // Count total files across all duplicate sets
        int totalFiles = 0;
        for (DuplicateSet ds : result.getDuplicateSets()) {
            totalFiles += ds.getDuplicatedFilesCount();
        }

        // Row count = 1 header + totalFiles data rows
        assertThat(lines.length).isEqualTo(1 + totalFiles);

        // Verify header
        assertThat(lines[0]).contains("SetIndex");
        assertThat(lines[0]).contains("FileIndex");
    }

    // Feature: duplicate-detection, Property 10: Path and extension extraction
    // **Validates: Requirements 6.3**
    @ParameterizedTest
    @MethodSource("fileNames")
    void pathAndExtensionExtraction(String fileName) {
        // Use DuplicateOutputGenerator indirectly by creating a result and checking CSV output
        Context context = new Context();
        context.setOutputType(OutputType.csv);

        // Create a duplicate set with 2 files using the generated fileName
        List<FileState> files = new ArrayList<>();
        files.add(makeFileState(fileName, 100, "ext_test"));
        files.add(makeFileState(fileName + "_dup", 100, "ext_test"));

        DuplicateResult result = new DuplicateResult(context);
        result.addDuplicatedFiles(files);

        String output = captureOutput(() -> {
            DuplicateOutputGenerator generator = new DuplicateOutputGenerator(context);
            generator.generate(result);
        });

        // Verify path extraction: everything before last /
        String expectedPath;
        int lastSlash = fileName.lastIndexOf("/");
        if (lastSlash == -1) {
            expectedPath = "";
        } else {
            expectedPath = fileName.substring(0, lastSlash);
        }

        // Verify extension extraction: lowercase substring after last .
        String expectedExt;
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot == -1) {
            expectedExt = "";
        } else {
            expectedExt = fileName.substring(lastDot + 1).toLowerCase();
        }

        // Parse CSV lines (skip header)
        String[] lines = output.split("\n");
        assertThat(lines.length).isGreaterThanOrEqualTo(2);

        // First data row corresponds to our fileName
        String firstDataRow = lines[1];
        // CSV format: SetIndex, FileIndex, WastedSpace, FilePath, FileName, FileLength, FileType
        // The path and type should match our expectations
        assertThat(firstDataRow).contains(expectedPath);
        assertThat(firstDataRow).contains(expectedExt);
    }

    // Feature: duplicate-detection, Property 11: JSON serialization round-trip
    // **Validates: Requirements 7.1, 7.2**
    @ParameterizedTest
    @MethodSource("groupSpecs")
    @SuppressWarnings("unchecked")
    void jsonSerializationRoundTrip(List<int[]> groupSpecs) throws Exception {
        Context context = new Context();
        context.setOutputType(OutputType.json);
        DuplicateResult result = buildResult(context, groupSpecs);

        String output = captureOutput(() -> {
            DuplicateOutputGenerator generator = new DuplicateOutputGenerator(context);
            generator.generate(result);
        });

        // Deserialize JSON
        ObjectMapper mapper = new JsonIO().getObjectMapper();
        TypeReference<List<Map<String, Object>>> listOfSets = new TypeReference<>() { };
        List<Map<String, Object>> jsonList = mapper.readValue(output, listOfSets);

        // Same number of sets
        assertThat(jsonList.size()).isEqualTo(result.getDuplicateSets().size());

        for (int i = 0; i < jsonList.size(); i++) {
            Map<String, Object> jsonSet = jsonList.get(i);
            DuplicateSet ds = result.getDuplicateSets().get(i);

            // wastedSpace matches
            long jsonWastedSpace = ((Number) jsonSet.get("wastedSpace")).longValue();
            assertThat(jsonWastedSpace).isEqualTo(ds.getWastedSpace());

            // fileList size matches
            List<Map<String, Object>> fileList = (List<Map<String, Object>>) jsonSet.get("fileList");
            assertThat(fileList.size()).isEqualTo(ds.getDuplicatedFilesCount());

            // Each file has name, length, path, type fields
            for (int j = 0; j < fileList.size(); j++) {
                Map<String, Object> fileMap = fileList.get(j);
                FileState fs = ds.getDuplicatedFiles().get(j);

                assertThat(fileMap.get("name")).isEqualTo(fs.getFileName());
                assertThat(((Number) fileMap.get("length")).longValue()).isEqualTo(fs.getFileLength());
                assertThat(fileMap).containsKey("path");
                assertThat(fileMap).containsKey("type");
            }
        }
    }

    // ---- Providers ----

    /**
     * Groups of {@code {fileCount, fileSize}}, 1 to 4 groups per list, file counts in 2..5 and sizes in
     * 1..10000. The smallest and largest shapes are listed explicitly, the rest is a seeded sample.
     */
    static Stream<List<int[]>> groupSpecs() {
        List<List<int[]>> values = new ArrayList<>();
        values.add(List.of(new int[] {2, 1}));
        values.add(List.of(new int[] {5, 10_000}, new int[] {5, 10_000},
                new int[] {5, 10_000}, new int[] {5, 10_000}));
        Random random = RandomInputs.random();
        for (int i = 0; i < 100; i++) {
            int groupCount = RandomInputs.intBetween(random, 1, 4);
            List<int[]> groups = new ArrayList<>();
            for (int g = 0; g < groupCount; g++) {
                int count = RandomInputs.intBetween(random, 2, 5);
                int size = RandomInputs.intBetween(random, 1, 10_000);
                groups.add(new int[] {count, size});
            }
            values.add(groups);
        }
        return values.stream();
    }

    /**
     * File names with an optional directory part and an optional extension, as the generator used to
     * produce them. Every directory and extension combination appears, with both ends of the base name
     * length range plus sampled lengths in between.
     */
    static Stream<String> fileNames() {
        // Generate file names with optional path and extension
        String[] dirParts = {"", "docs/", "src/main/", "data/sub/"};
        String[] extensions = {"", ".txt", ".TXT", ".jpg", ".PDF", ".java"};
        List<String> values = new ArrayList<>();
        Random random = RandomInputs.random();
        for (String dirPart : dirParts) {
            for (String extension : extensions) {
                values.add(dirPart + RandomInputs.stringOf(random, LOWER_ALPHA, 1, 1) + extension);
                values.add(dirPart + RandomInputs.stringOf(random, LOWER_ALPHA, 10, 10) + extension);
                values.add(dirPart + RandomInputs.stringOf(random, LOWER_ALPHA, 1, 10) + extension);
                values.add(dirPart + RandomInputs.stringOf(random, LOWER_ALPHA, 1, 10) + extension);
            }
        }
        return values.stream();
    }
}
