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
package org.fim.model;

import org.fim.tooling.BuildableContext;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DuplicateResultTest {
    private DuplicateResult cut;
    private Context context;
    private List<FileState> duplicatedFiles;

    @Before
    public void setUp() {
        context = new BuildableContext();
        context.setAlwaysYes(true);
        cut = new DuplicateResult(context);

        duplicatedFiles = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            FileState fileState = new FileState("file_" + index, 256, new FileTime(512, 512), new FileHash("A", "A", "A"), new ArrayList<>());
            duplicatedFiles.add(fileState);
        }
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

    private void assertIsToRemove(int start, int end, boolean toRemove) {
        for (int index = start; index <= end; index++) {
            assertThat(duplicatedFiles.get(index).isToRemove()).isEqualTo(toRemove);
        }
    }
}
