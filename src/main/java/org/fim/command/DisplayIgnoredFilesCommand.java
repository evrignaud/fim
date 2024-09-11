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
import org.fim.model.State;
import org.fim.util.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.atteo.evo.inflector.English.plural;
import static org.fim.util.FileUtil.byteCountToDisplaySize;
import static org.fim.util.FormatUtil.formatDate;

public class DisplayIgnoredFilesCommand extends AbstractCommand {
    @Override
    public String getCmdName() {
        return "display-ignored";
    }

    @Override
    public String getShortCmdName() {
        return "dign";
    }

    @Override
    public String getDescription() {
        return "Display the files or directories that are ignored into the last State";
    }

    @Override
    public Object execute(Context context) throws Exception {
        StateManager manager = new StateManager(context);

        int lastStateNumber = manager.getLastStateNumber();
        if (lastStateNumber == -1) {
            Logger.error("No State found");
            return null;
        }

        Path statFile = manager.getStateFile(lastStateNumber);
        if (Files.exists(statFile)) {
            State state = manager.loadState(lastStateNumber);
            Logger.out.printf("Files or directories ignored in State #%d: %s (%d %s - %s)%n", lastStateNumber, formatDate(state.getTimestamp()),
                    state.getFileCount(), plural("file", state.getFileCount()),
                    byteCountToDisplaySize(state.getFilesContentLength()));
            if (state.getComment().length() > 0) {
                Logger.out.printf("\tComment: %s%n", state.getComment());
            }
            Set<String> ignoredFiles = state.getIgnoredFiles();
            displayIgnore(ignoredFiles);
            Logger.newLine();

            return ignoredFiles;
        }

        return null;
    }

    private void displayIgnore(Set<String> ignoredFiles) {
        if (ignoredFiles == null || ignoredFiles.isEmpty()) {
            Logger.newLine();
            Logger.out.println("No files or directories ignored into this State");
        } else {
            Logger.newLine();

            for (String ignoredFile : ignoredFiles) {
                Logger.out.printf("\t%s%n", ignoredFile);
            }
        }
    }
}
