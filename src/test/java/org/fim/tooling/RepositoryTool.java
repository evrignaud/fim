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
package org.fim.tooling;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.fim.model.Context;
import org.fim.model.HashMode;

public class RepositoryTool
{
	public static final int FILE_SIZE = 10 * 1024 * 1024;

	private Path rootDir;
	private int fileCount;

	public RepositoryTool(Path rootDir)
	{
		this.rootDir = rootDir;
		this.fileCount = 0;
	}

	public Context createContext(HashMode hashMode, boolean verbose)
	{
		Context context = new Context();
		context.setHashMode(hashMode);
		context.setAlwaysYes(true);
		context.setCurrentDirectory(rootDir);
		context.setRepositoryRootDir(rootDir);
		context.setVerbose(verbose);
		context.setComment("Using hash mode " + hashMode);
		return context;
	}

	public void createASetOfFiles(int fileCount) throws IOException
	{
		for (int index = 1; index <= fileCount; index++)
		{
			createFile("file" + String.format("%02d", index));
		}
	}

	public void touch(String fileName) throws IOException
	{
		Path file = rootDir.resolve(fileName);
		long timeStamp = Math.max(System.currentTimeMillis(), Files.getLastModifiedTime(file).toMillis());
		timeStamp += 1000;
		Files.setLastModifiedTime(file, FileTime.fromMillis(timeStamp));
	}

	public void createFimIgnore(Path directory, String content) throws IOException
	{
		Path file = directory.resolve(".fimignore");
		if (Files.exists(file))
		{
			Files.delete(file);
		}
		Files.write(file, content.getBytes(), CREATE, APPEND);
	}

	public void createFile(String fileName) throws IOException
	{
		Path file = rootDir.resolve(fileName);
		createFile(file);
	}

	public void createFile(Path file) throws IOException
	{
		setFileContent(file, "File content " + String.format("%02d", fileCount));
		fileCount++;
	}

	public void setFileContent(String fileName, String content) throws IOException
	{
		Path file = rootDir.resolve(fileName);
		setFileContent(file, content);
	}

	public void setFileContent(Path file, String content) throws IOException
	{
		if (Files.exists(file))
		{
			Files.delete(file);
		}

		// Creates a big content based on the provided content
		int fileSize = FILE_SIZE + (301457 * fileCount);
		StringBuilder sb = new StringBuilder(fileSize);
		int index = 0;
		while (sb.length() < fileSize)
		{
			index++;
			sb.append("b_").append(index).append(": ").append(content).append('\n');
		}

		Files.write(file, sb.toString().getBytes(), CREATE, APPEND);
	}
}
