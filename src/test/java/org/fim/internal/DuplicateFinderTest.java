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
    private BuildableState s = new BuildableState(defaultContext()).addFiles("file_01", "file_02", "file_03", "file_04");

    @Test
    public void noDuplicatesWhenFilesHaveDifferentContent() {
        DuplicateResult result = cut.findDuplicates(s);
        assertFilesDuplicated(result);
        assertThat(result.getWastedSpace()).isEqualTo(0);
    }

    @Test
    public void duplicatesWhenFilesHaveSameContent() {
        s = s.copy("file_01", "file_10");
        DuplicateResult result = cut.findDuplicates(s);
        assertFilesDuplicated(result, duplicatedFiles("file_01", "file_10"));
        assertThat(result.getWastedSpace()).isEqualTo(("file_10").length());

        s = s.copy("file_01", "file_11");
        result = cut.findDuplicates(s);
        assertFilesDuplicated(result, duplicatedFiles("file_01", "file_10", "file_11"));
        assertThat(result.getWastedSpace()).isEqualTo(("file_10" + "file_11").length());

        s = s.copy("file_02", "file_08");
        result = cut.findDuplicates(s);
        assertFilesDuplicated(result, duplicatedFiles("file_01", "file_10", "file_11"), duplicatedFiles("file_02", "file_08"));
        assertThat(result.getWastedSpace()).isEqualTo(("file_10" + "file_11" + "file_08").length());
    }

    @Test
    public void emptyFilesAreNeverSeenAsDuplicates() {
        s = s.addEmptyFiles("empty_file_5", "empty_file_6", "empty_file_7", "empty_file_8");
        DuplicateResult result = cut.findDuplicates(s);
        assertFilesDuplicated(result);
        assertThat(result.getWastedSpace()).isEqualTo(0);
    }
}
