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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

import org.fim.model.Context;
import org.fim.model.FileToIgnore;
import org.fim.util.FileUtil;
import org.fim.util.Logger;

public class FimIgnoreManager
{
	public static final String DOT_FIM_IGNORE = ".fimignore";
	public static final String SUBDIRECTORY_MATCH = "**/";

	public static final Set IGNORED_DIRECTORIES = new HashSet<>(Arrays.asList(Context.DOT_FIM_DIR, ".git", ".svn", ".cvs"));

	private String repositoryRootDirString;
	private List<String> ignoredFiles;

	public FimIgnoreManager(Context context)
	{
		this.repositoryRootDirString = context.getRepositoryRootDir().toString();
		this.ignoredFiles = new ArrayList<>();
	}

	public Set<FileToIgnore> loadFimIgnore(Path directory)
	{
		Set<FileToIgnore> localIgnoreSet = new HashSet<>();

		Path dotFimIgnore = directory.resolve(DOT_FIM_IGNORE);
		if (Files.exists(dotFimIgnore))
		{
			try
			{
				List<String> allLines = Files.readAllLines(dotFimIgnore);
				for (String line : allLines)
				{
					FileToIgnore fileToIgnore = new FileToIgnore(line);
					localIgnoreSet.add(fileToIgnore);
				}
			}
			catch (IOException e)
			{
				Logger.error(String.format("Unable to read file %s: %s", dotFimIgnore, e.getMessage()));
			}
		}

		return localIgnoreSet;
	}

	public Set<FileToIgnore> buildSubDirectoriesIgnoreSet(Set<FileToIgnore> thisDirectoryIgnoreList, Set<FileToIgnore> currentIgnoreSet)
	{
		Set<FileToIgnore> subDirectoriesIgnoreSet = new HashSet<>(thisDirectoryIgnoreList);
		for (FileToIgnore fileToIgnore : currentIgnoreSet)
		{
			if (fileToIgnore.getRegexpFileName().startsWith(SUBDIRECTORY_MATCH))
			{
				String regexpFileName = fileToIgnore.getRegexpFileName().substring(SUBDIRECTORY_MATCH.length());
				subDirectoriesIgnoreSet.add(new FileToIgnore(regexpFileName));
			}
		}
		return subDirectoriesIgnoreSet;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void addToIgnoredFiles(Path file, BasicFileAttributes attributes)
	{
		String normalizedFileName = FileUtil.getNormalizedFileName(file);
		if (attributes.isDirectory())
		{
			normalizedFileName = normalizedFileName + "/";
		}

		String relativeFileName = FileUtil.getRelativeFileName(repositoryRootDirString, normalizedFileName);
		ignoredFiles.add(relativeFileName);
	}

	public boolean isIgnored(Path file, BasicFileAttributes attributes, Set<FileToIgnore> filesToIgnore)
	{
		String fileName = file.getFileName().toString();
		if (attributes.isDirectory() && IGNORED_DIRECTORIES.contains(fileName))
		{
			return true;
		}

		for (FileToIgnore fileToIgnore : filesToIgnore)
		{
			if (fileToIgnore.getCompiledFilename() != null)
			{
				Matcher matcher = fileToIgnore.getCompiledFilename().matcher(fileName);
				if (matcher.find())
				{
					return true;
				}
			}
			else if (fileToIgnore.getRegexpFileName().equals(fileName))
			{
				return true;
			}
		}

		return false;
	}

	public List<String> getIgnoredFiles()
	{
		return ignoredFiles;
	}
}
