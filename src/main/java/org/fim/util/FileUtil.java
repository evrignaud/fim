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

import java.io.File;
import java.nio.file.Path;

public class FileUtil
{
	public static String getNormalizedFileName(Path file)
	{
		String normalizedFileName = file.toAbsolutePath().normalize().toString();
		if (File.separatorChar != '/')
		{
			normalizedFileName = normalizedFileName.replace(File.separatorChar, '/');
		}
		return normalizedFileName;
	}

	public static String getRelativeFileName(String directory, String fileName)
	{
		String relativeFileName = fileName;
		if (relativeFileName.startsWith(directory))
		{
			relativeFileName = relativeFileName.substring(directory.length());
		}

		if (relativeFileName.startsWith("/"))
		{
			relativeFileName = relativeFileName.substring(1);
		}
		return relativeFileName;
	}
}
