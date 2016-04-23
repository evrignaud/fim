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
package org.fim.tooling;

import org.fim.model.DuplicateResult;
import org.fim.model.DuplicateSet;
import org.fim.model.DuplicatedFiles;
import org.fim.model.FileState;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DuplicateAssert extends StateAssert {
    protected void assertFilesDuplicated(DuplicateResult result, DuplicatedFiles... duplicatedFiles) {
        List<DuplicateSet> duplicateSets = result.getDuplicateSets();
        assertThat(duplicateSets.size()).isEqualTo(duplicatedFiles.length);
        for (DuplicateSet duplicateSet : duplicateSets) {
            int index = duplicateSets.indexOf(duplicateSet);
            List<String> duplicatesToCheck = duplicatedFiles[index].getDuplicates();

            assertThat(duplicateSet.getDuplicatedFiles().size()).isEqualTo(duplicatesToCheck.size());

            for (FileState fileState : duplicateSet.getDuplicatedFiles()) {
                assertThat(duplicatesToCheck.contains(fileState.getFileName()));
            }

            for (String fileName : duplicatesToCheck) {
                assertThat(containsFileName(duplicateSet, fileName)).isEqualTo(true);
            }
        }
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
