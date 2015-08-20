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

import org.fim.internal.DuplicateFinder;
import org.fim.internal.StateGenerator;
import org.fim.internal.StateManager;
import org.fim.model.Context;
import org.fim.model.State;

public class FindDuplicatesCommand extends AbstractCommand
{
	@Override
	public String getCmdName()
	{
		return "find-duplicates";
	}

	@Override
	public String getShortCmdName()
	{
		return "fdup";
	}

	@Override
	public String getDescription()
	{
		return "Find local duplicated files in the Fim repository";
	}

	@Override
	public void execute(Context context) throws Exception
	{
		checkGlobalHashMode(context);

		fileContentHashingMandatory(context);

		System.out.println("Searching for duplicated files" + (context.isUseLastState() ? " from the last committed State" : ""));
		System.out.println("");

		State state;
		if (context.isUseLastState())
		{
			state = new StateManager(context).loadLastState();
		}
		else
		{
			state = new StateGenerator(context).generateState("", context.getRepositoryRootDir(), CURRENT_DIRECTORY);
		}

		new DuplicateFinder(context).findDuplicates(state).displayDuplicates();
	}
}
