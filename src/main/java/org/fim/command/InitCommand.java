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

import org.fim.command.exception.DontWantToContinueException;
import org.fim.command.exception.RepositoryException;
import org.fim.internal.SettingsManager;
import org.fim.internal.StateComparator;
import org.fim.internal.StateGenerator;
import org.fim.internal.StateManager;
import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.model.State;
import org.fim.util.Logger;

import java.io.IOException;
import java.nio.file.Files;

import static org.fim.model.HashMode.hashAll;
import static org.fim.util.HashModeUtil.hashModeToString;

public class InitCommand extends AbstractCommand {
    @Override
    public String getCmdName() {
        return "init";
    }

    @Override
    public String getShortCmdName() {
        return "";
    }

    @Override
    public String getDescription() {
        return "Initialize a Fim repository and create the first State";
    }

    @Override
    public FimReposConstraint getFimReposConstraint() {
        return FimReposConstraint.MUST_NOT_EXIST;
    }

    @Override
    public Object execute(Context context) throws Exception {
        if (context.getComment().length() == 0) {
            Logger.out.println("No comment provided. You are going to initialize your repository using the default comment.");
            if (!confirmAction(context, "continue")) {
                throw new DontWantToContinueException();
            }
        }

        String comment = context.getComment();
        if (comment.length() == 0) {
            comment = "Initial State";
        }
        State currentState = new StateGenerator(context).generateState(comment, context.getCurrentDirectory(), context.getCurrentDirectory());

        CompareResult result = new StateComparator(context, null, currentState).compare();
        currentState.setModificationCounts(result.getModificationCounts());

        createRepository(context);

        new StateManager(context).createNewState(currentState);

        result.displayChanges();
        Logger.out.println("Repository initialized");

        return currentState;
    }

    private void createRepository(Context context) {
        try {
            Files.createDirectories(context.getRepositoryStatesDir());
        } catch (IOException ex) {
            Logger.error(String.format("Not able to create the '%s' directory that holds the Fim repository", context.getRepositoryDotFimDir()), ex,
                    context.isDisplayStackTrace());
            throw new RepositoryException();
        }

        if (context.getHashMode() != hashAll) {
            SettingsManager settingsManager = new SettingsManager(context);
            settingsManager.setGlobalHashMode(context.getHashMode());
            settingsManager.save();

            Logger.warning(String.format("Global hash mode set to '%s'%n", hashModeToString(context.getHashMode())));
        }
    }
}
