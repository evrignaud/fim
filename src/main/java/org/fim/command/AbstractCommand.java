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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

import org.fim.internal.SettingsManager;
import org.fim.internal.StateGenerator;
import org.fim.model.Command;
import org.fim.model.Context;
import org.fim.model.HashMode;
import org.fim.util.Logger;

public abstract class AbstractCommand implements Command
{
	protected static final Path CURRENT_DIRECTORY = Paths.get(".");

	@Override
	public FimReposConstraint getFimReposConstraint()
	{
		return FimReposConstraint.MUST_EXIST;
	}

	protected void fileContentHashingMandatory(Context context)
	{
		if (context.getHashMode() == HashMode.dontHash)
		{
			System.err.println("File content hashing mandatory for this command.");
			System.exit(-1);
		}
	}

	protected void checkGlobalHashMode(Context context)
	{
		SettingsManager settingsManager = new SettingsManager(context);
		if (settingsManager.getGlobalHashMode() != HashMode.hashAll)
		{
			Logger.warning(String.format("This repository use a global hash mode. Hash mode forced to '%s'%n", StateGenerator.hashModeToString(settingsManager.getGlobalHashMode())));
			context.setHashMode(settingsManager.getGlobalHashMode());
		}
	}

	protected boolean confirmAction(Context context, String action)
	{
		if (context.isAlwaysYes())
		{
			return true;
		}

		Scanner scanner = new Scanner(System.in);
		System.out.printf("Do you really want to %s (y/n/A)? ", action);
		String str = scanner.next();
		if (str.equalsIgnoreCase("y"))
		{
			return true;
		}
		else if (str.equals("A"))
		{
			context.setAlwaysYes(true);
			return true;
		}
		return false;
	}
}
