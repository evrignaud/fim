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
import org.fim.model.LogEntry;
import org.fim.model.LogResult;
import org.fim.model.State;
import org.fim.util.Logger;

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
		return "Display the history of the States and a summary of the changes that were made";
	}

	@Override
	public Object execute(Context context) throws Exception
	{
		StateManager manager = new StateManager(context);

		if (manager.getLastStateNumber() == -1)
		{
			Logger.error("No State found");
			return null;
		}

		LogResult logResult = new LogResult();
		for (int stateNumber = 1; stateNumber <= manager.getLastStateNumber(); stateNumber++)
		{
			Path statFile = manager.getStateFile(stateNumber);
			if (Files.exists(statFile))
			{
				State state = manager.loadState(stateNumber);
				LogEntry logEntry = new LogEntry();
				logEntry.setStateNumber(stateNumber);
				logEntry.setComment(state.getComment());
				logEntry.setTimestamp(state.getTimestamp());
				logEntry.setFileCount(state.getFileCount());
				logEntry.setFilesContentLength(state.getFilesContentLength());
				logEntry.setModificationCounts(state.getModificationCounts());
				logResult.add(logEntry);
			}
		}

		logResult.displayEntries();
		return logResult;
	}
}
