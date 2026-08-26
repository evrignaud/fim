/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2025 Etienne Vrignaud
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
import org.fim.util.TestAllHashModes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.HashMode.dontHash;
import static org.fim.model.HashMode.hashAll;

public class StateManagerTest extends StateAssert {
    private BuildableState s;

    private StateManager cut;

    private TestInfo testInfo;

    @BeforeEach
    void init(TestInfo testInfo) {
        this.testInfo = testInfo;
    }

    public void setUp(HashMode hashMode) throws IOException {
        RepositoryTool tool = new RepositoryTool(testInfo, hashMode);
        Context context = tool.getContext();

        Path statesDir = context.getRepositoryStatesDir();
        FileUtils.deleteDirectory(statesDir.toFile());
        Files.createDirectories(statesDir);

        s = new BuildableState(context);

        cut = new StateManager(context);
    }

    @TestAllHashModes
    public void canCreateNewState(HashMode hashMode) throws IOException {
        setUp(hashMode);

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

    @TestAllHashModes
    public void canRetrieveLastStateNumberWhenAStateFileIsMissing(HashMode hashMode) throws IOException {
        setUp(hashMode);

        s = s.addFiles("file_1", "file_2");
        cut.createNewState(s);

        s = s.addFiles("file_3");
        cut.createNewState(s);

        assertThat(cut.getLastStateNumber()).isEqualTo(2);

        Files.delete(cut.getStateFile(2));

        assertThat(cut.getLastStateNumber()).isEqualTo(1);
    }

    @TestAllHashModes
    public void canRetrieveLastStateNumberWhenThereAreStatesAfterTheLastState(HashMode hashMode) throws IOException {
        setUp(hashMode);

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

    // Req 3.7: getLastStateNumber returns 0 when no states and no settings exist
    @Test
    public void getLastStateNumberReturnsZeroWhenNoStatesExist() throws IOException {
        setUp(hashAll);
        assertThat(cut.getLastStateNumber()).isEqualTo(0);
    }

    // Req 3.7: saveLastStateNumber persists to settings.json and can be read back
    @Test
    public void saveLastStateNumberPersistsToSettingsFile() throws IOException {
        setUp(hashAll);

        cut.saveLastStateNumber(5);

        // Settings says 5, but no state files exist on disk,
        // so self-healing kicks in and returns 0
        assertThat(cut.getLastStateNumber()).isEqualTo(0);

        // Now create 5 actual state files so settings and disk agree
        for (int i = 0; i < 5; i++) {
            s = s.addFiles("persist_file_" + i);
            cut.createNewState(s);
        }
        assertThat(cut.getLastStateNumber()).isEqualTo(5);

        // Manually save a different number and verify it persists
        cut.saveLastStateNumber(3);
        // Self-healing will detect files up to 5 exist and correct to 5
        assertThat(cut.getLastStateNumber()).isEqualTo(5);
    }

    // Req 3.7: saveLastStateNumber with -1 is a no-op
    @Test
    public void saveLastStateNumberWithMinusOneIsNoOp() throws IOException {
        setUp(hashAll);

        // Create a state so settings.json gets created with lastStateNumber=1
        s = s.addFiles("file_1");
        cut.createNewState(s);
        assertThat(cut.getLastStateNumber()).isEqualTo(1);

        // Calling saveLastStateNumber(-1) should not change the persisted value
        cut.saveLastStateNumber(-1);
        assertThat(cut.getLastStateNumber()).isEqualTo(1);
    }

    // Req 3.7: Each createNewState call creates the corresponding state file on disk
    @Test
    public void createNewStateCreatesStateFilesOnDisk() throws IOException {
        setUp(hashAll);

        s = s.addFiles("file_1");
        cut.createNewState(s);
        assertThat(Files.exists(cut.getStateFile(1))).isTrue();

        s = s.addFiles("file_2");
        cut.createNewState(s);
        assertThat(Files.exists(cut.getStateFile(2))).isTrue();

        s = s.addFiles("file_3");
        cut.createNewState(s);
        assertThat(Files.exists(cut.getStateFile(3))).isTrue();

        // Verify all three files exist
        for (int i = 1; i <= 3; i++) {
            assertThat(Files.exists(cut.getStateFile(i))).isTrue();
        }
        // And state 4 does not
        assertThat(Files.exists(cut.getStateFile(4))).isFalse();
    }

    // Req 3.7, 3.8: SettingsManager correctly serializes and deserializes settings via Gson
    @Test
    public void settingsManagerGsonSerializationRoundTrip() throws IOException {
        RepositoryTool tool = new RepositoryTool(testInfo, hashAll);
        Context context = tool.getContext();

        Path dotFimDir = context.getRepositoryDotFimDir();
        Files.createDirectories(dotFimDir);

        // Create and save settings
        SettingsManager manager = new SettingsManager(context);
        manager.setGlobalHashMode(HashMode.hashMediumBlock);
        manager.setLastStateNumber(42);
        manager.save();

        // Verify the file exists
        Path settingsFile = dotFimDir.resolve(SettingsManager.SETTINGS_FILE);
        assertThat(Files.exists(settingsFile)).isTrue();

        // Read back via a new SettingsManager instance
        SettingsManager reloaded = new SettingsManager(context);
        assertThat(reloaded.getGlobalHashMode()).isEqualTo(HashMode.hashMediumBlock);
        assertThat(reloaded.getLastStateNumber()).isEqualTo(42);

        // Also verify the raw JSON content contains expected fields
        String json = new String(Files.readAllBytes(settingsFile));
        assertThat(json).contains("\"globalHashMode\"");
        assertThat(json).contains("\"lastStateNumber\"");
        assertThat(json).contains("hashMediumBlock");
        assertThat(json).contains("42");
    }

    // Req 3.8: Self-healing when settings file is missing but state files exist
    @Test
    public void selfHealingWhenSettingsFileMissing() throws IOException {
        setUp(hashAll);

        // Create two states (this also creates settings.json)
        s = s.addFiles("file_1");
        cut.createNewState(s);
        s = s.addFiles("file_2");
        cut.createNewState(s);
        assertThat(cut.getLastStateNumber()).isEqualTo(2);

        // Delete the settings.json file from the .fim directory
        Path dotFimDir = cut.getStateFile(1).getParent().getParent();
        Path settingsFile = dotFimDir.resolve(SettingsManager.SETTINGS_FILE);
        if (Files.exists(settingsFile)) {
            Files.delete(settingsFile);
        }

        // getLastStateNumber should self-heal by scanning disk
        assertThat(cut.getLastStateNumber()).isEqualTo(2);
    }

    @TestAllHashModes
    public void loadStateAdjustsHashFieldsAccordingToHashMode(HashMode hashMode) throws IOException {
        // Req 3.6: StateManager.loadState() adjusts FileHash fields based on context hashMode
        // Always create state with hashAll so all hash fields are populated
        RepositoryTool tool = new RepositoryTool(testInfo, HashMode.hashAll);
        Context fullHashContext = tool.getContext();

        Path statesDir = fullHashContext.getRepositoryStatesDir();
        FileUtils.deleteDirectory(statesDir.toFile());
        Files.createDirectories(statesDir);

        BuildableState state = new BuildableState(fullHashContext)
                .addFiles("adjust_file_1", "adjust_file_2");
        StateManager fullHashManager = new StateManager(fullHashContext);
        fullHashManager.createNewState(state);

        // Now load with the parameterized hash mode
        Context loadContext = tool.createContext(hashMode, true);
        StateManager loadManager = new StateManager(loadContext);
        State loaded = loadManager.loadState(1);

        for (FileState fs : loaded.getFileStates()) {
            FileHash fh = fs.getFileHash();
            switch (hashMode) {
                case dontHash:
                    assertThat(fh.getSmallBlockHash()).isEqualTo(Constants.NO_HASH);
                    assertThat(fh.getMediumBlockHash()).isEqualTo(Constants.NO_HASH);
                    assertThat(fh.getFullHash()).isEqualTo(Constants.NO_HASH);
                    break;
                case hashSmallBlock:
                    assertThat(fh.getSmallBlockHash()).isNotEqualTo(Constants.NO_HASH);
                    assertThat(fh.getMediumBlockHash()).isEqualTo(Constants.NO_HASH);
                    assertThat(fh.getFullHash()).isEqualTo(Constants.NO_HASH);
                    break;
                case hashMediumBlock:
                    assertThat(fh.getSmallBlockHash()).isNotEqualTo(Constants.NO_HASH);
                    assertThat(fh.getMediumBlockHash()).isNotEqualTo(Constants.NO_HASH);
                    assertThat(fh.getFullHash()).isEqualTo(Constants.NO_HASH);
                    break;
                case hashAll:
                    assertThat(fh.getSmallBlockHash()).isNotEqualTo(Constants.NO_HASH);
                    assertThat(fh.getMediumBlockHash()).isNotEqualTo(Constants.NO_HASH);
                    assertThat(fh.getFullHash()).isNotEqualTo(Constants.NO_HASH);
                    break;
            }
        }
    }
}
