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
import org.fim.internal.StateComparator;
import org.fim.internal.StateGenerator;
import org.fim.internal.StateManager;
import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.model.State;
import org.fim.util.Logger;

import static org.fim.model.HashMode.hashAll;

public class DetectCorruptionCommand extends AbstractCommand {
    @Override
    public String getCmdName() {
        return "detect-corruption";
    }

    @Override
    public String getShortCmdName() {
        return "dcor";
    }

    @Override
    public String getDescription() {
        return """
                Find changes most likely caused by a hardware corruption or a filesystem bug.
                                                Change in content, but not in creation time and last modified time""";
    }

    @Override
    public Object execute(Context context) throws Exception {
        if (context.getHashMode() != hashAll) {
            Logger.error("Hardware corruption detection require to hash all the file content.");
            throw new BadFimUsageException();
        }

        checkHashMode(context, Option.ALLOW_COMPATIBLE);

        State currentState = new StateGenerator(context).generateState("", context.getRepositoryRootDir(), context.getCurrentDirectory());
        State lastState = new StateManager(context).loadLastState();

        if (context.isInvokedFromSubDirectory()) {
            lastState = lastState.filterDirectory(context.getRepositoryRootDir(), context.getCurrentDirectory(), true);
        }

        CompareResult result = new StateComparator(context, lastState, currentState).searchForHardwareCorruption().compare();
        result.displayChanges("Nothing corrupted");
        return result;
    }
}
