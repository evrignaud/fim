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

package org.fim.command;

import org.fim.command.exception.BadFimUsageException;
import org.fim.command.exception.DontWantToContinueException;
import org.fim.internal.SettingsManager;
import org.fim.internal.StateComparator;
import org.fim.internal.StateGenerator;
import org.fim.internal.StateManager;
import org.fim.internal.StateReGenerator;
import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.model.Difference;
import org.fim.model.FileState;
import org.fim.model.HashMode;
import org.fim.model.Modification;
import org.fim.model.ModificationCounts;
import org.fim.model.State;
import org.fim.util.Logger;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.fim.internal.StateComparator.resetFileStates;
import static org.fim.model.HashMode.dontHash;
import static org.fim.model.Modification.attributesModified;
import static org.fim.model.Modification.dateModified;
import static org.fim.model.Modification.deleted;
import static org.fim.util.FileStateUtil.buildFileNamesMap;

public class CommitCommand extends AbstractCommand {
    SettingsManager settingsManager;
    StateManager manager;

    @Override
    public String getCmdName() {
        return "commit";
    }

    @Override
    public String getShortCmdName() {
        return "ci";
    }

    @Override
    public String getDescription() {
        return "Commit the current directory State";
    }

    @Override
    public Object execute(Context context) throws Exception {
        settingsManager = new SettingsManager(context);

        HashMode globalHashMode = settingsManager.getGlobalHashMode();
        if (context.getHashMode() == dontHash && globalHashMode != dontHash) {
            Logger.error("Computing hash is mandatory");
            throw new BadFimUsageException();
        }

        if (context.getIgnored().somethingIgnored()) {
            Logger.error("Not allowed to ignore any aspect while committing");
            throw new BadFimUsageException();
        }

        adjustThreadCount(context);

        if (context.getComment().length() == 0) {
            Logger.out.println("No comment provided. You are going to commit your modifications without any comment.");
            if (!confirmAction(context, "continue")) {
                throw new DontWantToContinueException();
            }
        }

        manager = new StateManager(context);
        State currentState = new StateGenerator(context).generateState(context.getComment(), context.getRepositoryRootDir(),
                context.getCurrentDirectory());
        State lastState = manager.loadLastState();
        State lastStateToCompare = lastState;

        if (context.isInvokedFromSubDirectory()) {
            if (!lastState.getModelVersion().equals(currentState.getModelVersion())) {
                Logger.error("Not able to incrementally commit into the last State that use a different model version.");
                throw new BadFimUsageException();
            }

            lastStateToCompare = lastState.filterDirectory(context.getRepositoryRootDir(), context.getCurrentDirectory(), true);
        }

        CompareResult result = new StateComparator(context, lastStateToCompare, currentState).compare();
        if (result.somethingModified()) {
            commitModifications(context, currentState, lastState, result);
        }
        result.displayChanges("Nothing committed");
        return result;
    }

    private void commitModifications(Context context, State originalCurrentState, State originalLastState, CompareResult result) throws Exception {
        State currentState = originalCurrentState;
        State lastState = originalLastState;

        HashMode initialHashMode = context.getHashMode();
        try {
            currentState.setModificationCounts(result.getModificationCounts());

            // Add all the deleted FileStates in order to be saved into the State
            List<FileState> deletedFileStates = result.getDeleted().stream().map(Difference::getFileState).collect(Collectors.toList());
            currentState.getFileStates().addAll(deletedFileStates);

            HashMode globalHashMode = settingsManager.getGlobalHashMode();
            if (initialHashMode != dontHash && initialHashMode != globalHashMode) {
                // Reload the last state with the globalHashMode in order to get a complete state.
                context.setHashMode(globalHashMode);
                currentState.setHashMode(globalHashMode);
                currentState.getCommitDetails().setHashModeUsedToGetTheStatus(initialHashMode);
                lastState = manager.loadLastState();
                retrieveMissingHash(context, currentState, lastState);
            }

            if (context.isInvokedFromSubDirectory()) {
                currentState = createConsolidatedState(context, lastState, currentState);
                setFromSubDirectory(context, currentState);
            }

            manager.createNewState(currentState);

            if (context.isPurgeStates()) {
                PurgeStatesCommand purgeStatesCommand = new PurgeStatesCommand();
                purgeStatesCommand.execute(context);
            }
        } finally {
            context.setHashMode(initialHashMode);
        }
    }

    private void setFromSubDirectory(Context context, State currentState) {
        String currentDir = context.getAbsoluteCurrentDirectory().toString();
        String rootDir = context.getRepositoryRootDir().toString() + "/";
        String fromSubDirectory = currentDir.replace(rootDir, "");
        currentState.getCommitDetails().setFromSubDirectory(fromSubDirectory);
    }

    private void retrieveMissingHash(Context context, State currentState, State lastState) throws NoSuchAlgorithmException {
        Map<String, FileState> lastFileStateMap = buildFileNamesMap(lastState.getFileStates());

        List<FileState> toReHash = new ArrayList<>();
        for (FileState fileState : currentState.getFileStates()) {
            Modification modification = fileState.getModification();
            if (modification == null || modification == attributesModified || modification == dateModified) {
                // Get in the last State the hash of the unmodified files
                FileState lastFileState = lastFileStateMap.get(fileState.getFileName());
                if (lastFileState == null) {
                    throw new IllegalStateException(String.format("Not able to find file '%s' into the previous state", fileState.getFileName()));
                }
                fileState.setFileHash(lastFileState.getFileHash());
            } else if (modification != deleted) {
                // Hash changed, we need to compute all the mandatory hash
                toReHash.add(fileState);
            }
        }

        StateReGenerator stateReGenerator = new StateReGenerator(context);
        stateReGenerator.reHashFiles(toReHash);
    }

    private State createConsolidatedState(Context context, State lastState, State currentState) {
        State filteredState = lastState.filterDirectory(context.getRepositoryRootDir(), context.getCurrentDirectory(), false);

        resetFileStates(filteredState.getFileStates());

        State consolidatedState = currentState.clone();
        consolidatedState.getFileStates().addAll(filteredState.getFileStates());
        consolidatedState.setModificationCounts(new ModificationCounts(consolidatedState.getFileStates()));
        consolidatedState.getIgnoredFiles().addAll(lastState.getIgnoredFiles());

        return consolidatedState;
    }
}
