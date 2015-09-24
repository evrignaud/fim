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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

import org.fim.internal.StateManager;
import org.fim.model.Context;
import org.fim.model.FileState;
import org.fim.model.State;
import org.fim.util.Console;
import org.fim.util.Logger;

public class ResetDateCommand extends AbstractCommand
{
	@Override
	public String getCmdName()
	{
		return "reset-date";
	}

	@Override
	public String getShortCmdName()
	{
		return "rdate";
	}

	@Override
	public String getDescription()
	{
		return "Reset the files modification date like it is stored in the last committed State";
	}

	@Override
	public void execute(Context context) throws Exception
	{
		StateManager manager = new StateManager(context);
		State lastState = manager.loadLastState();

		Logger.info(String.format("Reset files modification date based on the last committed State done %s", formatDate(lastState.getTimestamp())));
		if (lastState.getComment().length() > 0)
		{
			System.out.println("Comment: " + lastState.getComment());
		}
		Console.newLine();

		if (context.isInvokedFromSubDirectory())
		{
			lastState = lastState.filterDirectory(context.getRepositoryRootDir(), CURRENT_DIRECTORY, true);
		}

		int dateResetCount = 0;
		for (FileState fileState : lastState.getFileStates())
		{
			Path file = context.getRepositoryRootDir().resolve(fileState.getFileName());
			if (Files.exists(file))
			{
				BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);

				long creationTime = attributes.creationTime().toMillis();
				long lastModified = attributes.lastModifiedTime().toMillis();

				long previousCreationTime = fileState.getFileTime().getCreationTime();
				long previousLastModified = fileState.getFileTime().getLastModified();

				boolean dateReset = false;
				if (creationTime != previousCreationTime)
				{
					dateReset = true;
					setCreationTime(file, FileTime.fromMillis(previousCreationTime));
					System.out.printf("Set creation Time: %s \t%s -> %s%n", fileState.getFileName(), formatDate(creationTime), formatDate(previousCreationTime));
				}

				if (lastModified != previousLastModified)
				{
					dateReset = true;
					Files.setLastModifiedTime(file, FileTime.fromMillis(previousLastModified));
					System.out.printf("Set last modified: %s \t%s -> %s%n", fileState.getFileName(), formatDate(lastModified), formatDate(previousLastModified));
				}

				if (dateReset)
				{
					dateResetCount++;
				}
			}
		}

		if (dateResetCount == 0)
		{
			Logger.info("No file modification date have been reset");
		}
		else
		{
			Console.newLine();
			Logger.info(String.format("%d file modification dates have been reset", dateResetCount));
		}
	}

	private void setCreationTime(Path file, FileTime creationTime) throws IOException
	{
		Files.getFileAttributeView(file, BasicFileAttributeView.class).setTimes(null, null, creationTime);
	}
}
