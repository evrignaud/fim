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
import org.fim.model.FimOptions;
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
		return "Find duplicated files";
	}

	@Override
	public void execute(FimOptions fimOptions) throws Exception
	{
		StateGenerator generator = new StateGenerator(fimOptions.getThreadCount(), fimOptions.getCompareMode());
		StateManager manager = new StateManager(fimOptions.getStateDir(), fimOptions.getCompareMode());
		DuplicateFinder finder = new DuplicateFinder();

		fastCompareNotSupported(fimOptions.getCompareMode());

		System.out.println("Searching for duplicated files" + (fimOptions.isUseLastState() ? " from the last committed State" : ""));
		System.out.println("");

		State state;
		if (fimOptions.isUseLastState())
		{
			state = manager.loadLastState();
		}
		else
		{
			state = generator.generateState(fimOptions.getMessage(), fimOptions.getBaseDirectory());
		}
		finder.findDuplicates(state).displayDuplicates(fimOptions.isVerbose());
	}
}
