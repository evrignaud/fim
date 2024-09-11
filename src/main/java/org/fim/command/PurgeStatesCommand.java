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
import org.fim.util.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.atteo.evo.inflector.English.plural;

public class PurgeStatesCommand extends AbstractCommand {
    @Override
    public String getCmdName() {
        return "purge-states";
    }

    @Override
    public String getShortCmdName() {
        return "pst";
    }

    @Override
    public String getDescription() {
        return "Purge previous States";
    }

    @Override
    public FimReposConstraint getFimReposConstraint() {
        return FimReposConstraint.MUST_EXIST;
    }

    @Override
    public Object execute(Context context) throws Exception {
        StateManager stateManager = new StateManager(context);

        List<Path> statesToPurge = new ArrayList<>();

        int index;
        Path stateFile;
        Path nextStateFile;
        for (index = 1; ; index++) {
            nextStateFile = stateManager.getStateFile(index + 1);
            if (!Files.exists(nextStateFile)) {
                break;
            }

            stateFile = stateManager.getStateFile(index);
            statesToPurge.add(stateFile);
        }

        int statesPurgedCount = statesToPurge.size();
        if (statesPurgedCount == 0) {
            Logger.info("No State to purge");
        } else {
            Logger.out.printf("You are going to delete the %d previous State %s, keeping only the last one%n",
                    statesPurgedCount, plural("file", statesPurgedCount));
            if (confirmAction(context, "remove them")) {
                for (Path stateToDelete : statesToPurge) {
                    Files.delete(stateToDelete);
                }
            }
        }
        return statesPurgedCount;
    }
}
