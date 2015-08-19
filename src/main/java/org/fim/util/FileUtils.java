package org.fim.util;

import java.io.File;
import java.nio.file.Path;

/**
 * Created by evrignaud on 19/08/15.
 */
public class FileUtils
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
		if (fileName.startsWith(directory))
		{
			fileName = fileName.substring(directory.length());
		}

		if (fileName.startsWith("/"))
		{
			fileName = fileName.substring(1);
		}
		return fileName;
	}
}
