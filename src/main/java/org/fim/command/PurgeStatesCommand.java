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

import java.nio.file.Files;
import java.nio.file.Path;

import org.fim.internal.StateManager;
import org.fim.model.Context;

public class PurgeStatesCommand extends AbstractCommand
{
	@Override
	public String getCmdName()
	{
		return "purge-states";
	}

	@Override
	public String getShortCmdName()
	{
		return "purge";
	}

	@Override
	public String getDescription()
	{
		return "Purge previous States";
	}

	@Override
	public FimReposConstraint getFimReposConstraint()
	{
		return FimReposConstraint.MUST_EXIST;
	}

	@Override
	public void execute(Context context) throws Exception
	{
		StateManager stateManager = new StateManager(context);

		int index;
		Path stateFile;
		Path nextStateFile;
		for (index = 1; ; index++)
		{
			nextStateFile = stateManager.getStateFile(index + 1);
			if (!Files.exists(nextStateFile))
			{
				break;
			}

			stateFile = stateManager.getStateFile(index);
			Files.delete(stateFile);
		}

		stateFile = stateManager.getStateFile(index);
		Path newStateFile = stateManager.getStateFile(1);
		Files.move(stateFile, newStateFile);

		stateManager.saveLastStateNumber(1);
	}
}