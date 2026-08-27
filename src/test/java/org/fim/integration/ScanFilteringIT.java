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

import org.fim.tooling.FimCli;
import org.fim.tooling.IntegrationRepository;
import org.fim.tooling.TestTiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

/**
 * What the scan takes in and what it leaves out: the .fimignore files and the include/exclude patterns.
 *
 * The include and exclude options are documented as narrowing a search for duplicates, and that is how they
 * were understood. They are applied in the file tree scan as well, so they also decide what a State records,
 * which is the more consequential half and had no test at all. A State missing files because of a pattern
 * looks exactly like a State of a smaller directory, and nothing reports the difference.
 */
@Tag(TestTiers.INTEGRATION)
public class ScanFilteringIT {
    private IntegrationRepository repository;
    private FimCli fim;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        repository = new IntegrationRepository(testInfo);
        fim = repository.fim();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // include and exclude reach further than duplicate detection
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * A file excluded while initialising is simply not in the State. Running status afterwards without the
     * option reports it as added, because as far as the repository is concerned it has just appeared.
     */
    @Test
    public void excludingWhileInitialisingLeavesTheFileOutOfTheState() throws Exception {
        repository.createFiles("kept.txt", "skipped.tmp");

        fim.succeeds("init", "-y", "-m", "first", "--exclude", "*.tmp");

        fim.succeeds("st", "--exclude", "*.tmp");
        fim.outputContains("Nothing modified");

        fim.succeeds("st");
        fim.outputContains("skipped.tmp", "1 added");
    }

    /**
     * A file left out by a pattern is not reported as ignored either. It is absent rather than excluded, so
     * nothing in the repository records that a pattern was in play.
     */
    @Test
    public void aFileLeftOutByAPatternIsNotListedAsIgnored() throws Exception {
        repository.createFiles("kept.txt", "skipped.tmp");

        fim.succeeds("init", "-y", "-m", "first", "--exclude", "*.tmp");

        fim.succeeds("dign");
        fim.outputContains("No files or directories ignored");
        fim.outputDoesNotContain("skipped.tmp");
    }

    @Test
    public void includingWhileInitialisingKeepsOnlyWhatMatches() throws Exception {
        repository.createFiles("wanted.jpg", "other.txt", "another.log");

        fim.succeeds("init", "-y", "-m", "first", "--include", "*.jpg");

        fim.succeeds("st");
        fim.outputContains("other.txt", "another.log", "2 added");
    }

    @Test
    public void excludingWhileCommittingLeavesTheFileOutOfTheNewState() throws Exception {
        repository.createFile("kept.txt");
        fim.succeeds("init", "-y", "-m", "first");

        repository.createFiles("added.txt", "skipped.tmp");
        fim.succeeds("ci", "-y", "-m", "second", "--exclude", "*.tmp");

        // The commit recorded added.txt but never saw skipped.tmp.
        fim.succeeds("st");
        fim.outputContains("skipped.tmp", "1 added");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // .fimignore
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void aPatternIgnoresMatchingFilesInThatDirectory() throws Exception {
        repository.createFiles("kept.txt", "noise.log");
        repository.writeFimIgnore("", "*.log");

        fim.succeeds("init", "-y", "-m", "first");

        fim.succeeds("dign");
        fim.outputContains("noise.log");

        fim.succeeds("st");
        fim.outputContains("Nothing modified");
    }

    /** A plain pattern applies to the directory holding the .fimignore and not to the ones below it. */
    @Test
    public void aPlainPatternDoesNotReachIntoSubDirectories() throws Exception {
        repository.createFiles("noise.log", "sub/noise.log");
        repository.writeFimIgnore("", "*.log");

        fim.succeeds("init", "-y", "-m", "first");

        // The one at the root was ignored, the one below was recorded.
        fim.succeeds("dign");
        fim.outputContains("noise.log");

        fim.succeeds("st");
        fim.outputContains("Nothing modified");

        // Removing the recorded one is noticed, which shows it had been taken in.
        repository.delete("sub/noise.log");
        fim.succeeds("st");
        fim.outputContains("1 deleted");
    }

    /** The `**​/` prefix is what makes a pattern apply all the way down. */
    @Test
    public void theAllDirectoriesPrefixReachesEveryDirectory() throws Exception {
        repository.createFiles("keep.txt", "noise.log", "sub/noise.log", "sub/deeper/noise.log");
        repository.writeFimIgnore("", "**/*.log");

        fim.succeeds("init", "-y", "-m", "first");

        fim.succeeds("st");
        fim.outputContains("Nothing modified");

        // Every one of them was left out, so deleting them all changes nothing.
        repository.delete("noise.log");
        repository.delete("sub/noise.log");
        repository.delete("sub/deeper/noise.log");
        fim.succeeds("st");
        fim.outputContains("Nothing modified");
    }

    @Test
    public void aFimIgnoreInASubDirectoryAppliesThere() throws Exception {
        repository.createFiles("root.log", "sub/sub.log");
        repository.writeFimIgnore("sub", "*.log");

        fim.succeeds("init", "-y", "-m", "first");

        // Only the one under sub was ignored, so removing the root one is noticed.
        repository.delete("root.log");
        fim.succeeds("st");
        fim.outputContains("1 deleted");
    }

    @Test
    public void anExactNameIsIgnored() throws Exception {
        repository.createFiles("keep.txt", "exactly-this");
        repository.writeFimIgnore("", "exactly-this");

        fim.succeeds("init", "-y", "-m", "first");

        fim.succeeds("dign");
        fim.outputContains("exactly-this");
    }

    /**
     * The directories other tools keep their own data in are always skipped, whether or not a .fimignore
     * mentions them.
     */
    @ParameterizedTest
    @ValueSource(strings = { ".git", ".svn", ".cvs" })
    public void theDirectoriesOfOtherToolsAreAlwaysSkipped(String directory) throws Exception {
        repository.createFile("tracked.txt");
        repository.createFile(directory + "/internal-file", "not ours");

        fim.succeeds("init", "-y", "-m", "first");

        fim.succeeds("dign");
        fim.outputContains(directory);

        fim.succeeds("st");
        fim.outputContains("Nothing modified");
    }

    /** Fim's own directory is never part of what it records. */
    @Test
    public void fimDoesNotRecordItsOwnDirectory() throws Exception {
        repository.createFile("tracked.txt");
        fim.succeeds("init", "-y", "-m", "first");

        fim.succeeds("st");
        fim.outputContains("Nothing modified");
        fim.outputDoesNotContain(".fim/settings.json");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Working from a sub-directory
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Fim finds the repository by walking up, so a command given a sub-directory works on that part of the
     * tree and leaves the rest of the State alone.
     */
    @Test
    public void aCommandGivenASubDirectoryOnlyLooksAtThatPart() throws Exception {
        repository.createFiles("top.txt", "sub/inside.txt", "other/elsewhere.txt");
        fim.succeeds("init", "-y", "-m", "first");

        repository.createFile("sub/new-inside.txt");
        repository.createFile("other/new-elsewhere.txt");

        FimCli fromSub = new FimCli(repository.resolve("sub"));
        fromSub.succeeds("st");
        fromSub.outputContains("new-inside.txt", "1 added");
        fromSub.outputDoesNotContain("new-elsewhere.txt");
    }

    /** Committing from a sub-directory records that part without discarding what lies outside it. */
    @Test
    public void committingFromASubDirectoryKeepsTheRestOfTheState() throws Exception {
        repository.createFiles("top.txt", "sub/inside.txt", "other/elsewhere.txt");
        fim.succeeds("init", "-y", "-m", "first");

        repository.createFile("sub/new-inside.txt");

        FimCli fromSub = new FimCli(repository.resolve("sub"));
        fromSub.succeeds("ci", "-y", "-m", "from the sub-directory");

        // Nothing outside sub was dropped: the whole repository is still consistent.
        fim.succeeds("st");
        fim.outputContains("Nothing modified");
    }
}
