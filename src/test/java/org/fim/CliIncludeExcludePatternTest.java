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

package org.fim;

import org.fim.model.Context;
import org.fim.model.FilePattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for CLI include/exclude pattern parsing (Fim.parseFilePatterns),
 * Context include/exclude pattern storage, and combined filtering logic.
 *
 * Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7
 */
public class CliIncludeExcludePatternTest {

    private Fim fim;
    private Method parseFilePatterns;

    @BeforeEach
    public void setUp() throws Exception {
        fim = new Fim();
        parseFilePatterns = Fim.class.getDeclaredMethod("parseFilePatterns", String.class);
        parseFilePatterns.setAccessible(true);
    }

    // --- Req 4.1: Colon-separated include pattern parsing ---

    @Test
    public void parseFilePatterns_singlePattern() throws Exception {
        @SuppressWarnings("unchecked")
        List<FilePattern> result = (List<FilePattern>) parseFilePatterns.invoke(fim, "*.txt");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).match("readme.txt")).isTrue();
        assertThat(result.get(0).match("readme.log")).isFalse();
    }

    @Test
    public void parseFilePatterns_multipleColonSeparatedPatterns() throws Exception {
        @SuppressWarnings("unchecked")
        List<FilePattern> result = (List<FilePattern>) parseFilePatterns.invoke(fim, "*.txt:*.log:*.csv");
        assertThat(result).hasSize(3);
        assertThat(result.get(0).match("file.txt")).isTrue();
        assertThat(result.get(1).match("error.log")).isTrue();
        assertThat(result.get(2).match("data.csv")).isTrue();
    }

    // --- Req 4.2: Colon-separated exclude pattern parsing ---

    @Test
    public void parseFilePatterns_twoPatterns() throws Exception {
        @SuppressWarnings("unchecked")
        List<FilePattern> result = (List<FilePattern>) parseFilePatterns.invoke(fim, "*.tmp:*.bak");
        assertThat(result).hasSize(2);
        assertThat(result.get(0).match("cache.tmp")).isTrue();
        assertThat(result.get(1).match("old.bak")).isTrue();
    }

    @Test
    public void parseFilePatterns_patternWithSpecialChars() throws Exception {
        @SuppressWarnings("unchecked")
        List<FilePattern> result = (List<FilePattern>) parseFilePatterns.invoke(fim, "file.txt:data[1]");
        assertThat(result).hasSize(2);
        assertThat(result.get(0).match("file.txt")).isTrue();
        assertThat(result.get(1).match("data[1]")).isTrue();
    }

    // --- Context include/exclude pattern storage ---

    @Test
    public void context_includePatterns_defaultNull() {
        Context context = new Context();
        assertThat(context.getIncludePatterns()).isNull();
    }

    @Test
    public void context_excludePatterns_defaultNull() {
        Context context = new Context();
        assertThat(context.getExcludePatterns()).isNull();
    }

    @Test
    public void context_setAndGetIncludePatterns() {
        Context context = new Context();
        List<FilePattern> patterns = Arrays.asList(new FilePattern("*.java"), new FilePattern("*.xml"));
        context.setIncludePatterns(patterns);
        assertThat(context.getIncludePatterns()).hasSize(2);
        assertThat(context.getIncludePatterns().get(0).match("Main.java")).isTrue();
    }

    @Test
    public void context_setAndGetExcludePatterns() {
        Context context = new Context();
        List<FilePattern> patterns = Arrays.asList(new FilePattern("*.class"), new FilePattern("*.jar"));
        context.setExcludePatterns(patterns);
        assertThat(context.getExcludePatterns()).hasSize(2);
        assertThat(context.getExcludePatterns().get(0).match("App.class")).isTrue();
    }

    // --- Req 4.6: Null include patterns → all files included (default true) ---

    @Test
    public void matchPatterns_nullInclude_defaultsToTrue() {
        boolean result = FilePattern.matchPatterns("anyfile.txt", null, true);
        assertThat(result).isTrue();
    }

    // --- Req 4.7: Null exclude patterns → no files excluded (default false) ---

    @Test
    public void matchPatterns_nullExclude_defaultsToFalse() {
        boolean result = FilePattern.matchPatterns("anyfile.txt", null, false);
        assertThat(result).isFalse();
    }

    // --- Req 4.3, 4.4, 4.5: Combined include+exclude filtering logic ---
    // Replicates the exact logic from StateGenerator.scanFileTree():
    //   FilePattern.matchPatterns(fileName, includePatterns, true) &&
    //   !FilePattern.matchPatterns(fileName, excludePatterns, false)

    @Test
    public void combinedFiltering_includeOnly_matchingFileAccepted() {
        List<FilePattern> include = Arrays.asList(new FilePattern("*.java"));
        boolean accepted = FilePattern.matchPatterns("Main.java", include, true)
                && !FilePattern.matchPatterns("Main.java", null, false);
        assertThat(accepted).isTrue();
    }

    @Test
    public void combinedFiltering_includeOnly_nonMatchingFileRejected() {
        List<FilePattern> include = Arrays.asList(new FilePattern("*.java"));
        boolean accepted = FilePattern.matchPatterns("readme.txt", include, true)
                && !FilePattern.matchPatterns("readme.txt", null, false);
        assertThat(accepted).isFalse();
    }

    @Test
    public void combinedFiltering_excludeOnly_matchingFileRejected() {
        List<FilePattern> exclude = Arrays.asList(new FilePattern("*.class"));
        boolean accepted = FilePattern.matchPatterns("App.class", null, true)
                && !FilePattern.matchPatterns("App.class", exclude, false);
        assertThat(accepted).isFalse();
    }

    @Test
    public void combinedFiltering_excludeOnly_nonMatchingFileAccepted() {
        List<FilePattern> exclude = Arrays.asList(new FilePattern("*.class"));
        boolean accepted = FilePattern.matchPatterns("Main.java", null, true)
                && !FilePattern.matchPatterns("Main.java", exclude, false);
        assertThat(accepted).isTrue();
    }

    @Test
    public void combinedFiltering_bothSet_fileMatchesBoth_rejected() {
        // File matches include AND exclude → excluded wins
        List<FilePattern> include = Arrays.asList(new FilePattern("*.java"));
        List<FilePattern> exclude = Arrays.asList(new FilePattern("Test*.java"));
        boolean accepted = FilePattern.matchPatterns("TestMain.java", include, true)
                && !FilePattern.matchPatterns("TestMain.java", exclude, false);
        assertThat(accepted).isFalse();
    }

    @Test
    public void combinedFiltering_bothSet_fileMatchesIncludeNotExclude_accepted() {
        List<FilePattern> include = Arrays.asList(new FilePattern("*.java"));
        List<FilePattern> exclude = Arrays.asList(new FilePattern("*.class"));
        boolean accepted = FilePattern.matchPatterns("Main.java", include, true)
                && !FilePattern.matchPatterns("Main.java", exclude, false);
        assertThat(accepted).isTrue();
    }

    @Test
    public void combinedFiltering_bothNull_allFilesAccepted() {
        // No include (default true) and no exclude (default false) → accepted
        boolean accepted = FilePattern.matchPatterns("anything.xyz", null, true)
                && !FilePattern.matchPatterns("anything.xyz", null, false);
        assertThat(accepted).isTrue();
    }

    @Test
    public void combinedFiltering_multipleIncludePatterns_anyMatchAccepted() {
        List<FilePattern> include = Arrays.asList(new FilePattern("*.java"), new FilePattern("*.xml"));
        boolean accepted = FilePattern.matchPatterns("pom.xml", include, true)
                && !FilePattern.matchPatterns("pom.xml", null, false);
        assertThat(accepted).isTrue();
    }

    @Test
    public void combinedFiltering_multipleExcludePatterns_anyMatchRejected() {
        List<FilePattern> exclude = Arrays.asList(new FilePattern("*.tmp"), new FilePattern("*.bak"));
        boolean accepted = FilePattern.matchPatterns("cache.bak", null, true)
                && !FilePattern.matchPatterns("cache.bak", exclude, false);
        assertThat(accepted).isFalse();
    }
}
