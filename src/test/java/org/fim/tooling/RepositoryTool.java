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

import static java.nio.file.StandardOpenOption.CREATE;
import static org.fim.model.HashMode.hashAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import org.apache.commons.lang3.SystemUtils;
import org.fim.model.Context;
import org.fim.model.HashMode;
import org.fim.util.DosFilePermissions;

public class RepositoryTool
{
	public static final int FILE_SIZE = 10 * 1_024 * 1_024;

	private Path rootDir;
	private int fileCount;
	private Context context;

	public RepositoryTool(Path rootDir)
	{
		this.rootDir = rootDir;
		this.fileCount = 1;

		createContext(hashAll, true); // Default context
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

		this.context = context;

		return context;
	}

	public void createASetOfFiles(int count) throws IOException
	{
		for (int index = 1; index <= count; index++)
		{
			createOneFile();
		}
	}

	public void createOneFile() throws IOException
	{
		createFile("file" + String.format("%02d", fileCount));
	}

	public void touchCreationTime(String fileName) throws IOException
	{
		Path file = rootDir.resolve(fileName);
		long timeStamp = Math.max(System.currentTimeMillis(), getCreationTime(file).toMillis());
		timeStamp += 1_000;
		setCreationTime(file, FileTime.fromMillis(timeStamp));
	}

	public void touchLastModified(String fileName) throws IOException
	{
		Path file = rootDir.resolve(fileName);
		long timeStamp = Math.max(System.currentTimeMillis(), Files.getLastModifiedTime(file).toMillis());
		timeStamp += 1_000;
		Files.setLastModifiedTime(file, FileTime.fromMillis(timeStamp));
	}

	public void createFimIgnore(Path directory, String content) throws IOException
	{
		Path file = directory.resolve(".fimignore");
		if (Files.exists(file))
		{
			Files.delete(file);
		}
		Files.write(file, content.getBytes(), CREATE);
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
		int fileSize = FILE_SIZE + (301_457 * fileCount);
		StringBuilder sb = new StringBuilder(fileSize);
		int index = 0;
		while (sb.length() < fileSize)
		{
			index++;
			sb.append("b_").append(index).append(": ").append(content).append('\n');
		}

		Files.write(file, sb.toString().getBytes(), CREATE);
	}

	public void setPermissions(String fileName, String posixPermissions, String dosPermissions) throws IOException
	{
		Path file = rootDir.resolve(fileName);
		if (SystemUtils.IS_OS_WINDOWS)
		{
			DosFilePermissions.setPermissions(context, file, dosPermissions);
		}
		else
		{
			Set<PosixFilePermission> permissionSet = PosixFilePermissions.fromString(posixPermissions);
			Files.getFileAttributeView(file, PosixFileAttributeView.class).setPermissions(permissionSet);
		}
	}

	public void sleepSafely(int millis)
	{
		try
		{
			Thread.sleep(millis);
		}
		catch (InterruptedException e)
		{
			// Never mind
		}
	}

	private FileTime getCreationTime(Path file) throws IOException
	{
		BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
		return attributes.creationTime();
	}

	private void setCreationTime(Path file, FileTime creationTime) throws IOException
	{
		Files.getFileAttributeView(file, BasicFileAttributeView.class).setTimes(null, null, creationTime);
	}
}
