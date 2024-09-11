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

import org.fim.model.DuplicateResult;
import org.fim.model.DuplicateSet;
import org.fim.model.DuplicatedFiles;
import org.fim.model.FileState;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DuplicateAssert {
    protected void assertFilesDuplicated(DuplicateResult result, DuplicatedFiles... expectedDuplicatedFiles) {
        List<DuplicateSet> duplicateSets = result.getDuplicateSets();
        assertThat(duplicateSets.size()).isEqualTo(expectedDuplicatedFiles.length);

        int expectedDuplicatedFilesCount = 0;
        for (DuplicateSet duplicateSet : duplicateSets) {
            List<FileState> duplicatedFiles = duplicateSet.getDuplicatedFiles();
            expectedDuplicatedFilesCount += duplicatedFiles.size() - 1;

            int index = duplicateSets.indexOf(duplicateSet);
            List<String> expectedDuplicates = expectedDuplicatedFiles[index].getDuplicates();

            assertThat(duplicatedFiles.size()).isEqualTo(expectedDuplicates.size());

            for (FileState fileState : duplicatedFiles) {
                assertThat(expectedDuplicates).contains(fileState.getFileName());
            }

            for (String fileName : expectedDuplicates) {
                assertThat(containsFileName(duplicateSet, fileName)).isEqualTo(true);
            }
        }
        assertThat(result.getDuplicatedFilesCount()).isEqualTo(expectedDuplicatedFilesCount);
    }

    protected boolean containsFileName(DuplicateSet duplicateSet, String fileName) {
        for (FileState fileState : duplicateSet.getDuplicatedFiles()) {
            if (fileState.getFileName().equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    protected DuplicatedFiles duplicatedFiles(String... duplicates) {
        return new DuplicatedFiles(Arrays.asList(duplicates));
    }

}
