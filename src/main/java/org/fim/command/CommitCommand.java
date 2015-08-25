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

import org.fim.internal.StateComparator;
import org.fim.internal.StateGenerator;
import org.fim.internal.StateManager;
import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.model.State;

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
	public void execute(Context context) throws Exception
	{
		checkHashMode(context, false);

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
		State currentState = new StateGenerator(context).generateState(context.getComment(), context.getRepositoryRootDir(), CURRENT_DIRECTORY);

		if (context.isInvokedFromSubDirectory())
		{
			lastState.filterDirectory(context.getRepositoryRootDir(), CURRENT_DIRECTORY, true);
		}

		CompareResult result = new StateComparator(context).compare(lastState, currentState).displayChanges();
		if (result.somethingModified())
		{
			System.out.println("");
			if (confirmAction(context, "commit"))
			{
				if (context.isInvokedFromSubDirectory())
				{
					lastState = manager.loadLastState();
					lastState.filterDirectory(context.getRepositoryRootDir(), CURRENT_DIRECTORY, false);

					currentState.getFileStates().addAll(lastState.getFileStates());
				}

				currentState.setModificationCounts(result.getModificationCounts());
				manager.createNewState(currentState);
			}
			else
			{
				System.out.println("Nothing committed");
			}
		}
	}
}
