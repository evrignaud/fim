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
import org.fim.model.Context;
import org.fim.model.FileState;
import org.fim.model.LogEntry;
import org.fim.model.LogResult;
import org.fim.model.Modification;
import org.fim.model.ModificationCounts;
import org.fim.model.State;
import org.fim.util.Console;
import org.fim.util.Logger;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.atteo.evo.inflector.English.plural;
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
                logResult.add(logEntry);

                displayEntry(context, state, logEntry, System.out);
            }
        }

        return logResult;
    }

    private void displayEntry(Context context, State state, LogEntry logEntry, PrintStream out) {
        logEntry.displayEntryHeader(out);

        ModificationCounts modificationCounts = logEntry.getModificationCounts();
        if (context.isVerbose()) {
            Console.newLine();
            displayFileDetails(context, out, state);
            if (modificationCounts.somethingModified()) {
                Console.newLine();
            }
        }

        modificationCounts.displayCounts(out, context.isVerbose());
        Console.newLine();
    }

    private void displayFileDetails(Context context, PrintStream out, State state) {
        String stateFormat = "%-17s ";

        final String addedStr = String.format(stateFormat, "Added:");
        displayFileModified(out, context, addedStr, added, state,
            fileState -> out.printf(addedStr + "%s%n", fileState.getFileName()));

        final String copiedStr = String.format(stateFormat, "Copied:");
        displayFileModified(out, context, copiedStr, copied, state,
            fileState -> out.printf(copiedStr + "%s%n", fileState.getFileName()));

        final String duplicatedStr = String.format(stateFormat, "Duplicated:");
        displayFileModified(out, context, duplicatedStr, duplicated, state,
            fileState -> out.printf(duplicatedStr + "%s%n", fileState.getFileName()));

        final String dateModifiedStr = String.format(stateFormat, "Date modified:");
        displayFileModified(out, context, dateModifiedStr, dateModified, state,
            fileState -> out.printf(dateModifiedStr + "%s%n", fileState.getFileName()));

        final String contentModifiedStr = String.format(stateFormat, "Content modified:");
        displayFileModified(out, context, contentModifiedStr, contentModified, state,
            fileState -> out.printf(contentModifiedStr + "%s%n", fileState.getFileName()));

        final String attrsModifiedStr = String.format(stateFormat, "Attrs. modified:");
        displayFileModified(out, context, attrsModifiedStr, attributesModified, state,
            fileState -> out.printf(attrsModifiedStr + "%s%n", fileState.getFileName()));

        final String renamedStr = String.format(stateFormat, "Renamed:");
        displayFileModified(out, context, renamedStr, renamed, state,
            fileState -> out.printf(renamedStr + "%s%n", fileState.getFileName()));

        final String deletedStr = String.format(stateFormat, "Deleted:");
        displayFileModified(out, context, deletedStr, deleted, state,
            fileState -> out.printf(deletedStr + "%s%n", fileState.getFileName()));
    }

    public static void displayFileModified(PrintStream out, Context context, String actionStr,
                                           Modification modification, State state, Consumer<FileState> displayOneModification) {
        int truncateOutput = context.getTruncateOutput();
        if (truncateOutput < 1) {
            return;
        }

        int quarter = truncateOutput / 4;

        List<FileState> fileStates = state.getFileStates().stream()
            .filter(fileState -> fileState.getModification() == modification).collect(Collectors.toList());

        int fileStatesSize = fileStates.size();
        for (int index = 0; index < fileStatesSize; index++) {
            FileState fileState = fileStates.get(index);
            if (index >= truncateOutput && (fileStatesSize - index) > quarter) {
                out.println("  [Too many lines. Truncating the output] ...");
                int moreFiles = fileStatesSize - index;
                out.printf("%s%d %s more%n", actionStr, moreFiles, plural("file", moreFiles));
                break;
            }

            if (displayOneModification != null) {
                displayOneModification.accept(fileState);
            }
        }
    }
}
