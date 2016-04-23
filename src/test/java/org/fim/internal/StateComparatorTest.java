/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2015  Etienne Vrignaud
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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import static org.fim.model.HashMode.*;
import static org.fim.model.Modification.*;

@RunWith(Parameterized.class)
public class StateComparatorTest extends StateAssert {
    private HashMode hashMode;
    private BuildableContext context;
    private BuildableState s1;
    private BuildableState s2;

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
    public void setup() throws IOException {
        context = defaultContext();
        context.setHashMode(hashMode);
        s1 = new BuildableState(context).addFiles("file_01", "file_02", "file_03", "file_04");
    }

    @Test
    public void weCanDoCompareOnSimpleOperations() {
        // Set the same file content
        s2 = s1.setContent("file_01", "file_01");
        CompareResult result = new StateComparator(context, s1, s2).compare();
        assertNothingModified(result);

        s2 = s1.addFiles("file_05");
        result = new StateComparator(context, s1, s2).compare();
        assertOnlyFilesAdded(result, "file_05");

        s2 = s1.touch("file_01");
        result = new StateComparator(context, s1, s2).compare();
        assertOnlyDatesModified(result, "file_01");

        s2 = s1.appendContent("file_01", "append_01");
        result = new StateComparator(context, s1, s2).compare();
        if (hashMode == dontHash) {
            assertNothingModified(result);
        } else {
            assertOnlyContentModified(result, "file_01");
        }

        s2 = s1.rename("file_01", "file_06");
        result = new StateComparator(context, s1, s2).compare();
        if (hashMode == dontHash) {
            assertGotOnlyModifications(result, added, deleted);
            assertFilesModified(result, deleted, "file_01");
            assertFilesModified(result, added, "file_06");
        } else {
            assertOnlyFileRenamed(result, new FileNameDiff("file_01", "file_06"));
        }

        s2 = s1.copy("file_01", "file_06");
        result = new StateComparator(context, s1, s2).compare();
        if (hashMode == dontHash) {
            assertOnlyFilesAdded(result, "file_06");
        } else {
            assertOnlyFileDuplicated(result, new FileNameDiff("file_01", "file_06"));
        }

        s2 = s1.delete("file_01");
        result = new StateComparator(context, s1, s2).compare();
        assertOnlyFileDeleted(result, "file_01");
    }

    @Test
    public void weCanCopyAFileAndChangeDate() {
        s2 = s1.copy("file_01", "file_00")
            .copy("file_01", "file_06")
            .touch("file_01");
        CompareResult result = new StateComparator(context, s1, s2).compare();
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
    public void weCanCopyAFileAndChangeContent() {
        s2 = s1.copy("file_01", "file_00")
            .copy("file_01", "file_06")
            .appendContent("file_01", "append_01");
        CompareResult result = new StateComparator(context, s1, s2).compare();
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
    public void weCanDetectHardwareCorruption() {
        if (hashMode == dontHash) {
            return;
        }

        s2 = s1.clone();
        CompareResult result = new StateComparator(context, s1, s2).searchForHardwareCorruption().compare();
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
