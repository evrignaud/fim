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
 * Setting aside a kind of difference with -i, and what that actually changes in the comparison.
 *
 * The five accepted spellings of this option were covered, in the sense that a test ran each of them and
 * checked that nothing threw. What none of them checked is whether the option does anything: a run with
 * `-i dates` that still reported every date change would have passed just as well.
 *
 * Each kind is therefore shown twice here, once without the option so the difference is reported, and once
 * with it so the same difference is not.
 */
@Tag(TestTiers.INTEGRATION)
public class IgnoredDifferencesIT {
    private IntegrationRepository repository;
    private FimCli fim;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        repository = new IntegrationRepository(testInfo);
        fim = repository.fim();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // dates
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void aDateChangeIsReportedAndCanBeSetAside() throws Exception {
        repository.createFiles("touched.txt", "untouched.txt");
        fim.succeeds("init", "-y", "-m", "first");

        // The content is left exactly as it was, so the modification time is the only difference.
        repository.touchLastModified("touched.txt");

        fim.succeeds("st");
        fim.outputContains("touched.txt", "1 date modified");

        fim.succeeds("st", "-i", "dates");
        fim.outputContains("Nothing modified", "Not taking in account modification dates");
        fim.outputDoesNotContain("1 date modified");
    }

    /** Setting dates aside must not hide a change of content that happens to come with one. */
    @Test
    public void settingDatesAsideStillReportsAChangeOfContent() throws Exception {
        repository.createFile("edited.txt");
        fim.succeeds("init", "-y", "-m", "first");

        repository.setContent("edited.txt", "different content entirely");
        repository.touchLastModified("edited.txt");

        fim.succeeds("st", "-i", "dates");
        fim.outputContains("edited.txt", "1 content modified");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // attrs
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void anAttributeChangeIsReportedAndCanBeSetAside() throws Exception {
        repository.createFiles("guarded.txt", "ordinary.txt");
        fim.succeeds("init", "-y", "-m", "first");

        // Permissions only: the content and both timestamps stay as they were.
        repository.setReadOnly("guarded.txt");

        fim.succeeds("st");
        fim.outputContains("guarded.txt");

        fim.succeeds("st", "-i", "attrs");
        fim.outputContains("Nothing modified", "Not taking in account file attributes");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // renamed
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void aRenameIsReportedAndCanBeSetAside() throws Exception {
        repository.createFiles("before.txt", "other.txt");
        fim.succeeds("init", "-y", "-m", "first");

        repository.move("before.txt", "after.txt");

        fim.succeeds("st");
        fim.outputContains("1 renamed");

        fim.succeeds("st", "-i", "renamed");
        fim.outputContains("Nothing modified", "Not taking in account renamed files");
        fim.outputDoesNotContain("1 renamed");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Combinations
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void severalKindsAreSeparatedByACommaAndAllAnnounced() throws Exception {
        givenADateAnAttributeAndARenameChange();

        fim.succeeds("st", "-i", "attrs,dates,renamed");
        fim.outputContains("Nothing modified",
                "Not taking in account file attributes, modification dates, renamed files");
    }

    @Test
    public void allIsTheSameAsNamingEveryKind() throws Exception {
        givenADateAnAttributeAndARenameChange();

        fim.succeeds("st", "-i", "all");
        fim.outputContains("Nothing modified",
                "Not taking in account file attributes, modification dates, renamed files");
    }

    @Test
    public void onlyTheNamedKindsAreAnnounced() throws Exception {
        repository.createFile("touched.txt");
        fim.succeeds("init", "-y", "-m", "first");
        repository.touchLastModified("touched.txt");

        fim.succeeds("st", "-i", "dates");
        fim.outputContains("Not taking in account modification dates");
        fim.outputDoesNotContain("file attributes", "renamed files");
    }

    @ParameterizedTest
    @ValueSource(strings = { "attrs", "dates", "renamed", "all" })
    public void everyAcceptedKindIsUnderstood(String kind) throws Exception {
        repository.createFile("file-1");
        fim.succeeds("init", "-y", "-m", "first");

        fim.succeeds("st", "-i", kind);
        fim.outputContains("Not taking in account");
    }

    @Test
    public void anUnknownKindIsRefused() throws Exception {
        repository.createFile("file-1");
        fim.succeeds("init", "-y", "-m", "first");

        fim.failsAsBadUsage("st", "-i", "colour");
        fim.outputContains("'colour' unknown as difference kind to ignore");
    }

    /** A kind that is not understood is refused even when it sits beside ones that are. */
    @Test
    public void anUnknownKindIsRefusedAlongsideValidOnes() throws Exception {
        repository.createFile("file-1");
        fim.succeeds("init", "-y", "-m", "first");

        fim.failsAsBadUsage("st", "-i", "dates,colour");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Where it is not allowed
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Committing records what is actually on disk, so it cannot be asked to look away from part of it. A State
     * written while ignoring dates would claim timestamps the files do not have.
     */
    @ParameterizedTest
    @ValueSource(strings = { "attrs", "dates", "renamed", "all" })
    public void committingWhileIgnoringAnythingIsRefused(String kind) throws Exception {
        repository.createFile("file-1");
        fim.succeeds("init", "-y", "-m", "first");
        repository.createFile("file-2");

        fim.failsAsBadUsage("ci", "-y", "-m", "second", "-i", kind);
        fim.outputContains("Not allowed to ignore any aspect while committing");

        // Nothing was recorded, so the repository is untouched.
        fim.succeeds("st");
        fim.outputContains("1 added");
    }

    /** The other read-only commands accept it, since they only report. */
    @Test
    public void theDeprecatedDiffAliasAcceptsItToo() throws Exception {
        repository.createFile("touched.txt");
        fim.succeeds("init", "-y", "-m", "first");
        repository.touchLastModified("touched.txt");

        fim.succeeds("diff", "-i", "dates");
        fim.outputContains("Nothing modified");
    }

    // -----------------------------------------------------------------------------------------------------------------

    /** One file whose date moved, one whose permissions changed, and one that was renamed. */
    private void givenADateAnAttributeAndARenameChange() throws Exception {
        repository.createFiles("touched.txt", "guarded.txt", "before.txt");
        fim.succeeds("init", "-y", "-m", "first");

        repository.touchLastModified("touched.txt");
        repository.setReadOnly("guarded.txt");
        repository.move("before.txt", "after.txt");
    }
}
