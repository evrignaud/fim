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
import static java.nio.file.StandardOpenOption.CREATE;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import org.fim.model.Context;
import org.fim.model.FilePattern;
import org.fim.model.FimIgnore;
import org.fim.tooling.RandomInputs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for FimIgnoreManager.
 * Feature: file-ignore-system
 */
public class FimIgnoreManagerPropertyTest {

    /** Same character set the generated patterns were drawn from before the conversion. */
    private static final String PATTERN_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789*._-";
    private static final String FILE_NAME_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    private Path tempDir;
    private FimIgnoreManager manager;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("fim-ignore-prop-test");
        Context context = new Context();
        context.setCurrentDirectory(tempDir);
        context.setRepositoryRootDir(tempDir);
        manager = new FimIgnoreManager(context);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null) {
            // Clean up temp directory recursively
            try (Stream<Path> walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                // Cleanup is best-effort
                            }
                        });
            }
        }
    }

    // -------------------------------------------------------------------------
    // Property 1: Fimignore pattern classification
    // **Validates: Requirements 1.2, 1.3**
    // -------------------------------------------------------------------------
    @ParameterizedTest
    @MethodSource("patternLinePairs")
    void fimignorePatternClassification(List<String> localPatterns, List<String> allDirPatterns) throws IOException {

        // Build .fimignore content: allDirPatterns get **/ prefix, localPatterns don't
        StringBuilder content = new StringBuilder();
        for (String p : allDirPatterns) {
            content.append("**/").append(p).append("\n");
        }
        for (String p : localPatterns) {
            content.append(p).append("\n");
        }

        Path fimignore = tempDir.resolve(".fimignore");
        Files.deleteIfExists(fimignore);
        Files.write(fimignore, content.toString().getBytes(), CREATE);

        FimIgnore loaded = manager.loadFimIgnore(tempDir);

        // All-directories patterns should contain each allDirPattern (with prefix stripped)
        Set<String> allDirFileNames = loaded.getFilesToIgnoreInAllDirectories().stream()
                .map(FilePattern::getFileName)
                .collect(Collectors.toSet());
        for (String p : allDirPatterns) {
            assertThat(allDirFileNames).contains(p.trim());
        }

        // Local patterns should contain each localPattern
        Set<String> localFileNames = loaded.getFilesToIgnoreLocally().stream()
                .map(FilePattern::getFileName)
                .collect(Collectors.toSet());
        for (String p : localPatterns) {
            assertThat(localFileNames).contains(p.trim());
        }
    }

    // -------------------------------------------------------------------------
    // Property 2: All-directories pattern inheritance via merge
    // **Validates: Requirements 1.4**
    // -------------------------------------------------------------------------
    @ParameterizedTest
    @MethodSource("patternLines")
    void allDirectoriesPatternInheritanceViaMerge(List<String> parentPatterns) throws IOException {

        // Create a parent FimIgnore with allDirectories patterns
        FimIgnore parentFimIgnore = new FimIgnore();
        for (String p : parentPatterns) {
            parentFimIgnore.getFilesToIgnoreInAllDirectories().add(new FilePattern(p));
        }

        // Create a child directory
        Path childDir = tempDir.resolve("child");
        Files.createDirectories(childDir);

        // loadLocalIgnore for child should inherit parent's allDirectories patterns
        FimIgnore childFimIgnore = manager.loadLocalIgnore(childDir, parentFimIgnore);

        Set<String> childAllDirNames = childFimIgnore.getFilesToIgnoreInAllDirectories().stream()
                .map(FilePattern::getFileName)
                .collect(Collectors.toSet());

        for (String p : parentPatterns) {
            assertThat(childAllDirNames).contains(p.trim());
        }

        // Clean up child dir
        Files.deleteIfExists(childDir);
    }

    // -------------------------------------------------------------------------
    // Property 3: Hardcoded directories are always ignored
    // **Validates: Requirements 2.1**
    // -------------------------------------------------------------------------
    @ParameterizedTest
    @MethodSource("patternLines")
    void hardcodedDirectoriesAreAlwaysIgnored(List<String> randomPatterns) {

        // Build a random FimIgnore
        FimIgnore fimIgnore = new FimIgnore();
        for (String p : randomPatterns) {
            fimIgnore.getFilesToIgnoreLocally().add(new FilePattern(p));
        }

        BasicFileAttributes dirAttrs = mock(BasicFileAttributes.class);
        when(dirAttrs.isDirectory()).thenReturn(true);

        // Each hardcoded directory must be ignored regardless of FimIgnore content
        for (String dirName : FimIgnoreManager.IGNORED_DIRECTORIES) {
            assertThat(manager.isIgnored(dirName, dirAttrs, fimIgnore))
                    .as("Hardcoded directory '%s' should always be ignored", dirName)
                    .isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // Property 4: Pattern-based ignore evaluation
    // **Validates: Requirements 2.3**
    // -------------------------------------------------------------------------
    @ParameterizedTest
    @MethodSource("simpleFileNames")
    void patternBasedIgnoreEvaluation(String fileName) {

        // Create a FimIgnore where at least one pattern matches the file name
        FimIgnore fimIgnore = new FimIgnore();
        // Add the exact file name as a pattern — guaranteed to match
        fimIgnore.getFilesToIgnoreLocally().add(new FilePattern(fileName));

        BasicFileAttributes attrs = mock(BasicFileAttributes.class);
        when(attrs.isDirectory()).thenReturn(false);

        assertThat(manager.isIgnored(fileName, attrs, fimIgnore))
                .as("File '%s' matching a local pattern should be ignored", fileName)
                .isTrue();

        // Also test via allDirectories set
        FimIgnore fimIgnore2 = new FimIgnore();
        fimIgnore2.getFilesToIgnoreInAllDirectories().add(new FilePattern(fileName));

        assertThat(manager.isIgnored(fileName, attrs, fimIgnore2))
                .as("File '%s' matching an allDirectories pattern should be ignored", fileName)
                .isTrue();
    }

    // -------------------------------------------------------------------------
    // Property 5: Ignored directory paths have trailing slash
    // **Validates: Requirements 2.4**
    // -------------------------------------------------------------------------
    @ParameterizedTest
    @MethodSource("simpleFileNames")
    void ignoredDirectoryPathsHaveTrailingSlash(String name) throws IOException {

        // Create a fresh manager for each test to avoid accumulation
        Context ctx = new Context();
        ctx.setCurrentDirectory(tempDir);
        ctx.setRepositoryRootDir(tempDir);
        FimIgnoreManager localManager = new FimIgnoreManager(ctx);

        // Create actual directory and file in temp dir
        Path dir = tempDir.resolve(name + "_dir");
        Path file = tempDir.resolve(name + "_file.txt");
        Files.createDirectories(dir);
        if (!Files.exists(file)) {
            Files.write(file, "x".getBytes(), CREATE);
        }

        BasicFileAttributes dirAttrs = mock(BasicFileAttributes.class);
        when(dirAttrs.isDirectory()).thenReturn(true);

        BasicFileAttributes fileAttrs = mock(BasicFileAttributes.class);
        when(fileAttrs.isDirectory()).thenReturn(false);

        localManager.ignoreThisFiles(dir, dirAttrs);
        localManager.ignoreThisFiles(file, fileAttrs);

        Set<String> ignoredFiles = localManager.getIgnoredFiles();

        for (String ignored : ignoredFiles) {
            if (ignored.contains(name + "_dir")) {
                assertThat(ignored).as("Directory path should end with '/'").endsWith("/");
            }
            if (ignored.contains(name + "_file.txt")) {
                assertThat(ignored).as("File path should not end with '/'").doesNotEndWith("/");
            }
        }

        // Clean up
        Files.deleteIfExists(file);
        Files.deleteIfExists(dir);
    }

    // -------------------------------------------------------------------------
    // Providers
    // -------------------------------------------------------------------------

    /**
     * Lists of 1 to 5 ignore patterns, each 1 to 15 characters long. Random strings cannot be
     * enumerated, so they are sampled from a fixed seed with the smallest and largest shapes, plus the
     * pattern-significant characters, listed explicitly first. Generated patterns keep the constraints
     * the previous generator applied: never blank once trimmed and never carrying the all-directories
     * prefix, which the loader would otherwise strip.
     */
    static Stream<List<String>> patternLines() {
        List<List<String>> values = new ArrayList<>();
        values.addAll(explicitPatternLists());

        Random random = RandomInputs.random();
        for (int i = 0; i < 100; i++) {
            values.add(randomPatternList(random));
        }
        return values.stream();
    }

    /**
     * Pairs of pattern lists for the property that needs both a local and an all-directories list. The
     * explicit shapes are crossed with each other first, then random pairs are sampled.
     */
    static Stream<Arguments> patternLinePairs() {
        List<Arguments> values = new ArrayList<>();
        List<List<String>> explicit = explicitPatternLists();
        for (List<String> local : explicit) {
            for (List<String> allDir : explicit) {
                values.add(Arguments.of(local, allDir));
            }
        }

        Random random = RandomInputs.random();
        for (int i = 0; i < 100; i++) {
            values.add(Arguments.of(randomPatternList(random), randomPatternList(random)));
        }
        return values.stream();
    }

    /** File names of 1 to 20 lower-case alphanumeric characters, boundary lengths listed first. */
    static Stream<String> simpleFileNames() {
        List<String> values = new ArrayList<>();
        values.add("a");
        values.add("z9");
        values.add("abcdefghij0123456789");

        Random random = RandomInputs.random();
        for (int i = 0; i < 100; i++) {
            values.add(RandomInputs.stringOf(random, FILE_NAME_CHARS, 1, 20));
        }
        return values.stream();
    }

    /** Boundary shapes: shortest list with the shortest pattern, longest list with the longest ones. */
    private static List<List<String>> explicitPatternLists() {
        List<List<String>> explicit = new ArrayList<>();
        explicit.add(List.of("a"));
        explicit.add(List.of("*", ".", "_", "-", "**"));
        explicit.add(List.of("abcdefghij01234", "0123456789abcde", "a*b.c_d-e*f.g_h",
                "*.txt", "z"));
        return explicit;
    }

    private static List<String> randomPatternList(Random random) {
        int size = RandomInputs.intBetween(random, 1, 5);
        List<String> patterns = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            patterns.add(randomPattern(random));
        }
        return patterns;
    }

    /**
     * Keeps the constraints of the replaced generator: not blank after trimming and not starting with
     * the all-directories prefix.
     */
    private static String randomPattern(Random random) {
        String pattern;
        do {
            pattern = RandomInputs.stringOf(random, PATTERN_CHARS, 1, 15);
        } while (pattern.trim().isEmpty() || pattern.startsWith("**/"));
        return pattern;
    }
}
