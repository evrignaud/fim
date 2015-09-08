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
import org.fim.model.Context;
import org.fim.model.ModificationCounts;
import org.fim.model.State;
import org.fim.util.Console;
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
	public void execute(Context context) throws Exception
	{
		StateManager manager = new StateManager(context);

		if (manager.getLastStateNumber() == -1)
		{
			Logger.error("No State found");
			return;
		}

		for (int stateNumber = 1; stateNumber <= manager.getLastStateNumber(); stateNumber++)
		{
			Path statFile = manager.getStateFile(stateNumber);
			if (Files.exists(statFile))
			{
				State state = manager.loadState(stateNumber);
				System.out.printf("State #%d: %s (%d files)%n", stateNumber, formatDate(state.getTimestamp()), state.getFileCount());
				if (state.getComment().length() > 0)
				{
					System.out.printf("\tComment: %s%n", state.getComment());
				}
				displayCounts(state.getModificationCounts());
				Console.newLine();
			}
		}
	}

	private void displayCounts(ModificationCounts modificationCounts)
	{
		if (modificationCounts == null)
		{
			return;
		}

		String message = "";
		if (modificationCounts.getAdded() > 0)
		{
			message += "" + modificationCounts.getAdded() + " added, ";
		}

		if (modificationCounts.getCopied() > 0)
		{
			message += "" + modificationCounts.getCopied() + " copied, ";
		}

		if (modificationCounts.getDuplicated() > 0)
		{
			message += "" + modificationCounts.getDuplicated() + " duplicated, ";
		}

		if (modificationCounts.getDateModified() > 0)
		{
			message += "" + modificationCounts.getDateModified() + " date modified, ";
		}

		if (modificationCounts.getContentModified() > 0)
		{
			message += "" + modificationCounts.getContentModified() + " content modified, ";
		}

		if (modificationCounts.getRenamed() > 0)
		{
			message += "" + modificationCounts.getRenamed() + " renamed, ";
		}

		if (modificationCounts.getDeleted() > 0)
		{
			message += "" + modificationCounts.getDeleted() + " deleted, ";
		}

		message = message.replaceAll(", $", "");
		System.out.printf("\t%s%n", message);
	}
}
