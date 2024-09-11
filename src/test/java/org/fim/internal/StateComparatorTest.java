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

import org.fim.model.CompareResult;
import org.fim.model.HashMode;
import org.fim.tooling.BuildableContext;
import org.fim.tooling.BuildableState;
import org.fim.tooling.FileNameDiff;
import org.fim.tooling.StateAssert;
import org.fim.util.Logger;
import org.fim.util.TestAllHashModes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.HashMode.dontHash;
import static org.fim.model.Modification.added;
import static org.fim.model.Modification.contentModified;
import static org.fim.model.Modification.copied;
import static org.fim.model.Modification.corrupted;
import static org.fim.model.Modification.dateModified;
import static org.fim.model.Modification.deleted;
import static org.fim.model.Modification.duplicated;
import static org.fim.model.Modification.renamed;

public class StateComparatorTest extends StateAssert {
    private BuildableContext context;
    private BuildableState s1;
    private BuildableState s2;
    private CompareResult result;

    public void setUp(HashMode hashMode) {
        context = defaultContext();
        context.setHashMode(hashMode);
        s1 = new BuildableState(context).addFiles("file_01", "file_02", "file_03", "file_04");
    }

    @TestAllHashModes
    public void canManageSameContent(HashMode hashMode) {
        setUp(hashMode);

        // Set the same file content
        s2 = s1.setContent("file_01", "file_01");
        result = new StateComparator(context, s1, s2).compare();
        assertNothingModified(result);
    }

    @TestAllHashModes
    public void canManageFileAdded(HashMode hashMode) {
        setUp(hashMode);

        s2 = s1.addFiles("file_05");
        result = new StateComparator(context, s1, s2).compare();
        assertOnlyFilesAdded(result, "file_05");
    }

    @TestAllHashModes
    public void canManageDateModified(HashMode hashMode) {
        setUp(hashMode);

        s2 = s1.touch("file_01");
        result = new StateComparator(context, s1, s2).compare();
        assertOnlyDatesModified(result, "file_01");
    }

    @TestAllHashModes
    public void noChangeDetectedWhenDatesIgnoredIsSet(HashMode hashMode) {
        setUp(hashMode);

        context.getIgnored().setDatesIgnored(true);
        s2 = s1.touch("file_01");
        result = new StateComparator(context, s1, s2).compare();
        assertNothingModified(result);
    }

    @TestAllHashModes
    public void canManageContentModified(HashMode hashMode) {
        setUp(hashMode);

        s2 = s1.appendContent("file_01", "append_01");
        result = new StateComparator(context, s1, s2).compare();
        assertOnlyContentModified(result, "file_01");
    }

    @TestAllHashModes
    public void canManageFileRename(HashMode hashMode) {
        setUp(hashMode);

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

    @TestAllHashModes
    public void noChangeDetectedWhenRenamedIgnoredIsSet(HashMode hashMode) {
        setUp(hashMode);

        context.getIgnored().setRenamedIgnored(true);
        s2 = s1.rename("file_01", "file_06");
        result = new StateComparator(context, s1, s2).compare();
        if (hashMode != dontHash) {
            assertNothingModified(result);
        }
    }

    @TestAllHashModes
    public void canManageFileCopy(HashMode hashMode) {
        setUp(hashMode);

        s2 = s1.copy("file_01", "file_06");
        result = new StateComparator(context, s1, s2).compare();
        if (hashMode == dontHash) {
            assertOnlyFilesAdded(result, "file_06");
        } else {
            assertOnlyFileDuplicated(result, new FileNameDiff("file_01", "file_06"));
        }
    }

    @TestAllHashModes
    public void canManageFileDelete(HashMode hashMode) {
        setUp(hashMode);

        s2 = s1.delete("file_01");
        result = new StateComparator(context, s1, s2).compare();
        assertOnlyFileDeleted(result, "file_01");
    }

    @TestAllHashModes
    public void canCopyAFileAndChangeDate(HashMode hashMode) {
        setUp(hashMode);

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

    @TestAllHashModes
    public void canCopyAFileAndChangeContent(HashMode hashMode) {
        setUp(hashMode);

        s2 = s1.copy("file_01", "file_00")
                .copy("file_01", "file_06")
                .appendContent("file_01", "append_01");
        result = new StateComparator(context, s1, s2).compare();
        if (hashMode == dontHash) {
            assertGotOnlyModifications(result, contentModified, added);
            assertFilesModified(result, contentModified, "file_01");
            assertFilesModified(result, added, "file_00", "file_06");
        } else {
            assertGotOnlyModifications(result, copied, contentModified);
            assertFilesModified(result, copied, new FileNameDiff("file_01", "file_00"), new FileNameDiff("file_01", "file_06"));
            assertFilesModified(result, contentModified, "file_01");
        }
    }

    @TestAllHashModes
    public void canCorrectlyDetectRenamedFiles(HashMode hashMode) {
        setUp(hashMode);

        s1 = s1.copy("file_01", "dup_file_01");
        s2 = s1.rename("file_01", "new_file_01")
                .rename("dup_file_01", "new_dup_file_01");

        detectAndAssertRenamedFiles(hashMode);
    }

    @TestAllHashModes
    public void canCorrectlyDetectRenamedFilesThatHaveDateChanged(HashMode hashMode) {
        setUp(hashMode);

        s1 = s1.copy("file_01", "dup_file_01");
        s2 = s1.rename("file_01", "new_file_01").touch("new_file_01")
                .rename("dup_file_01", "new_dup_file_01").touch("new_dup_file_01");
        detectAndAssertRenamedFiles(hashMode);
    }

    private void detectAndAssertRenamedFiles(HashMode hashMode) {
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

    @TestAllHashModes
    public void emptyFilesAreNeverSeenAsDuplicates(HashMode hashMode) {
        setUp(hashMode);

        s1 = s1.addEmptyFiles("empty_file_01");
        s2 = s1.addEmptyFiles("empty_file_02");
        result = new StateComparator(context, s1, s2).compare();
        assertGotOnlyModifications(result, added);
        assertFilesModified(result, added, "empty_file_02");
    }

    @TestAllHashModes
    public void canDetectHardwareCorruption(HashMode hashMode) {
        setUp(hashMode);

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

    @TestAllHashModes
    public void withLogDebugWeHaveAResult(HashMode hashMode) {
        setUp(hashMode);

        boolean debugEnabled = Logger.debugEnabled;
        try {
            Logger.debugEnabled = true;
            s2 = s1.clone();
            String result = new StateComparator(context, s1, s2).fileStatesToString("My message", s1.getFileStates());
            assertThat(result).startsWith("  My message:");
        } finally {
            Logger.debugEnabled = debugEnabled;
        }
    }

    @TestAllHashModes
    public void withoutLogDebugWeHaveNothing(HashMode hashMode) {
        setUp(hashMode);

        boolean debugEnabled = Logger.debugEnabled;
        try {
            Logger.debugEnabled = false;
            s2 = s1.clone();
            String result = new StateComparator(context, s1, s2).fileStatesToString("My message", s1.getFileStates());
            assertThat(result).isEqualTo("");
        } finally {
            Logger.debugEnabled = debugEnabled;
        }
    }
}
