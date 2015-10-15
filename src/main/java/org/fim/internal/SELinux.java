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
package org.fim.internal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.lang3.SystemUtils;
import org.fim.util.CommandUtil;
import org.fim.util.Logger;

public class SELinux
{
	public static final boolean ENABLED = isEnabled();

	/**
	 * Check whether SELinux is enabled or not.
	 */
	private static boolean isEnabled()
	{
		if (SystemUtils.IS_OS_WINDOWS)
		{
			return false;
		}

		try
		{
			List<String> lines = CommandUtil.execCmdAndGetLines("sestatus");
			for (String line : lines)
			{
				if (line.contains("SELinux status"))
				{
					if (line.contains("enabled"))
					{
						System.out.println("SELinux enabled");
						return true;
					}

					return false;
				}
			}
		}
		catch (IOException ex)
		{
			// Never mind
		}
		return false;
	}

	/**
	 * Retrieve the SELinux label of the specified file
	 */
	public static String getLabel(Path file)
	{
		String fileName = file.normalize().toAbsolutePath().toString();
		try
		{
			String line = CommandUtil.execCmd("ls -1Z " + fileName);
			String[] strings = line.split(" ");
			if (strings.length == 2)
			{
				return strings[0];
			}
		}
		catch (IOException ex)
		{
			Logger.error("Error retrieving SELinux label for '" + fileName + "'", ex);
		}

		return null;
	}
}