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

import org.fim.internal.StateManager;
import org.fim.model.Context;
import org.fim.model.LogEntry;
import org.fim.model.LogResult;
import org.fim.model.State;
import org.fim.util.Logger;

import java.nio.file.Files;
import java.nio.file.Path;

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
        return "Display the history of the States with the same output as the 'status' command";
    }

    @Override
    public Object execute(Context context) throws Exception {
        StateManager stateManager = new StateManager(context);

        int lastStateNumber = stateManager.getLastStateNumber();
        if (lastStateNumber == -1) {
            Logger.error("No State found");
            return null;
        }

        LogResult logResult = new LogResult();
        for (int stateNumber = 1; stateNumber <= lastStateNumber; stateNumber++) {
            Path statFile = stateManager.getStateFile(stateNumber);
            if (Files.exists(statFile)) {
                State state = stateManager.loadState(stateNumber, false);
                LogEntry logEntry = new LogEntry(context, state, stateNumber);

                logEntry.displayEntryHeader();
                Logger.newLine();
                logEntry.getCompareResult().displayChanges();
                Logger.newLine();

                logEntry.setCompareResult(null); // Cleanup the compareResult to avoid: java.lang.OutOfMemoryError: GC overhead limit exceeded
                logResult.add(logEntry);
            }
        }

        return logResult;
    }
}
