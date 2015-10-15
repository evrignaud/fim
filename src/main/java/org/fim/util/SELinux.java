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

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.SystemUtils;

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
			List<String> lines = CommandUtil.execCmdAndGetLines(Arrays.asList("sestatus"));
			for (String line : lines)
			{
				if (line.contains("SELinux status"))
				{
					if (line.contains("enabled"))
					{
						Logger.info("SELinux is enabled on this system");
						return true;
					}

					return false;
				}
			}
		}
		catch (Exception ex)
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
			String line = CommandUtil.execCmd(Arrays.asList("ls", "-1Z", fileName));
			String[] strings = line.split(" ");
			if (strings.length == 2)
			{
				return strings[0];
			}
		}
		catch (Exception ex)
		{
			Logger.error("Error retrieving SELinux label for '" + file + "'", ex);
		}

		return null;
	}

	public static void setLabel(Path file, String label)
	{
		String fileName = file.normalize().toAbsolutePath().toString();
		try
		{
			CommandUtil.execCmd(Arrays.asList("chcon", label, fileName));
		}
		catch (Exception ex)
		{
			Logger.error("Error setting SELinux label for '" + file + "'", ex);
		}
	}
}