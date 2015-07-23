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

import static org.fim.util.FormatUtil.formatDate;

import java.nio.file.Files;
import java.nio.file.Path;

import org.fim.internal.StateManager;
import org.fim.model.Parameters;
import org.fim.model.State;

public class LogCommand extends AbstractCommand
{
	@Override
	public String getCmdName()
	{
		return "log";
	}

	@Override
	public String getShortCmdName()
	{
		return "";
	}

	@Override
	public String getDescription()
	{
		return "Display States log";
	}

	@Override
	public void execute(Parameters parameters) throws Exception
	{
		StateManager manager = new StateManager(parameters);

		if (manager.getLastStateNumber() == -1)
		{
			System.out.println("No State found");
			return;
		}

		for (int stateNumber = 1; stateNumber <= manager.getLastStateNumber(); stateNumber++)
		{
			Path statFile = manager.getStateFile(stateNumber);
			if (Files.exists(statFile))
			{
				State state = manager.loadState(stateNumber);
				System.out.printf("State #%d: %s%n", stateNumber, formatDate(state.getTimestamp()));
				if (state.getComment().length() > 0)
				{
					System.out.printf("\tComment: %s%n", state.getComment());
				}
				System.out.printf("\tContains %d files%n", state.getFileCount());
				System.out.println("");
			}
		}
	}
}
