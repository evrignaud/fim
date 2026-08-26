/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2026 Etienne Vrignaud
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
import org.fim.internal.DuplicateFinder;
import org.fim.internal.StateGenerator;
import org.fim.internal.StateManager;
import org.fim.model.Context;
import org.fim.model.DuplicateResult;
import org.fim.model.OutputType;
import org.fim.model.State;
import org.fim.util.Logger;

public class FindDuplicatesCommand extends AbstractCommand {
    @Override
    public String getCmdName() {
        return "find-duplicates";
    }

    @Override
    public String getShortCmdName() {
        return "fdup";
    }

    @Override
    public String getDescription() {
        return "Find local duplicate files in the Fim repository";
    }

    @Override
    public Object execute(Context context) throws Exception {
        checkHashMode(context, Option.ALLOW_COMPATIBLE);

        fileContentHashingMandatory(context);

        if (context.getOutputType() != OutputType.human && context.isRemoveDuplicates()) {
            Logger.error("You cannot display duplicates in a non human format and remove them");
            throw new BadFimUsageException();
        }

        if (context.isRemoveDuplicates() && context.isAlwaysYes() && !context.isCalledFromTest()) {
            explicitlyConfirmAutomaticRemoval(context);
        }

        Logger.info(String.format("Searching for duplicate files%s", context.isUseLastState() ? " from the last committed State" : ""));
        Logger.newLine();

        State state;
        if (context.isUseLastState()) {
            state = new StateManager(context).loadLastState();
        } else {
            state = new StateGenerator(context).generateState("", context.getRepositoryRootDir(), context.getCurrentDirectory());
        }

        DuplicateResult result = new DuplicateFinder(context).findDuplicates(state);
        result.displayAndRemoveDuplicates();
        return result;
    }

    /**
     * Ask once before removing every duplicate automatically, even though {@code -y} was given, because
     * that combination deletes files without showing them first.
     * <p>
     * {@code -y} has to be put back afterwards. It is cleared so that the question below is actually
     * asked, and {@link #confirmAction} only re-arms it when the answer is {@code A} — answering
     * {@code y} left it off, so {@link DuplicateResult#selectFilesToRemove} then prompted for every
     * single duplicate set and {@code -y} behaved as if it had never been passed.
     * <p>
     * Restored whatever the answer was: on refusal the command stops right here, so the value only
     * matters when the removal proceeds.
     */
    private void explicitlyConfirmAutomaticRemoval(Context context) {
        Logger.out.println("You are going to remove automatically all duplicates, keeping the first of each duplicate set.");

        context.setAlwaysYes(false);
        boolean confirmed = confirmAction(context, "continue");
        context.setAlwaysYes(true);

        if (!confirmed) {
            throw new DontWantToContinueException();
        }
        Logger.newLine();
    }
}
