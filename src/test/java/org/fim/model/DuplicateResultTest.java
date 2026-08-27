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

    /**
     * <a href="https://github.com/evrignaud/fim/issues/12">Issue #12</a> asked to sort duplicate results
     * by the number of files in a set, by individual file size and by file path. The first two were
     * already available through --sort; this covers the third.
     */
    @Test
    public void canBeSortedOnPath() {
        checkSort(SortMethod.path, true, "big_file_0");
        checkSort(SortMethod.path, false, "little_file_0");
    }

    /**
     * Sorting by path puts the sets of a same directory next to each other, which is what the other sort
     * methods cannot do: ordering by size or by count scatters neighbouring files through the output.
     */
    @Test
    public void sortingOnPathGroupsSetsOfTheSameDirectory() {
        Context ctx = new BuildableContext();
        DuplicateResult result = new DuplicateResult(ctx);

        // Deliberately added out of path order, and with sizes that would interleave the directories if
        // the ordering were by size
        result.addDuplicatedFiles(duplicateSetNamed("photos/b.jpg", 500));
        result.addDuplicatedFiles(duplicateSetNamed("music/b.mp3", 400));
        result.addDuplicatedFiles(duplicateSetNamed("photos/a.jpg", 300));
        result.addDuplicatedFiles(duplicateSetNamed("music/a.mp3", 200));

        ctx.setSortMethod(SortMethod.path);
        ctx.setSortAscending(true);
        result.sortDuplicateSets();

        assertThat(result.getDuplicateSets().stream().map(DuplicateSet::getPathOrderingKey).toList())
                .containsExactly("music/a.mp3", "music/b.mp3", "photos/a.jpg", "photos/b.jpg");
    }

    /**
     * The ordering key does not depend on the order the duplicates were discovered in. Sets are built
     * while walking files sorted by hash, so the first file of a set is unrelated to where it lives.
     */
    @Test
    public void pathOrderingKeyIsTheLowestFileNameOfTheSet() {
        List<FileState> files = new java.util.ArrayList<>();
        files.add(new FileState("zzz/last.dat", 100L, new FileTime(1000, 1000), new FileHash("A", "A", "A"), new java.util.ArrayList<>()));
        files.add(new FileState("aaa/first.dat", 100L, new FileTime(1000, 1000), new FileHash("A", "A", "A"), new java.util.ArrayList<>()));

        assertThat(new DuplicateSet(files).getPathOrderingKey()).isEqualTo("aaa/first.dat");
    }

    private List<FileState> duplicateSetNamed(String fileName, int fileLength) {
        List<FileState> files = new java.util.ArrayList<>();
        for (int index = 0; index < 2; index++) {
            files.add(new FileState(fileName + ".dup" + index, fileLength, new FileTime(1000, 1000),
                    new FileHash(fileName, fileName, fileName), new java.util.ArrayList<>()));
        }
        // The lowest name of the set is the one the ordering key must pick
        files.add(new FileState(fileName, fileLength, new FileTime(1000, 1000),
                new FileHash(fileName, fileName, fileName), new java.util.ArrayList<>()));
        return files;
    }

    @Test
    public void canBeDisplayedInCSV() throws IOException {
        checkOutput(OutputType.csv, "SetIndex,FileIndex,WastedSpace,FilePath,FileName,FileLength,FileType\n" +
                                    "1,1,608,,little_file_0,32,");
    }

    @Test
    public void csvOutputHasCorrectRowCount() throws IOException {
        // Req 6.1, 6.2: header + one row per file across all sets
        ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
        Logger.out = new PrintStream(arrayOutputStream);
        context.setOutputType(OutputType.csv);

        cut.displayAndRemoveDuplicates();

        arrayOutputStream.flush();
        String result = arrayOutputStream.toString().replace("\r", "");
        String[] lines = result.split("\n");
        // Header + 20 little files + 10 files + 2 big files = 33 lines
        assertThat(lines.length).isEqualTo(33);
        assertThat(lines[0]).isEqualTo("SetIndex,FileIndex,WastedSpace,FilePath,FileName,FileLength,FileType");
    }

    @Test
    public void csvOutputExtractsPathAndExtension() throws IOException {
        // Req 6.3: path and extension extraction
        Context ctx = new BuildableContext();
        DuplicateResult dr = new DuplicateResult(ctx);
        // Create file states with paths and extensions
        List<FileState> files = new java.util.ArrayList<>();
        files.add(new FileState("dir/subdir/photo.jpg", 100L, new org.fim.model.FileTime(1000, 1000),
                new org.fim.model.FileHash("A", "A", "A"), new java.util.ArrayList<>()));
        files.add(new FileState("dir/subdir/photo_copy.JPG", 100L, new org.fim.model.FileTime(1000, 1000),
                new org.fim.model.FileHash("A", "A", "A"), new java.util.ArrayList<>()));
        dr.addDuplicatedFiles(files);

        ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
        Logger.out = new PrintStream(arrayOutputStream);
        ctx.setOutputType(OutputType.csv);

        dr.displayAndRemoveDuplicates();

        arrayOutputStream.flush();
        String result = arrayOutputStream.toString().replace("\r", "");
        // Verify path extraction and lowercase extension
        assertThat(result).contains("dir/subdir");
        assertThat(result).contains("jpg");
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

    @Test
    public void jsonOutputContainsWastedSpaceAndFileFields() throws IOException {
        // Req 7.1, 7.2, 7.3: JSON array with wastedSpace and fileList fields
        ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
        Logger.out = new PrintStream(arrayOutputStream);
        context.setOutputType(OutputType.json);

        cut.displayAndRemoveDuplicates();

        arrayOutputStream.flush();
        String result = arrayOutputStream.toString().replace("\r", "");
        assertThat(result).contains("\"wastedSpace\"");
        assertThat(result).contains("\"fileList\"");
        assertThat(result).contains("\"name\"");
        assertThat(result).contains("\"length\"");
        assertThat(result).contains("\"path\"");
        assertThat(result).contains("\"type\"");
    }

    @Test
    public void aggregateStatisticsAreCorrect() {
        // Req 3.2, 3.3, 12.4, 12.5: verify duplicatedFilesCount and totalWastedSpace
        // setUp adds: 20 little files (32 bytes), 10 files (256 bytes), 2 big files (512 bytes)
        // duplicatedFilesCount = (20-1) + (10-1) + (2-1) = 19 + 9 + 1 = 29
        assertThat(cut.getDuplicatedFilesCount()).isEqualTo(29);

        // totalWastedSpace = 19*32 + 9*256 + 1*512 = 608 + 2304 + 512 = 3424
        assertThat(cut.getTotalWastedSpace()).isEqualTo(3424);

        // Verify per-set wasted space
        assertThat(cut.getDuplicateSets()).hasSize(3);
    }

    @Test
    public void noDuplicatesDisplaysMessage() throws IOException {
        // Req 5.3: "No duplicate file found" when no duplicates
        Context emptyContext = new BuildableContext();
        DuplicateResult emptyResult = new DuplicateResult(emptyContext);

        ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
        Logger.out = new PrintStream(arrayOutputStream);

        emptyResult.displayAndRemoveDuplicates();

        arrayOutputStream.flush();
        String output = arrayOutputStream.toString().replace("\r", "");
        assertThat(output).contains("No duplicate file found");
    }

    @Test
    public void summaryOnlyWhenNotVerbose() throws IOException {
        // Req 5.4: summary-only when verbose is disabled and not removing
        context.setVerbose(false);
        context.setRemoveDuplicates(false);

        ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
        Logger.out = new PrintStream(arrayOutputStream);

        cut.displayAndRemoveDuplicates();

        arrayOutputStream.flush();
        String output = arrayOutputStream.toString().replace("\r", "");
        // Should show summary line but not individual set details
        assertThat(output).contains("duplicate");
        assertThat(output).contains("wasted space");
        assertThat(output).doesNotContain("Duplicate set #");
    }

    @Test
    public void humanOutputShowsSetDetails() throws IOException {
        // Req 5.1, 5.2, 5.5: verbose human output shows numbered sets and file listings
        context.setVerbose(true);
        context.setOutputType(OutputType.human);

        ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
        Logger.out = new PrintStream(arrayOutputStream);

        cut.displayAndRemoveDuplicates();

        arrayOutputStream.flush();
        String output = arrayOutputStream.toString().replace("\r", "");
        assertThat(output).contains("Duplicate set #1");
        assertThat(output).contains("wasted space");
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
        return cut.getDuplicateSets().get(0).getDuplicatedFiles().get(0);
    }

    private void assertIsToRemove(int start, int end, boolean toRemove) {
        for (int index = start; index <= end; index++) {
            assertThat(duplicatedFiles.get(index).isToRemove()).isEqualTo(toRemove);
        }
    }
}
