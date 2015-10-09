/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2015  Etienne Vrignaud
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

import java.io.IOException;

import org.fim.internal.StateComparator;
import org.fim.internal.StateGenerator;
import org.fim.internal.StateManager;
import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.model.State;
import org.fim.util.Console;
import org.fim.util.Logger;

public class CommitCommand extends AbstractCommand
{
	@Override
	public String getCmdName()
	{
		return "commit";
	}

	@Override
	public String getShortCmdName()
	{
		return "ci";
	}

	@Override
	public String getDescription()
	{
		return "Commit the current directory State";
	}

	@Override
	public Object execute(Context context) throws Exception
	{
		checkHashMode(context, Option.ALL_HASH_MANDATORY);

		if (context.getComment().length() == 0)
		{
			System.out.println("No comment provided. You are going to commit your modifications without any comment.");
			if (!confirmAction(context, "continue"))
			{
				System.exit(0);
			}
		}

		StateManager manager = new StateManager(context);
		State lastState = manager.loadLastState();
		State lastStateToCompare = lastState;
		State currentState = new StateGenerator(context).generateState(context.getComment(), context.getRepositoryRootDir(), context.getCurrentDirectory());

		if (context.isInvokedFromSubDirectory())
		{
			if (!lastState.getModelVersion().equals(currentState.getModelVersion()))
			{
				Logger.error("Not able to incrementally commit into the last State that use a different model version.");
				System.exit(-1);
			}

			lastStateToCompare = lastState.filterDirectory(context.getRepositoryRootDir(), context.getCurrentDirectory(), true);
		}

		CompareResult result = new StateComparator(context, lastStateToCompare, currentState).compare().displayChanges();
		if (result.somethingModified())
		{
			Console.newLine();
			if (confirmAction(context, "commit"))
			{
				currentState.setModificationCounts(result.getModificationCounts());

				if (context.isInvokedFromSubDirectory())
				{
					currentState = createConsolidatedState(context, lastState, currentState);
				}

				manager.createNewState(currentState);
			}
			else
			{
				Logger.info("Nothing committed");
			}
		}

		return result;
	}

	private State createConsolidatedState(Context context, State lastState, State currentState) throws IOException
	{
		State filteredState = lastState.filterDirectory(context.getRepositoryRootDir(), context.getCurrentDirectory(), false);

		State consolidatedState = currentState.clone();
		consolidatedState.getFileStates().addAll(filteredState.getFileStates());
		consolidatedState.getModificationCounts().add(filteredState.getModificationCounts());
		consolidatedState.getIgnoredFiles().addAll(lastState.getIgnoredFiles());

		return consolidatedState;
	}
}
