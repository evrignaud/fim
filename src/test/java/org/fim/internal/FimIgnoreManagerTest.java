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

import org.fim.model.Context;
import org.fim.model.FilePattern;
import org.fim.model.FimIgnore;
import org.fim.tooling.RepositoryTool;
import org.fim.tooling.StateAssert;
import org.fim.util.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

import static java.nio.file.StandardOpenOption.CREATE;
import static org.assertj.core.api.Assertions.assertThat;

public class FimIgnoreManagerTest extends StateAssert {
    private FimIgnoreManager cut;

    private BasicFileAttributes fileAttributes;
    private Path rootDir;
    private RepositoryTool tool;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        tool = new RepositoryTool(testInfo);
        rootDir = tool.getRootDir();

        cut = new FimIgnoreManager(tool.getContext());
    }

    @Test
    public void filesCanBeIgnored() {
        fileAttributes = Mockito.mock(BasicFileAttributes.class);
        Mockito.when(fileAttributes.isDirectory()).thenReturn(false);

        FimIgnore fimIgnore = new FimIgnore();
        ignoreFile(fimIgnore, "foo");
        ignoreFile(fimIgnore, "bar*");
        ignoreFile(fimIgnore, "*baz*");
        ignoreFile(fimIgnore, "*.mp3");
        ignoreFile(fimIgnore, "$Data[1]|2(3)*");
        ignoreFile(fimIgnore, "****qux****");

        assertFileIgnored("foo", fimIgnore);
        assertFileNotIgnored("a_foo", fimIgnore);

        assertFileIgnored("bar_yes", fimIgnore);
        assertFileNotIgnored("yes_bar", fimIgnore);

        assertFileIgnored("no_baz_yes", fimIgnore);

        assertFileIgnored("track12.mp3", fimIgnore);
        assertFileNotIgnored("track12_mp3", fimIgnore);

        assertFileIgnored("$Data[1]|2(3)_file", fimIgnore);

        assertFileIgnored("****qux****", fimIgnore);
    }

    @Test
    public void fileToIgnoreListDontAllowDuplicates() {
        FimIgnore fimIgnore = new FimIgnore();
        ignoreFile(fimIgnore, "foo");
        ignoreFile(fimIgnore, "foo");

        assertThat(fimIgnore.getFilesToIgnoreLocally().size()).isEqualTo(1);
    }

    @Test
    public void canLoadCorrectlyAFimIgnore() throws IOException {
        FimIgnore fimIgnore = cut.loadFimIgnore(rootDir);
        assertThat(fimIgnore.getFilesToIgnoreLocally().size()).isEqualTo(0);
        assertThat(fimIgnore.getFilesToIgnoreInAllDirectories().size()).isEqualTo(0);

        String fileContent = """
                **/*.mp3
                *.mp4
                **/.git
                foo
                **/bar""";
        Files.write(rootDir.resolve(".fimignore"), fileContent.getBytes(), CREATE);

        fimIgnore = cut.loadFimIgnore(rootDir);
        assertThat(fimIgnore.getFilesToIgnoreLocally().toString()).isEqualTo(
                "[FilePattern{fileName=foo, compiled=^foo$}," +
                " FilePattern{fileName=*.mp4, compiled=^.*\\.mp4$}]");

        assertThat(fimIgnore.getFilesToIgnoreInAllDirectories().toString()).isEqualTo(
                "[FilePattern{fileName=bar, compiled=^bar$}," +
                " FilePattern{fileName=.git, compiled=^\\.git$}," +
                " FilePattern{fileName=*.mp3, compiled=^.*\\.mp3$}]");
    }

    @Test
    public void emptyFimIgnoreFileResultsInEmptyPatterns() throws IOException {
        // Write an empty .fimignore file
        Files.write(rootDir.resolve(".fimignore"), "".getBytes(), CREATE);

        FimIgnore fimIgnore = cut.loadFimIgnore(rootDir);
        assertThat(fimIgnore.getFilesToIgnoreLocally()).isEmpty();
        assertThat(fimIgnore.getFilesToIgnoreInAllDirectories()).isEmpty();
    }

    @Test
    public void ioErrorDuringLoadReturnsEmptyFimIgnoreAndLogsError() throws IOException {
        // Create .fimignore as a directory instead of a file to trigger IOException on readAllLines
        Path dotFimIgnore = rootDir.resolve(".fimignore");
        Files.createDirectories(dotFimIgnore);

        // Capture Logger output
        ByteArrayOutputStream logOutput = new ByteArrayOutputStream();
        PrintStream originalOut = Logger.out;
        Logger.out = new PrintStream(logOutput);
        try {
            FimIgnore fimIgnore = cut.loadFimIgnore(rootDir);

            // Req 1.5: returns empty FimIgnore on I/O error
            assertThat(fimIgnore.getFilesToIgnoreLocally()).isEmpty();
            assertThat(fimIgnore.getFilesToIgnoreInAllDirectories()).isEmpty();

            // Req 1.5: logs error message including file path
            String logMessage = logOutput.toString();
            assertThat(logMessage).contains("Unable to read file");
            assertThat(logMessage).contains(".fimignore");
        } finally {
            Logger.out = originalOut;
            // Clean up the directory so @BeforeEach cleanup works
            Files.delete(dotFimIgnore);
        }
    }

    @Test
    public void subdirectoryParentTraversalMergesAllDirectoriesPatterns() throws IOException {
        // Set up directory structure: rootDir/sub1/sub2
        Path sub1 = rootDir.resolve("sub1");
        Path sub2 = sub1.resolve("sub2");
        Files.createDirectories(sub2);

        // Create .fimignore in rootDir with an all-directories pattern
        Files.write(rootDir.resolve(".fimignore"), "**/*.log\n".getBytes(), CREATE);

        // Create .fimignore in sub1 with another all-directories pattern and a local pattern
        Files.write(sub1.resolve(".fimignore"), "**/*.tmp\nlocalonly.txt\n".getBytes(), CREATE);

        // Create a context invoked from sub2 subdirectory
        Context subDirContext = tool.createContext(tool.getContext().getHashMode(), false);
        subDirContext.setCurrentDirectory(sub2);
        subDirContext.setRepositoryRootDir(rootDir);
        subDirContext.setInvokedFromSubDirectory(true);

        FimIgnoreManager subDirManager = new FimIgnoreManager(subDirContext);

        // Req 1.6: loadInitialFimIgnore traverses parents up to root, merging allDirectories patterns
        FimIgnore initialFimIgnore = subDirManager.loadInitialFimIgnore();

        // Should contain all-directories patterns from both rootDir and sub1
        assertThat(initialFimIgnore.getFilesToIgnoreInAllDirectories())
                .extracting(FilePattern::getFileName)
                .contains("*.log", "*.tmp");

        // Local patterns from sub1 should NOT be inherited (only allDirectories propagate)
        assertThat(initialFimIgnore.getFilesToIgnoreInAllDirectories())
                .extracting(FilePattern::getFileName)
                .doesNotContain("localonly.txt");
    }

    @Test
    public void loadLocalIgnoreMergesParentAllDirectoriesPatterns() throws IOException {
        // Req 1.4: loadLocalIgnore merges parent's allDirectories into child
        Path subDir = rootDir.resolve("child");
        Files.createDirectories(subDir);

        // Create parent FimIgnore with allDirectories patterns
        FimIgnore parentFimIgnore = new FimIgnore();
        parentFimIgnore.getFilesToIgnoreInAllDirectories().add(new FilePattern("*.log"));
        parentFimIgnore.getFilesToIgnoreInAllDirectories().add(new FilePattern("*.tmp"));

        // Create a .fimignore in child with a local pattern
        Files.write(subDir.resolve(".fimignore"), "localfile.txt\n".getBytes(), CREATE);

        FimIgnore childFimIgnore = cut.loadLocalIgnore(subDir, parentFimIgnore);

        // Child should have parent's allDirectories patterns
        assertThat(childFimIgnore.getFilesToIgnoreInAllDirectories())
                .extracting(FilePattern::getFileName)
                .contains("*.log", "*.tmp");

        // Child should have its own local pattern
        assertThat(childFimIgnore.getFilesToIgnoreLocally())
                .extracting(FilePattern::getFileName)
                .contains("localfile.txt");
    }

    // --- Task 4.1: Verify isIgnored() evaluation order and ignoreThisFiles() logic ---

    @Test
    public void hardcodedDirectoryNamesWithNonDirectoryAttributesAreNotIgnored() {
        // Req 2.1: Hardcoded directories (.fim, .git, .svn, .cvs) should only be ignored
        // when the attributes indicate a directory. Files with those names should NOT be
        // auto-ignored by the hardcoded check.
        BasicFileAttributes nonDirAttributes = Mockito.mock(BasicFileAttributes.class);
        Mockito.when(nonDirAttributes.isDirectory()).thenReturn(false);

        FimIgnore emptyFimIgnore = new FimIgnore();

        for (String dirName : FimIgnoreManager.IGNORED_DIRECTORIES) {
            assertThat(cut.isIgnored(dirName, nonDirAttributes, emptyFimIgnore))
                    .as("File named '%s' with non-directory attributes should NOT be ignored", dirName)
                    .isFalse();
        }
    }

    @Test
    public void hardcodedDirectoryNamesWithDirectoryAttributesAreIgnored() {
        // Req 2.1: Hardcoded directories should be ignored when attributes indicate a directory,
        // regardless of FimIgnore patterns.
        BasicFileAttributes dirAttributes = Mockito.mock(BasicFileAttributes.class);
        Mockito.when(dirAttributes.isDirectory()).thenReturn(true);

        FimIgnore emptyFimIgnore = new FimIgnore();

        for (String dirName : FimIgnoreManager.IGNORED_DIRECTORIES) {
            assertThat(cut.isIgnored(dirName, dirAttributes, emptyFimIgnore))
                    .as("Directory named '%s' should always be ignored", dirName)
                    .isTrue();
        }
    }

    @Test
    public void isIgnoredEvaluatesAllDirectoriesPatternsBeforeLocalPatterns() {
        // Req 2.2: isIgnored checks allDirectories patterns first, then local patterns.
        // A file matching an allDirectories pattern should be ignored even if local set is empty.
        BasicFileAttributes attrs = Mockito.mock(BasicFileAttributes.class);
        Mockito.when(attrs.isDirectory()).thenReturn(false);

        FimIgnore fimIgnore = new FimIgnore();
        fimIgnore.getFilesToIgnoreInAllDirectories().add(new FilePattern("*.log"));
        // Local set is empty

        assertThat(cut.isIgnored("debug.log", attrs, fimIgnore))
                .as("File matching allDirectories pattern should be ignored")
                .isTrue();

        // A file matching only a local pattern should also be ignored
        FimIgnore fimIgnore2 = new FimIgnore();
        fimIgnore2.getFilesToIgnoreLocally().add(new FilePattern("local.txt"));
        // allDirectories set is empty

        assertThat(cut.isIgnored("local.txt", attrs, fimIgnore2))
                .as("File matching local pattern should be ignored")
                .isTrue();

        // A file matching neither should not be ignored
        assertThat(cut.isIgnored("other.txt", attrs, fimIgnore2))
                .as("File matching no pattern should not be ignored")
                .isFalse();
    }

    @Test
    public void ignoreThisFilesAppendsTrailingSlashForDirectories() throws IOException {
        // Req 2.4: Directories get trailing '/' in the ignoredFiles set, files do not.
        Path testDir = rootDir.resolve("ignored_dir");
        Files.createDirectories(testDir);

        Path testFile = rootDir.resolve("ignored_file.txt");
        Files.write(testFile, "content".getBytes(), CREATE);

        BasicFileAttributes dirAttrs = Mockito.mock(BasicFileAttributes.class);
        Mockito.when(dirAttrs.isDirectory()).thenReturn(true);

        BasicFileAttributes fileAttrs = Mockito.mock(BasicFileAttributes.class);
        Mockito.when(fileAttrs.isDirectory()).thenReturn(false);

        cut.ignoreThisFiles(testDir, dirAttrs);
        cut.ignoreThisFiles(testFile, fileAttrs);

        Set<String> ignoredFiles = cut.getIgnoredFiles();

        // Find the entries for our test paths
        boolean foundDirWithSlash = false;
        boolean foundFileWithoutSlash = false;
        for (String ignored : ignoredFiles) {
            if (ignored.contains("ignored_dir")) {
                assertThat(ignored).as("Directory path should end with '/'").endsWith("/");
                foundDirWithSlash = true;
            }
            if (ignored.contains("ignored_file.txt")) {
                assertThat(ignored).as("File path should not end with '/'").doesNotEndWith("/");
                foundFileWithoutSlash = true;
            }
        }

        assertThat(foundDirWithSlash).as("Should have recorded the directory").isTrue();
        assertThat(foundFileWithoutSlash).as("Should have recorded the file").isTrue();
    }

    private void assertFileIgnored(String fileName, FimIgnore fimIgnore) {
        assertThat(cut.isIgnored(fileName, fileAttributes, fimIgnore)).isTrue();
    }

    private void assertFileNotIgnored(String fileName, FimIgnore fimIgnore) {
        assertThat(cut.isIgnored(fileName, fileAttributes, fimIgnore)).isFalse();
    }

    private void ignoreFile(FimIgnore fimIgnore, String fileNamePattern) {
        FilePattern filePattern;
        filePattern = new FilePattern(fileNamePattern);
        fimIgnore.getFilesToIgnoreLocally().add(filePattern);
    }
}
