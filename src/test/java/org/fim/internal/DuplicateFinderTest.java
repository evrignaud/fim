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

import org.fim.model.DuplicateResult;
import org.fim.tooling.BuildableState;
import org.fim.tooling.DuplicateAssert;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DuplicateFinderTest extends DuplicateAssert {
    private DuplicateFinder cut = new DuplicateFinder(defaultContext());
    private BuildableState s = new BuildableState(defaultContext()).addFiles("file_01", "file_02_", "file_03", "file_04");

    @Test
    public void noDuplicatesWhenFilesHaveDifferentContent() {
        DuplicateResult result = cut.findDuplicates(s);
        assertFilesDuplicated(result);
        assertThat(result.getTotalWastedSpace()).isEqualTo(0);
    }

    @Test
    public void duplicatesWhenFilesHaveSameContent() {
        s = s.copy("file_01", "file_10");
        DuplicateResult result = cut.findDuplicates(s);
        int totalWastedSpace = "file_10".length();
        assertThat(result.getDuplicateSets().size()).isEqualTo(1);
        assertThat(result.getWastedSpace(result.getDuplicateSets().get(0))).isEqualTo(totalWastedSpace);
        assertFilesDuplicated(result, duplicatedFiles("file_01", "file_10"));
        assertThat(result.getTotalWastedSpace()).isEqualTo(totalWastedSpace);

        s = s.copy("file_01", "file_11");
        result = cut.findDuplicates(s);
        totalWastedSpace = ("file_10" + "file_11").length();
        assertThat(result.getDuplicateSets().size()).isEqualTo(1);
        assertThat(result.getWastedSpace(result.getDuplicateSets().get(0))).isEqualTo(totalWastedSpace);
        assertFilesDuplicated(result, duplicatedFiles("file_01", "file_10", "file_11"));
        assertThat(result.getTotalWastedSpace()).isEqualTo(totalWastedSpace);

        s = s.copy("file_02_", "file_08_");
        result = cut.findDuplicates(s);
        int wastedSpace1 = ("file_10" + "file_11").length();
        int wastedSpace2 = "file_08_".length();
        totalWastedSpace = wastedSpace1 + wastedSpace2;
        assertThat(result.getDuplicateSets().size()).isEqualTo(2);
        assertThat(result.getWastedSpace(result.getDuplicateSets().get(0))).isEqualTo(wastedSpace1);
        assertThat(result.getWastedSpace(result.getDuplicateSets().get(1))).isEqualTo(wastedSpace2);
        assertFilesDuplicated(result, duplicatedFiles("file_01", "file_10", "file_11"), duplicatedFiles("file_02_", "file_08_"));
        assertThat(result.getTotalWastedSpace()).isEqualTo(totalWastedSpace);
    }

    @Test
    public void emptyFilesAreNeverSeenAsDuplicates() {
        s = s.addEmptyFiles("empty_file_01", "empty_file_02", "empty_file_03", "empty_file_04");
        DuplicateResult result = cut.findDuplicates(s);
        assertFilesDuplicated(result);
        assertThat(result.getTotalWastedSpace()).isEqualTo(0);
    }
}
