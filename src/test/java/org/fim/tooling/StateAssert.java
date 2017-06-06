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
package org.fim.tooling;

import org.fim.model.CompareResult;
import org.fim.model.Difference;
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.FileTime;
import org.fim.model.Modification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class StateAssert {
    protected BuildableContext defaultContext() {
        return new BuildableContext();
    }

    public static List<FileState> createFileStates(int count) {
        List<FileState> fileStates = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            FileState fileState = new FileState("file_" + index, 256, new FileTime(512, 512), new FileHash("A", "A", "A"), new ArrayList<>());
            fileStates.add(fileState);
        }
        return fileStates;
    }

    protected void assertNothingModified(CompareResult result) {
        assertThat(result.somethingModified()).isFalse();
    }

    protected void assertOnlyFilesAdded(CompareResult result, String... fileNames) {
        assertGotOnlyModifications(result, Modification.added);
        assertFilesModified(result, Modification.added, fileNames);
    }

    protected void assertOnlyFileCopied(CompareResult result, FileNameDiff... fileNameDiffs) {
        assertGotOnlyModifications(result, Modification.copied);
        assertFilesModified(result, Modification.copied, fileNameDiffs);
    }

    protected void assertOnlyFileDuplicated(CompareResult result, FileNameDiff... fileNameDiffs) {
        assertGotOnlyModifications(result, Modification.duplicated);
        assertFilesModified(result, Modification.duplicated, fileNameDiffs);
    }

    protected void assertOnlyDatesModified(CompareResult result, String... fileNames) {
        assertGotOnlyModifications(result, Modification.dateModified);
        assertFilesModified(result, Modification.dateModified, fileNames);
    }

    protected void assertOnlyContentModified(CompareResult result, String... fileNames) {
        assertGotOnlyModifications(result, Modification.contentModified);
        assertFilesModified(result, Modification.contentModified, fileNames);
    }

    protected void assertOnlyFileRenamed(CompareResult result, FileNameDiff... fileNameDiffs) {
        assertGotOnlyModifications(result, Modification.renamed);
        assertFilesModified(result, Modification.renamed, fileNameDiffs);
    }

    protected void assertOnlyFileDeleted(CompareResult result, String... fileNames) {
        assertGotOnlyModifications(result, Modification.deleted);
        assertFilesModified(result, Modification.deleted, fileNames);
    }

    protected void assertGotOnlyModifications(CompareResult result, Modification... modifications) {
        List<Modification> modificationsList = Arrays.asList(modifications);

        for (Modification modification : Modification.values()) {
            List<Difference> differences = getDifferences(result, modification);
            if (modificationsList.contains(modification) == false) {
                assertThat(differences.isEmpty()).isTrue();
            } else {
                assertThat(differences.isEmpty()).isFalse();
            }
        }
    }

    protected void assertFilesModified(CompareResult result, Modification modification, String... fileNames) {
        List<String> fileNamesList = Arrays.asList(fileNames);

        List<Difference> differences = getDifferences(result, modification);
        assertThat(fileNamesList.size()).isEqualTo(differences.size());

        for (Difference difference : differences) {
            assertThat(fileNamesList.contains(difference.getFileState().getFileName())).isTrue();
        }
    }

    protected void assertFilesModified(CompareResult result, Modification modification, FileNameDiff... fileNameDiffs) {
        List<FileNameDiff> fileNameDiffsList = Arrays.asList(fileNameDiffs);

        List<Difference> differences = getDifferences(result, modification);
        assertThat(fileNameDiffsList.size()).isEqualTo(differences.size());

        for (Difference difference : differences) {
            assertThat(fileNameDiffsList.contains(new FileNameDiff(difference))).isTrue();
        }
    }

    private List<Difference> getDifferences(CompareResult result, Modification modification) {
        switch (modification) {
            case added:
                return result.getAdded();

            case copied:
                return result.getCopied();

            case duplicated:
                return result.getDuplicated();

            case dateModified:
                return result.getDateModified();

            case contentModified:
                return result.getContentModified();

            case attributesModified:
                return result.getAttributesModified();

            case renamed:
                return result.getRenamed();

            case deleted:
                return result.getDeleted();

            case corrupted:
                return result.getCorrupted();
        }

        throw new IllegalArgumentException("Invalid Modification " + modification);
    }

    protected List<String> toFileNames(List<FileState> fileStates) {
        List<String> fileNames = fileStates.stream().map(FileState::getFileName).collect(Collectors.toList());
        return fileNames;
    }
}
