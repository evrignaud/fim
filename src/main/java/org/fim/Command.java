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
package org.fim;

public enum Command
{
	INIT("init", "", "Initialize a Fim repository"),
	COMMIT("commit", "ci", "Commit the current directory State"),
	DIFF("diff", "", "Compare the current directory State with the previous one"),
	FIND_DUPLICATES("find-duplicates", "fdup", "Find duplicated files"),
	REMOVE_DUPLICATES("remove-duplicates", "rdup", "Remove duplicated files from local directory based on a master Fim repository"),
	LOG("log", "", "Display States log"),
	RESET_DATES("reset-dates", "rdates", "Reset the file modification dates like it's stored in the last committed State");

	private final String cmdName;
	private final String shortCmdName;
	private final String description;

	Command(String cmdName, String shortCmdName, String description)
	{
		this.cmdName = cmdName;
		this.shortCmdName = shortCmdName;
		this.description = description;
	}

	public static Command fromName(final String cmdName)
	{
		for (final Command command : values())
		{
			if (command.getCmdName().equals(cmdName))
			{
				return command;
			}

			if (command.getShortCmdName().equals(cmdName))
			{
				return command;
			}
		}
		return null;
	}

	public String getCmdName()
	{
		return cmdName;
	}

	public String getShortCmdName()
	{
		return shortCmdName;
	}

	public String getDescription()
	{
		return description;
	}
}
