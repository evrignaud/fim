/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2016  Etienne Vrignaud
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
 * along with Fim.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.fim.internal;

import org.fim.model.CompareResult;
import org.fim.model.HashMode;
import org.fim.tooling.BuildableContext;
import org.fim.tooling.BuildableState;
import org.fim.tooling.FileNameDiff;
import org.fim.tooling.StateAssert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;

import static org.fim.model.HashMode.dontHash;
import static org.fim.model.HashMode.hashAll;
import static org.fim.model.HashMode.hashMediumBlock;
import static org.fim.model.HashMode.hashSmallBlock;
import static org.fim.model.Modification.added;
import static org.fim.model.Modification.contentModified;
import static org.fim.model.Modification.copied;
import static org.fim.model.Modification.corrupted;
import static org.fim.model.Modification.dateModified;
import static org.fim.model.Modification.deleted;
import static org.fim.model.Modification.duplicated;
import static org.fim.model.Modification.renamed;

@RunWith(Parameterized.class)
public class StateComparatorTest extends StateAssert {
    private HashMode hashMode;
    private BuildableContext context;
    private BuildableState s1;
    private BuildableState s2;
    private CompareResult result;

    public StateComparatorTest(final HashMode hashMode) {
        this.hashMode = hashMode;
    }

    @Parameters(name = "Hash mode: {0}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(new Object[][]{
            {dontHash},
            {hashSmallBlock},
            {hashMediumBlock},
            {hashAll}
        });
    }

    @Before
    public void setup() {
        context = defaultContext();
        context.setHashMode(hashMode);
        s1 = new BuildableState(context).addFiles("file_01", "file_02", "file_03", "file_04");
    }

    @Test
    public void canManageSameContent() {
        // Set the same file content
        s2 = s1.setContent("file_01", "file_01");
        result = new StateComparator(context, s1, s2).compare();
        assertNothingModified(result);
    }

    @Test
    public void canManageFileAdded() {
        s2 = s1.addFiles("file_05");
        result = new StateComparator(context, s1, s2).compare();
        assertOnlyFilesAdded(result, "file_05");
    }

    @Test
    public void canManageDateModified() {
        s2 = s1.touch("file_01");
        result = new StateComparator(context, s1, s2).compare();
        assertOnlyDatesModified(result, "file_01");
    }

    @Test
    public void noChangeDetectedWhenDatesIgnoredIsSet() {
        context.getIgnored().setDatesIgnored(true);
        s2 = s1.touch("file_01");
        result = new StateComparator(context, s1, s2).compare();
        assertNothingModified(result);
    }

    @Test
    public void canManageContentModified() {
        s2 = s1.appendContent("file_01", "append_01");
        result = new StateComparator(context, s1, s2).compare();
        if (hashMode == dontHash) {
            assertNothingModified(result);
        } else {
            assertOnlyContentModified(result, "file_01");
        }
    }

    @Test
    public void canManageFileRename() {
        s2 = s1.rename("file_01", "file_06");
        result = new StateComparator(context, s1, s2).compare();
        if (hashMode == dontHash) {
            assertGotOnlyModifications(result, added, deleted);
            assertFilesModified(result, deleted, "file_01");
            assertFilesModified(result, added, "file_06");
        } else {
            assertOnlyFileRenamed(result, new FileNameDiff("file_01", "file_06"));
        }
    }

    @Test
    public void noChangeDetectedWhenRenamedIgnoredIsSet() {
        context.getIgnored().setRenamedIgnored(true);
        s2 = s1.rename("file_01", "file_06");
        result = new StateComparator(context, s1, s2).compare();
        if (hashMode != dontHash) {
            assertNothingModified(result);
        }
    }

    @Test
    public void canManageFileCopy() {
        s2 = s1.copy("file_01", "file_06");
        result = new StateComparator(context, s1, s2).compare();
        if (hashMode == dontHash) {
            assertOnlyFilesAdded(result, "file_06");
        } else {
            assertOnlyFileDuplicated(result, new FileNameDiff("file_01", "file_06"));
        }
    }

    @Test
    public void canManageFileDelete() {
        s2 = s1.delete("file_01");
        result = new StateComparator(context, s1, s2).compare();
        assertOnlyFileDeleted(result, "file_01");
    }

    @Test
    public void canCopyAFileAndChangeDate() {
        s2 = s1.copy("file_01", "file_00")
            .copy("file_01", "file_06")
            .touch("file_01");
        result = new StateComparator(context, s1, s2).compare();
        if (hashMode == dontHash) {
            assertGotOnlyModifications(result, added, dateModified);
            assertFilesModified(result, added, "file_00", "file_06");
        } else {
            assertGotOnlyModifications(result, duplicated, dateModified);
            assertFilesModified(result, duplicated, new FileNameDiff("file_01", "file_00"), new FileNameDiff("file_01", "file_06"));
        }
        assertFilesModified(result, dateModified, "file_01");
    }

    @Test
    public void canCopyAFileAndChangeContent() {
        s2 = s1.copy("file_01", "file_00")
            .copy("file_01", "file_06")
            .appendContent("file_01", "append_01");
        result = new StateComparator(context, s1, s2).compare();
        if (hashMode == dontHash) {
            assertGotOnlyModifications(result, added);
            assertFilesModified(result, added, "file_00", "file_06");
        } else {
            assertGotOnlyModifications(result, copied, contentModified);
            assertFilesModified(result, copied, new FileNameDiff("file_01", "file_00"), new FileNameDiff("file_01", "file_06"));
            assertFilesModified(result, contentModified, "file_01");
        }
    }

    @Test
    public void canCorrectlyDetectRenamedFiles() {
        s1 = s1.copy("file_01", "dup_file_01");
        s2 = s1.rename("file_01", "new_file_01")
            .rename("dup_file_01", "new_dup_file_01");

        detectAndAssertRenamedFiles();
    }

    @Test
    public void canCorrectlyDetectRenamedFilesThatHaveDateChanged() {
        s1 = s1.copy("file_01", "dup_file_01");
        s2 = s1.rename("file_01", "new_file_01").touch("new_file_01")
            .rename("dup_file_01", "new_dup_file_01").touch("new_dup_file_01");
        detectAndAssertRenamedFiles();
    }

    private void detectAndAssertRenamedFiles() {
        result = new StateComparator(context, s1, s2).compare();
        if (hashMode == dontHash) {
            assertGotOnlyModifications(result, added, deleted);
            assertFilesModified(result, added, "new_file_01", "new_dup_file_01");
            assertFilesModified(result, deleted, "file_01", "dup_file_01");
        } else {
            assertGotOnlyModifications(result, renamed);
            assertFilesModified(result, renamed, new FileNameDiff("dup_file_01", "new_file_01"), new FileNameDiff("dup_file_01", "new_dup_file_01"));
        }
    }

    @Test
    public void emptyFilesAreNeverSeenAsDuplicates() {
        s1 = s1.addEmptyFiles("empty_file_01");
        s2 = s1.addEmptyFiles("empty_file_02");
        result = new StateComparator(context, s1, s2).compare();
        assertGotOnlyModifications(result, added);
        assertFilesModified(result, added, "empty_file_02");
    }

    @Test
    public void canDetectHardwareCorruption() {
        if (hashMode == dontHash) {
            return;
        }

        s2 = s1.clone();
        result = new StateComparator(context, s1, s2).searchForHardwareCorruption().compare();
        assertNothingModified(result);

        s2 = s2.setContent("file_01", "XXXX");
        result = new StateComparator(context, s1, s2).searchForHardwareCorruption().compare();
        assertGotOnlyModifications(result, corrupted);
        assertFilesModified(result, corrupted, "file_01");

        // file_02 is deleted and file_05 is added, they are not detected as corrupted
        s2 = s2.delete("file_02").addFiles("file_05");
        result = new StateComparator(context, s1, s2).searchForHardwareCorruption().compare();
        assertGotOnlyModifications(result, corrupted);
        assertFilesModified(result, corrupted, "file_01");
    }
}
