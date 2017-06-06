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

import org.apache.commons.io.FileUtils;
import org.fim.model.Constants;
import org.fim.model.Context;
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.HashMode;
import org.fim.model.State;
import org.fim.tooling.BuildableState;
import org.fim.tooling.RepositoryTool;
import org.fim.tooling.StateAssert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.HashMode.dontHash;
import static org.fim.model.HashMode.hashAll;
import static org.fim.model.HashMode.hashMediumBlock;
import static org.fim.model.HashMode.hashSmallBlock;

@RunWith(Parameterized.class)
public class StateManagerTest extends StateAssert {
    private HashMode hashMode;
    private Context context;
    private BuildableState s;

    private StateManager cut;
    private RepositoryTool tool;
    private Path rootDir;

    public StateManagerTest(final HashMode hashMode) {
        this.hashMode = hashMode;
    }

    @Parameterized.Parameters(name = "Hash mode: {0}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(new Object[][]{
            {dontHash},
            {hashSmallBlock},
            {hashMediumBlock},
            {hashAll}
        });
    }

    @Before
    public void setUp() throws IOException {
        tool = new RepositoryTool(this.getClass(), hashMode);
        rootDir = tool.getRootDir();
        context = tool.getContext();

        Path statesDir = context.getRepositoryStatesDir();
        FileUtils.deleteDirectory(statesDir.toFile());
        Files.createDirectories(statesDir);

        s = new BuildableState(context);

        cut = new StateManager(context);
    }

    @Test
    public void canCreateNewState() throws IOException {
        int count = 10;
        for (int index = 0; index < count; index++) {
            String dirName = "dir_" + index;
            s = s.addFiles(dirName + "/file_1", dirName + "/file_2", dirName + "/file_3");
            cut.createNewState(s);

            assertThat(cut.getLastStateNumber()).isEqualTo(index + 1);
        }

        assertThat(cut.getLastStateNumber()).isEqualTo(count);

        State result = cut.loadLastState();
        if (hashMode == dontHash) {
            assertAllFileStatesHaveNoHash(result, 30);
        } else {
            assertThat(result).isEqualTo(s);
        }

        assertThat(cut.getLastStateNumber()).isEqualTo(10);
        Path stateFile = cut.getStateFile(cut.getLastStateNumber());
        assertThat(stateFile.getFileName().toString()).isEqualTo("state_10.json.gz");

        result = cut.loadState(10);
        if (hashMode == dontHash) {
            assertAllFileStatesHaveNoHash(result, 30);
        } else {
            assertThat(result).isEqualTo(s);
        }
    }

    @Test
    public void canRetrieveLastStateNumberWhenAStateFileIsMissing() throws IOException {
        s = s.addFiles("file_1", "file_2");
        cut.createNewState(s);

        s = s.addFiles("file_3");
        cut.createNewState(s);

        assertThat(cut.getLastStateNumber()).isEqualTo(2);

        Files.delete(cut.getStateFile(2));

        assertThat(cut.getLastStateNumber()).isEqualTo(1);
    }

    @Test
    public void canRetrieveLastStateNumberWhenThereAreStatesAfterTheLastState() throws IOException {
        s = s.addFiles("file_1", "file_2");
        cut.createNewState(s);

        s = s.addFiles("file_3");
        cut.createNewState(s);

        assertThat(cut.getLastStateNumber()).isEqualTo(2);

        cut.saveLastStateNumber(1);

        assertThat(cut.getLastStateNumber()).isEqualTo(2);
    }

    private void assertAllFileStatesHaveNoHash(State result, int fileCount) {
        FileHash noHash = new FileHash(Constants.NO_HASH, Constants.NO_HASH, Constants.NO_HASH);

        assertThat(result.getFileCount()).isEqualTo(fileCount);
        for (FileState fileState : result.getFileStates()) {
            assertThat(fileState.getFileHash()).isEqualTo(noHash);
        }
    }
}
