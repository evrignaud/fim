/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2017  Etienne Vrignaud
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

import org.fim.model.FilePattern;
import org.fim.model.FimIgnore;
import org.fim.tooling.RepositoryTool;
import org.fim.tooling.StateAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import static java.nio.file.StandardOpenOption.CREATE;
import static org.assertj.core.api.Assertions.assertThat;

public class FimIgnoreManagerTest extends StateAssert {
    private FimIgnoreManager cut;

    private BasicFileAttributes fileAttributes;
    private Path rootDir;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        RepositoryTool tool = new RepositoryTool(testInfo);
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
