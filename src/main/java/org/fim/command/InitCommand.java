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
import java.nio.file.Files;

import org.fim.internal.StateComparator;
import org.fim.internal.StateGenerator;
import org.fim.internal.StateManager;
import org.fim.model.CompareResult;
import org.fim.model.Parameters;
import org.fim.model.State;

public class InitCommand extends AbstractCommand
{
	@Override
	public String getCmdName()
	{
		return "init";
	}

	@Override
	public String getShortCmdName()
	{
		return "";
	}

	@Override
	public String getDescription()
	{
		return "Initialize a Fim repository";
	}

	@Override
	public FimReposConstraint getFimReposConstraint()
	{
		return FimReposConstraint.MUST_NOT_EXIST;
	}

	@Override
	public void execute(Parameters parameters) throws Exception
	{
		computeAllHashMandatory(parameters);

		try
		{
			Files.createDirectories(parameters.getDefaultStateDir());
		}
		catch (IOException ex)
		{
			System.err.printf("Not able to create the '%s' directory that holds the Fim repository: %s %s%n",
					Parameters.DOT_FIM_DIR, ex.getClass().getSimpleName(), ex.getMessage());
			System.exit(-1);
		}

		String comment = parameters.getComment();
		if (comment.length() == 0)
		{
			comment = "Initial State";
		}
		State currentState = new StateGenerator(parameters).generateState(comment, CURRENT_DIRECTORY);

		CompareResult result = new StateComparator(parameters).compare(null, currentState).displayChanges();
		currentState.setModificationCounts(result.getModificationCounts());

		new StateManager(parameters).createNewState(currentState);
	}
}
