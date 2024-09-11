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
import org.fim.model.State;
import org.fim.util.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RollbackCommand extends AbstractCommand {
    @Override
    public String getCmdName() {
        return "rollback";
    }

    @Override
    public String getShortCmdName() {
        return "rbk";
    }

    @Override
    public String getDescription() {
        return "Rollback the last commit. It will remove the last State";
    }

    @Override
    public FimReposConstraint getFimReposConstraint() {
        return FimReposConstraint.MUST_EXIST;
    }

    @Override
    public Object execute(Context context) throws Exception {
        StateManager stateManager = new StateManager(context);

        int lastStateNumber = stateManager.getLastStateNumber();
        if (lastStateNumber <= 1) {
            Logger.info("No commit to rollback");
            return null;
        }

        Path stateFile = stateManager.getStateFile(lastStateNumber);
        if (Files.exists(stateFile)) {
            Logger.out.printf("You are going to rollback the last commit. State #%d will be removed%n", lastStateNumber);

            displayStateSummary(context, stateManager, lastStateNumber);

            if (confirmAction(context, "remove it")) {
                Files.delete(stateFile);

                stateManager.saveLastStateNumber(lastStateNumber - 1);
            }
        }
        return null;
    }

    private void displayStateSummary(Context context, StateManager stateManager, int stateNumber) throws IOException {
        State state = stateManager.loadState(stateNumber, false);
        LogEntry logEntry = new LogEntry(context, state, stateNumber);

        Logger.newLine();
        logEntry.displayEntryHeader();
        Logger.newLine();
        logEntry.getCompareResult().displayChanges();
        Logger.newLine();
    }
}
