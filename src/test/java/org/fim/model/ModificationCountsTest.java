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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.Modification.added;
import static org.fim.model.Modification.attributesModified;
import static org.fim.model.Modification.contentModified;
import static org.fim.model.Modification.copied;
import static org.fim.model.Modification.corrupted;
import static org.fim.model.Modification.dateModified;
import static org.fim.model.Modification.deleted;
import static org.fim.model.Modification.duplicated;
import static org.fim.model.Modification.renamed;
import static org.fim.tooling.StateAssert.createFileStates;

public class ModificationCountsTest {
    @Test
    public void canSelectFilesToRemove() {
        List<FileState> fileStates = createFileStates("file_", 256, 9 * 3);

        for (int i = 0; i < 3; i++) {
            int index = i * 9;
            fileStates.get(0 + index).setModification(added);
            fileStates.get(1 + index).setModification(copied);
            fileStates.get(2 + index).setModification(duplicated);
            fileStates.get(3 + index).setModification(dateModified);
            fileStates.get(4 + index).setModification(contentModified);
            fileStates.get(5 + index).setModification(attributesModified);
            fileStates.get(6 + index).setModification(renamed);
            fileStates.get(7 + index).setModification(deleted);
            fileStates.get(8 + index).setModification(corrupted);
        }

        ModificationCounts cut = new ModificationCounts(fileStates);

        assertThat(cut.getAdded()).isEqualTo(3);
        assertThat(cut.getCopied()).isEqualTo(3);
        assertThat(cut.getDuplicated()).isEqualTo(3);
        assertThat(cut.getDateModified()).isEqualTo(3);
        assertThat(cut.getContentModified()).isEqualTo(3);
        assertThat(cut.getAttributesModified()).isEqualTo(3);
        assertThat(cut.getRenamed()).isEqualTo(3);
        assertThat(cut.getDeleted()).isEqualTo(3);
        assertThat(cut.getCorrupted()).isEqualTo(3);
    }
}
