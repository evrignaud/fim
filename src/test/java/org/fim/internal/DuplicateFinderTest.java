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

package org.fim.internal;

import org.fim.model.Context;
import org.fim.model.DuplicateResult;
import org.fim.tooling.BuildableState;
import org.fim.tooling.DuplicateAssert;
import org.fim.tooling.RepositoryTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class DuplicateFinderTest extends DuplicateAssert {
    private DuplicateFinder cut;
    private BuildableState s;
    private Path rootDir;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        RepositoryTool tool = new RepositoryTool(testInfo);
        rootDir = tool.getRootDir();
        Context context = tool.getContext();

        cut = new DuplicateFinder(context);
        s = new BuildableState(context).addFiles("file_01", "file_02_", "file_03", "file_04");

        tool.createFile(rootDir.resolve("file_01"));
        tool.createFile(rootDir.resolve("file_02_"));
        tool.createFile(rootDir.resolve("file_03"));
        tool.createFile(rootDir.resolve("file_04"));
    }

    @Test
    public void noDuplicatesWhenFilesHaveDifferentContent() {
        DuplicateResult result = cut.findDuplicates(s);
        assertFilesDuplicated(result);
        assertThat(result.getTotalWastedSpace()).isEqualTo(0);
    }

    @Test
    public void duplicatesWhenFilesHaveSameContent() throws IOException {
        s = s.copy("file_01", "file_10");
        Files.copy(rootDir.resolve("file_01"), rootDir.resolve("file_10"));

        DuplicateResult result = cut.findDuplicates(s);
        int totalWastedSpace = "file_10".length();
        assertThat(result.getDuplicateSets().size()).isEqualTo(1);
        assertThat(result.getDuplicateSets().getFirst().getWastedSpace()).isEqualTo(totalWastedSpace);
        assertFilesDuplicated(result, duplicatedFiles("file_01", "file_10"));
        assertThat(result.getTotalWastedSpace()).isEqualTo(totalWastedSpace);

        s = s.copy("file_01", "file_11");
        Files.copy(rootDir.resolve("file_01"), rootDir.resolve("file_11"));

        result = cut.findDuplicates(s);
        totalWastedSpace = ("file_10" + "file_11").length();
        assertThat(result.getDuplicateSets().size()).isEqualTo(1);
        assertThat(result.getDuplicateSets().getFirst().getWastedSpace()).isEqualTo(totalWastedSpace);
        assertFilesDuplicated(result, duplicatedFiles("file_01", "file_10", "file_11"));
        assertThat(result.getTotalWastedSpace()).isEqualTo(totalWastedSpace);

        s = s.copy("file_02_", "file_08_");
        Files.copy(rootDir.resolve("file_02_"), rootDir.resolve("file_08_"));

        result = cut.findDuplicates(s);
        int wastedSpace1 = ("file_10" + "file_11").length();
        int wastedSpace2 = "file_08_".length();
        totalWastedSpace = wastedSpace1 + wastedSpace2;
        assertThat(result.getDuplicateSets().size()).isEqualTo(2);
        assertThat(result.getDuplicateSets().get(0).getWastedSpace()).isEqualTo(wastedSpace1);
        assertThat(result.getDuplicateSets().get(1).getWastedSpace()).isEqualTo(wastedSpace2);
        assertFilesDuplicated(result, duplicatedFiles("file_01", "file_10", "file_11"), duplicatedFiles("file_02_", "file_08_"));
        assertThat(result.getTotalWastedSpace()).isEqualTo(totalWastedSpace);
    }

    @Test
    public void filesWithDifferentLengthAreNeverDuplicated() throws IOException {
        s = s.copy("file_01", "file_10");
        Files.copy(rootDir.resolve("file_01"), rootDir.resolve("file_10"));
        s = s.forceDifferentFileLength("file_10", 15);

        DuplicateResult result = cut.findDuplicates(s);
        assertThat(result.getDuplicateSets().size()).isEqualTo(0);
    }

    @Test
    public void emptyFilesAreNeverSeenAsDuplicates() throws IOException {
        s = s.addEmptyFiles("empty_file_01", "empty_file_02", "empty_file_03", "empty_file_04");
        rootDir.resolve("empty_file_01").toFile().createNewFile();
        rootDir.resolve("empty_file_02").toFile().createNewFile();
        rootDir.resolve("empty_file_03").toFile().createNewFile();
        rootDir.resolve("empty_file_04").toFile().createNewFile();

        DuplicateResult result = cut.findDuplicates(s);
        assertFilesDuplicated(result);
        assertThat(result.getTotalWastedSpace()).isEqualTo(0);
    }
}
