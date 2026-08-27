/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2026 Etienne Vrignaud
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

package org.fim.integration;

import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import org.fim.tooling.FimCli;
import org.fim.tooling.IntegrationRepository;
import org.fim.tooling.TestTiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Files whose name or size is awkward, and which therefore tend to be where a tool like this breaks.
 *
 * A name is written into the State as json and read back out, compared as a string, and printed. Anything
 * outside plain ASCII goes through an encoding at each of those steps, and getting one of them wrong shows up
 * as a file reported modified on every run, or worse, as a file quietly matched against the wrong entry.
 *
 * Length zero is a separate matter: an empty file is the same as every other empty file, so it would otherwise
 * turn up as a duplicate of all of them.
 */
@Tag(TestTiers.INTEGRATION)
public class FileNamesAndSizesIT {
    private IntegrationRepository repository;
    private FimCli fim;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        repository = new IntegrationRepository(testInfo);
        fim = repository.fim();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Empty files
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Empty files are watched like any other, so the State has to hold them even though there is no content to
     * hash.
     */
    @Test
    public void emptyFilesAreRecordedAndFollowed() throws Exception {
        repository.createFile("empty-1", "", 0);
        repository.createFile("empty-2", "", 0);
        repository.createFile("ordinary.txt");

        fim.succeeds("init", "-y", "-m", "first");
        fim.outputContains("Scanned 3 files");

        fim.succeeds("st");
        fim.outputContains("Nothing modified");

        // Adding one more is noticed, so they are genuinely being tracked rather than skipped on the way in.
        repository.createFile("empty-3", "", 0);
        fim.succeeds("st");
        fim.outputContains("1 added", "empty-3");
    }

    /**
     * Every empty file has the same content as every other, so reporting them as duplicates would bury the
     * real ones. They are left out of the search.
     */
    @Test
    public void emptyFilesAreNotReportedAsDuplicatesOfEachOther() throws Exception {
        repository.createFile("empty-1", "", 0);
        repository.createFile("empty-2", "", 0);
        repository.createFile("empty-3", "", 0);
        fim.succeeds("init", "-y", "-m", "first");

        fim.succeeds("fdup");
        fim.outputContains("No duplicate file found");
    }

    /** Leaving the empty ones out does not hide the duplicates that matter. */
    @Test
    public void realDuplicatesAreStillFoundAlongsideEmptyFiles() throws Exception {
        repository.createFile("empty-1", "", 0);
        repository.createFile("empty-2", "", 0);
        repository.createIdenticalFiles("a real pair", "copy-a.txt", "copy-b.txt");
        fim.succeeds("init", "-y", "-m", "first");

        fim.succeeds("fdup");
        fim.outputContains("1 duplicate file");
        assertThat(fim.output()).containsPattern("copy-[ab]\\.txt");
        fim.outputDoesNotContain("empty-1");
    }

    /** A file that fills up, and one that is emptied, are both changes of content. */
    @Test
    public void becomingEmptyAndCeasingToBeEmptyAreBothNoticed() throws Exception {
        repository.createFile("fills-up", "", 0);
        repository.createFile("empties-out");
        fim.succeeds("init", "-y", "-m", "first");

        repository.setContent("fills-up", "now it has something in it");
        Files.write(repository.resolve("empties-out"), new byte[0]);

        fim.succeeds("st");
        fim.outputContains("2 content modified", "fills-up", "empties-out");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Names that are not plain ASCII
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * A name survives being written to the State and read back, so a second run finds the same file rather
     * than reporting the old name deleted and a new one added.
     *
     * That failure mode is the reason each case runs status twice: once to see nothing modified, and once more
     * after a commit, since a name that is mangled on the way out would only show up on the comparison.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "café.txt",
            "naïve-résumé.txt",
            "Ünicode-Ärger.txt",
            "日本語のファイル.txt",
            "файл.txt",
            "ελληνικά.txt"
    })
    public void aNameOutsideAsciiIsKeptAsItIs(String name) throws Exception {
        repository.createFile(name);
        repository.createFile("plain.txt");
        fim.succeeds("init", "-y", "-m", "first");

        fim.succeeds("st");
        fim.outputContains("Nothing modified");

        repository.appendTo(name, "a change");
        fim.succeeds("st");
        fim.outputContains("1 content modified");

        fim.succeeds("ci", "-y", "-m", "second");
        fim.succeeds("st");
        fim.outputContains("Nothing modified");
    }

    /** The same applies to a directory name. */
    @Test
    public void aDirectoryNameOutsideAsciiIsKeptAsItIs() throws Exception {
        repository.createFile("répertoire/fichier.txt");
        repository.createFile("日本語/ファイル.txt");
        fim.succeeds("init", "-y", "-m", "first");

        fim.succeeds("st");
        fim.outputContains("Nothing modified");
    }

    /** Names holding characters that mean something to a shell or to a parser are just names to fim. */
    @ParameterizedTest
    @ValueSource(strings = {
            "with spaces.txt",
            "  leading and trailing  .txt",
            "with,commas.txt",
            "with#hash.txt",
            "with(parentheses).txt",
            "with[brackets].txt",
            "with'quote.txt",
            "with=equals.txt",
            "with+plus.txt",
            "with%percent.txt",
            "with@at.txt",
            "with!bang.txt",
            "with&ampersand.txt",
            "with$dollar.txt",
            "-looks-like-an-option.txt",
            "--looks-like-a-long-option.txt"
    })
    public void aNameHoldingAwkwardCharactersIsKeptAsItIs(String name) throws Exception {
        repository.createFile(name);
        fim.succeeds("init", "-y", "-m", "first");

        fim.succeeds("st");
        fim.outputContains("Nothing modified");

        repository.appendTo(name, "a change");
        fim.succeeds("st");
        fim.outputContains("1 content modified");
    }

    /** A name long enough to be interesting, without reaching the limit of any filesystem. */
    @Test
    public void aVeryLongNameIsKeptAsItIs() throws Exception {
        // Kept to a length that leaves room for the directory the test runs in, since the whole path has to
        // stay inside the limit Windows puts on it.
        String name = "a".repeat(100) + ".txt";
        repository.createFile(name);
        fim.succeeds("init", "-y", "-m", "first");

        fim.succeeds("st");
        fim.outputContains("Nothing modified");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Shape of the tree
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void aDeeplyNestedFileIsFound() throws Exception {
        StringBuilder path = new StringBuilder();
        for (int level = 1; level <= 12; level++) {
            path.append("level-").append(level).append('/');
        }
        String deepFile = path + "buried.txt";

        repository.createFile(deepFile);
        fim.succeeds("init", "-y", "-m", "first");
        fim.outputContains("Scanned 1 file");

        repository.appendTo(deepFile, "a change");
        fim.succeeds("st");
        fim.outputContains("1 content modified", "buried.txt");
    }

    /** An empty directory holds no files, so there is nothing for fim to record. */
    @Test
    public void anEmptyDirectoryIsNotAnObstacle() throws Exception {
        repository.createFile("file-1");
        repository.createDirectory("empty-directory");
        repository.createDirectory("outer/inner-empty");

        fim.succeeds("init", "-y", "-m", "first");
        fim.outputContains("Scanned 1 file");

        fim.succeeds("st");
        fim.outputContains("Nothing modified");
    }

    /** Enough files in one directory to be sure nothing depends on there being only a handful. */
    @Test
    public void manyFilesInOneDirectoryAreAllRecorded() throws Exception {
        for (int index = 0; index < 200; index++) {
            repository.createFile(String.format("file-%03d", index));
        }

        fim.succeeds("init", "-y", "-m", "first");
        fim.outputContains("Scanned 200 files");

        fim.succeeds("st");
        fim.outputContains("Nothing modified");

        repository.delete("file-100");
        fim.succeeds("st");
        fim.outputContains("1 deleted", "file-100");
    }
}
