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
import org.fim.internal.SettingsManager;
import org.fim.model.Command;
import org.fim.model.Context;
import org.fim.util.HashModeUtil;
import org.fim.util.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import static org.fim.model.HashMode.dontHash;
import static org.fim.model.HashMode.hashAll;
import static org.fim.util.HashModeUtil.hashModeToString;

public abstract class AbstractCommand implements Command {
    @Override
    public FimReposConstraint getFimReposConstraint() {
        return FimReposConstraint.MUST_EXIST;
    }

    protected void fileContentHashingMandatory(Context context) {
        if (context.getHashMode() == dontHash) {
            Logger.error("File content hashing mandatory for this command.");
            throw new BadFimUsageException();
        }
    }

    protected void checkHashMode(Context context, Option... options) {
        List<Option> optionList = Arrays.asList(options);
        SettingsManager settingsManager = new SettingsManager(context);
        if (settingsManager.getGlobalHashMode() != hashAll) {
            if (HashModeUtil.isCompatible(settingsManager.getGlobalHashMode(), context.getHashMode())) {
                if (optionList.contains(Option.ALLOW_COMPATIBLE)) {
                    Logger.info(String.format("Using global hash mode '%s' that is compatible with the current one",
                            hashModeToString(settingsManager.getGlobalHashMode())));
                } else {
                    Logger.warning(String.format(
                            "Using global hash mode '%s' that is compatible with the current one, but is not allowed by this command. Hash mode " +
                            "forced",
                            hashModeToString(settingsManager.getGlobalHashMode())));
                    context.setHashMode(settingsManager.getGlobalHashMode());
                }
            } else {
                Logger.warning(String.format("Using global hash mode '%s' that is not compatible with the current one. Hash mode forced",
                        hashModeToString(settingsManager.getGlobalHashMode())));
                context.setHashMode(settingsManager.getGlobalHashMode());
            }
        } else if (context.getHashMode() != hashAll) {
            if (optionList.contains(Option.ALL_HASH_MANDATORY)) {
                Logger.error("Computing all hash is mandatory");
                throw new BadFimUsageException();
            }
        }

        adjustThreadCount(context);
    }

    protected void adjustThreadCount(Context context) {
        if ((context.getThreadCount() != 1) && (context.getHashMode() == dontHash)) {
            context.setThreadCount(1);
            if (context.isThreadCountSpecified()) {
                Logger.info("Not hashing file content so thread count forced to 1");
            }
        }
    }

    protected static boolean confirmAction(Context context, String action) {
        return confirmAction(context, new Scanner(System.in), action);
    }

    protected static boolean confirmAction(Context context, Scanner scanner, String action) {
        if (context.isAlwaysYes()) {
            return true;
        }

        Logger.out.printf("Do you really want to %s (y/n/A)? ", action);
        String str = scanner.next();
        if ("y".equalsIgnoreCase(str)) {
            return true;
        } else if ("A".equals(str)) {
            context.setAlwaysYes(true);
            return true;
        }
        return false;
    }

    protected enum Option {
        ALLOW_COMPATIBLE,
        ALL_HASH_MANDATORY
    }
}
