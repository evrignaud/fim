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
package org.fim.command;

import org.fim.internal.StateManager;
import org.fim.model.CommitDetails;
import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.model.Difference;
import org.fim.model.LogEntry;
import org.fim.model.LogResult;
import org.fim.model.Modification;
import org.fim.model.State;
import org.fim.util.Console;
import org.fim.util.Logger;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.fim.model.HashMode.hashAll;
import static org.fim.model.Modification.added;
import static org.fim.model.Modification.attributesModified;
import static org.fim.model.Modification.contentModified;
import static org.fim.model.Modification.copied;
import static org.fim.model.Modification.dateModified;
import static org.fim.model.Modification.deleted;
import static org.fim.model.Modification.duplicated;
import static org.fim.model.Modification.renamed;

public class LogCommand extends AbstractCommand {
    @Override
    public String getCmdName() {
        return "log";
    }

    @Override
    public String getShortCmdName() {
        return "";
    }

    @Override
    public String getDescription() {
        return "Display the history of the States and a summary of the changes that were made";
    }

    @Override
    public Object execute(Context context) throws Exception {
        StateManager manager = new StateManager(context);

        int lastStateNumber = manager.getLastStateNumber();
        if (lastStateNumber == -1) {
            Logger.error("No State found");
            return null;
        }

        LogResult logResult = new LogResult();
        for (int stateNumber = 1; stateNumber <= lastStateNumber; stateNumber++) {
            Path statFile = manager.getStateFile(stateNumber);
            if (Files.exists(statFile)) {
                State state = manager.loadState(stateNumber, false);
                LogEntry logEntry = new LogEntry();
                logEntry.setStateNumber(stateNumber);
                logEntry.setComment(state.getComment());
                logEntry.setTimestamp(state.getTimestamp());
                logEntry.setFileCount(state.getFileCount());
                logEntry.setFilesContentLength(state.getFilesContentLength());
                logEntry.setModificationCounts(state.getModificationCounts());
                logEntry.setCommitDetails(getCommitDetails(state));
                logResult.add(logEntry);

                CompareResult compareResult = buildCompareResult(context, state);
                displayEntry(compareResult, logEntry, System.out);
            }
        }

        return logResult;
    }

    private CommitDetails getCommitDetails(State state) {
        if (state.getCommitDetails() != null) {
            return state.getCommitDetails();
        }
        // For backward compatibility
        return new CommitDetails(hashAll, null);
    }

    private void displayEntry(CompareResult compareResult, LogEntry logEntry, PrintStream out) {
        logEntry.displayEntryHeader(out);
        Console.newLine();
        compareResult.displayChanges(out);
        Console.newLine();
    }

    private CompareResult buildCompareResult(Context context, State state) {
        CompareResult result = new CompareResult(context, null);

        addModifications(state, added, result.getAdded());
        addModifications(state, copied, result.getCopied());
        addModifications(state, duplicated, result.getDuplicated());
        addModifications(state, dateModified, result.getDateModified());
        addModifications(state, contentModified, result.getContentModified());
        addModifications(state, attributesModified, result.getAttributesModified());
        addModifications(state, renamed, result.getRenamed());
        addModifications(state, deleted, result.getDeleted());

        return result;
    }

    private void addModifications(State state, Modification modification, List<Difference> differences) {
        List<Difference> newDifferences = state.getFileStates().stream()
            .filter(fileState -> fileState.getModification() == modification)
            .map(Difference::new)
            .collect(Collectors.toList());
        differences.addAll(newDifferences);
    }
}
