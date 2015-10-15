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
package org.fim.util;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class CommandUtil
{
	/**
	 * Execute a command and return all the output.
	 */
	public static String execCmd(String cmd) throws java.io.IOException
	{
		Process proc = Runtime.getRuntime().exec(cmd);
		try (InputStream is = proc.getInputStream();
			 Scanner scanner = new Scanner(is).useDelimiter("$"))
		{
			String val = scanner.hasNext() ? scanner.next() : "";
			return val;
		}
	}

	/**
	 * Execute a command and return all the lines of the output.
	 */
	public static List<String> execCmdAndGetLines(String cmd) throws java.io.IOException
	{
		Process proc = Runtime.getRuntime().exec(cmd);
		try (InputStream is = proc.getInputStream();
			 Scanner scanner = new Scanner(is).useDelimiter("\n"))
		{
			List<String> lines = new ArrayList<>();
			while (scanner.hasNext())
			{
				lines.add(scanner.next());
			}

			return lines;
		}
	}
}
