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
import org.fim.model.CorruptedStateException;
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.State;
import org.fim.util.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.fim.model.Constants.NO_HASH;

public class StateManager {
    public static final String STATE_EXTENSION = ".json.gz";

    private final Context context;

    public StateManager(Context context) {
        this.context = context;
    }

    public void createNewState(State state) throws IOException {
        int lastStateNumber = getLastStateNumber();
        lastStateNumber++;
        state.saveToGZipFile(getStateFile(lastStateNumber));
        saveLastStateNumber(lastStateNumber);
    }

    public State loadLastState() throws IOException {
        int lastStateNumber = getLastStateNumber();
        if (lastStateNumber <= 0) {
            throw new IllegalStateException(String.format("Unable to load the last State from directory %s", context.getRepositoryStatesDir()));
        }
        return loadState(lastStateNumber);
    }

    public State loadState(int stateNumber) throws IOException {
        return loadState(stateNumber, true);
    }

    public State loadState(int stateNumber, boolean loadFullState) throws IOException {
        Path stateFile = getStateFile(stateNumber);
        if (!Files.exists(stateFile)) {
            throw new IllegalStateException(
                    String.format("Unable to load State file %d from directory %s", stateNumber, context.getRepositoryStatesDir()));
        }

        try {
            State state = State.loadFromGZipFile(stateFile, loadFullState);

            if (loadFullState) {
                adjustAccordingToHashMode(state);
            }

            return state;
        } catch (CorruptedStateException e) {
            throw new IllegalStateException(String.format("The content of the State file #%d have been modified and may be corrupted", stateNumber));
        }
    }

    private void adjustAccordingToHashMode(State state) {
        // Replace by 'no_hash' accurately to be able to compare the FileState entry
        // Keep the original hash before changing by 'no_hash' in order to fill correctly the previousFileState
        switch (context.getHashMode()) {
            case dontHash -> {
                for (FileState fileState : state.getFileStates()) {
                    fileState.storeOriginalHash();
                    fileState.setFileHash(new FileHash(NO_HASH, NO_HASH, NO_HASH));
                }
            }
            case hashSmallBlock -> {
                for (FileState fileState : state.getFileStates()) {
                    fileState.storeOriginalHash();
                    FileHash fileHash = fileState.getFileHash();
                    fileState.setFileHash(new FileHash(fileHash.getSmallBlockHash(), NO_HASH, NO_HASH));
                }
            }
            case hashMediumBlock -> {
                for (FileState fileState : state.getFileStates()) {
                    fileState.storeOriginalHash();
                    FileHash fileHash = fileState.getFileHash();
                    fileState.setFileHash(new FileHash(fileHash.getSmallBlockHash(), fileHash.getMediumBlockHash(), NO_HASH));
                }
            }
            case hashAll -> {
                // Nothing to do
            }
        }
    }

    /**
     * @return the State file formatted like this: &lt;statesDir&gt;/state_&lt;stateNumber&gt;.json.gz
     */
    public Path getStateFile(int stateNumber) {
        return context.getRepositoryStatesDir().resolve("state_" + stateNumber + STATE_EXTENSION);
    }

    public int getLastStateNumber() {
        int number;
        boolean lastStateFileDesynchronized = false;

        SettingsManager settingsManager = new SettingsManager(context);
        if (settingsManager.isCreated()) {
            number = settingsManager.getLastStateNumber();
            if (Files.exists(getStateFile(number)) && !Files.exists(getStateFile(number + 1))) {
                return number;
            }

            if (number > 0) {
                lastStateFileDesynchronized = true;
            }
        }

        for (int index = 1; ; index++) {
            if (!Files.exists(getStateFile(index))) {
                number = index - 1;
                if (lastStateFileDesynchronized) {
                    Logger.error(String.format("lastStateNumber desynchronized. Resetting it to %d.", number));
                    saveLastStateNumber(number);
                }
                return number;
            }
        }
    }

    public void saveLastStateNumber(int lastStateNumber) {
        if (lastStateNumber != -1) {
            SettingsManager settingsManager = new SettingsManager(context);
            settingsManager.setLastStateNumber(lastStateNumber);
            settingsManager.save();
        }
    }
}
