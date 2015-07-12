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

import java.io.File;
import java.util.Scanner;

import org.fim.model.Command;
import org.fim.model.HashMode;
import org.fim.model.Parameters;

public abstract class AbstractCommand implements Command
{
	protected static final File CURRENT_DIRECTORY = new File(".");

	@Override
	public FimReposConstraint getFimReposConstraint()
	{
		return FimReposConstraint.MUST_EXIST;
	}

	protected void fileContentHashingMandatory(Parameters parameters)
	{
		if (parameters.getHashMode() == HashMode.DONT_HASH_FILES)
		{
			System.err.println("File content hashing mandatory for this command.");
			System.exit(-1);
		}
	}

	protected boolean confirmAction(Parameters parameters, String action)
	{
		if (parameters.isAlwaysYes())
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
			parameters.setAlwaysYes(true);
			return true;
		}
		return false;
	}
}
