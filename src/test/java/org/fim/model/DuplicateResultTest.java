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

package org.fim.model;

import org.fim.tooling.BuildableContext;
import org.fim.util.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.tooling.StateAssert.createFileStates;

public class DuplicateResultTest {
    private DuplicateResult cut;
    private Context context;
    private List<FileState> duplicatedFiles;

    @BeforeEach
    public void setUp() {
        context = new BuildableContext();
        context.setAlwaysYes(true);
        cut = new DuplicateResult(context);

        List<FileState> littleDuplicatedFiles = createFileStates("little_file_", 32, 20);
        duplicatedFiles = createFileStates("file_", 256, 10);
        List<FileState> bigDuplicatedFiles = createFileStates("big_file_", 512, 2);

        cut.addDuplicatedFiles(littleDuplicatedFiles);
        cut.addDuplicatedFiles(duplicatedFiles);
        cut.addDuplicatedFiles(bigDuplicatedFiles);
    }

    @Test
    public void canSelectFilesToRemove() {
        cut.selectFilesToRemove(duplicatedFiles);
        assertIsToRemove(0, 0, false);
        assertIsToRemove(1, 9, true);
    }

    @Test
    public void canManageAnswers() {
        boolean gotCorrectAnswer = cut.manageAnswers(duplicatedFiles, "a");
        assertThat(gotCorrectAnswer).isTrue();
        assertIsToRemove(0, 9, false);

        gotCorrectAnswer = cut.manageAnswers(duplicatedFiles, "all");
        assertThat(gotCorrectAnswer).isTrue();
        assertIsToRemove(0, 9, false);

        gotCorrectAnswer = cut.manageAnswers(duplicatedFiles, "n");
        assertThat(gotCorrectAnswer).isTrue();
        assertIsToRemove(0, 9, true);

        gotCorrectAnswer = cut.manageAnswers(duplicatedFiles, "none");
        assertThat(gotCorrectAnswer).isTrue();
        assertIsToRemove(0, 9, true);

        gotCorrectAnswer = cut.manageAnswers(duplicatedFiles, "2");
        assertThat(gotCorrectAnswer).isTrue();
        assertIsToRemove(0, 0, true);
        assertIsToRemove(1, 1, false);
        assertIsToRemove(2, 9, true);

        gotCorrectAnswer = cut.manageAnswers(duplicatedFiles, "2 4");
        assertThat(gotCorrectAnswer).isTrue();
        assertIsToRemove(0, 0, true);
        assertIsToRemove(1, 1, false);
        assertIsToRemove(2, 2, true);
        assertIsToRemove(3, 3, false);
        assertIsToRemove(4, 9, true);

        gotCorrectAnswer = cut.manageAnswers(duplicatedFiles, " 12 24 0 2");
        assertThat(gotCorrectAnswer).isTrue();
        assertIsToRemove(0, 0, true);
        assertIsToRemove(1, 1, false);
        assertIsToRemove(2, 9, true);

        gotCorrectAnswer = cut.manageAnswers(duplicatedFiles, " Yeah ");
        assertThat(gotCorrectAnswer).isFalse();

        gotCorrectAnswer = cut.manageAnswers(duplicatedFiles, " 12 24 0");
        assertThat(gotCorrectAnswer).isFalse();
    }

    @Test
    public void canBeSortedOnWasted() {
        checkSort(SortMethod.wasted, true, "big_file_0");
        checkSort(SortMethod.wasted, false, "file_0");
    }

    @Test
    public void canBeSortedOnNumber() {
        checkSort(SortMethod.number, true, "big_file_0");
        checkSort(SortMethod.number, false, "little_file_0");
    }

    @Test
    public void canBeSortedOnSize() {
        checkSort(SortMethod.size, true, "little_file_0");
        checkSort(SortMethod.size, false, "big_file_0");
    }

    @Test
    public void canBeDisplayedInCSV() throws IOException {
        checkOutput(OutputType.csv, "SetIndex,FileIndex,WastedSpace,FilePath,FileName,FileLength,FileType\n" +
                                    "1,1,608,,little_file_0,32,");
    }

    @Test
    public void canBeDisplayedInJSON() throws IOException {
        checkOutput(OutputType.json, """
                "fileList": [
                      {
                        "path": "",
                        "name": "little_file_0",
                        "length": 32,
                        "type": ""
                      }""");
    }

    private void checkSort(SortMethod sortMethod, boolean sortAscending, String firstFileName) {
        context.setSortMethod(sortMethod);
        context.setSortAscending(sortAscending);
        cut.sortDuplicateSets();
        assertThat(getFirstDuplicatedFileState().getFileName()).isEqualTo(firstFileName);
    }

    private void checkOutput(OutputType outputType, String contentPart) throws IOException {
        ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
        Logger.out = new PrintStream(arrayOutputStream);
        context.setOutputType(outputType);

        cut.displayAndRemoveDuplicates();

        arrayOutputStream.flush();
        String result = arrayOutputStream.toString();
        result = result.replace("\r", "");
        assertThat(result).contains(contentPart);
    }

    private FileState getFirstDuplicatedFileState() {
        return cut.getDuplicateSets().getFirst().getDuplicatedFiles().getFirst();
    }

    private void assertIsToRemove(int start, int end, boolean toRemove) {
        for (int index = start; index <= end; index++) {
            assertThat(duplicatedFiles.get(index).isToRemove()).isEqualTo(toRemove);
        }
    }
}
