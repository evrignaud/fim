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

public class CorruptCommand extends AbstractCommand
{
	@Override
	public String getCmdName()
	{
		return "corrupt";
	}

	@Override
	public String getShortCmdName()
	{
		return "";
	}

	@Override
	public String getDescription()
	{
		return "Find changes most likely caused by a hardware corruption or a filesystem bug.\n" +
				"                                Change in content, but not in creation time and last modified time";
	}

	@Override
	public Object execute(Context context) throws Exception
	{
		fileContentHashingMandatory(context);

		checkHashMode(context, Option.ALLOW_COMPATIBLE);

		State currentState = new StateGenerator(context).generateState("", context.getRepositoryRootDir(), context.getCurrentDirectory());
		State lastState = new StateManager(context).loadLastState();

		if (context.isInvokedFromSubDirectory())
		{
			lastState = lastState.filterDirectory(context.getRepositoryRootDir(), context.getCurrentDirectory(), true);
		}

		CompareResult result = new StateComparator(context, lastState, currentState).searchForHardwareCorruption().compare();
		result.displayChanges();
		return result;
	}
}
