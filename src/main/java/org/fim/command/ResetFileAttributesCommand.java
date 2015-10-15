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

import static org.fim.model.FileAttribute.SELinuxLabel;
import static org.fim.model.FileAttribute.dosFilePermissions;
import static org.fim.model.FileAttribute.posixFilePermissions;
import static org.fim.util.FormatUtil.formatDate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.SystemUtils;
import org.fim.internal.StateManager;
import org.fim.model.Context;
import org.fim.model.FileAttribute;
import org.fim.model.FileState;
import org.fim.model.State;
import org.fim.util.Console;
import org.fim.util.DosFilePermissions;
import org.fim.util.Logger;
import org.fim.util.SELinux;

public class ResetFileAttributesCommand extends AbstractCommand
{
	@Override
	public String getCmdName()
	{
		return "reset-file-attrs";
	}

	@Override
	public String getShortCmdName()
	{
		return "rfa";
	}

	@Override
	public String getDescription()
	{
		return "Reset the files attributes like they were stored in the last committed State";
	}

	@Override
	public Object execute(Context context) throws Exception
	{
		StateManager manager = new StateManager(context);
		State lastState = manager.loadLastState();

		Logger.info(String.format("Reset files attributes based on the last committed State done %s", formatDate(lastState.getTimestamp())));
		if (lastState.getComment().length() > 0)
		{
			System.out.println("Comment: " + lastState.getComment());
		}
		Console.newLine();

		if (context.isInvokedFromSubDirectory())
		{
			lastState = lastState.filterDirectory(context.getRepositoryRootDir(), context.getCurrentDirectory(), true);
		}

		int attrResetCount = 0;
		for (FileState fileState : lastState.getFileStates())
		{
			Path file = context.getRepositoryRootDir().resolve(fileState.getFileName());
			if (Files.exists(file))
			{
				boolean attrReset = false;

				BasicFileAttributes attributes;

				if (SystemUtils.IS_OS_WINDOWS)
				{
					DosFileAttributes dosFileAttributes = Files.readAttributes(file, DosFileAttributes.class);
					attributes = dosFileAttributes;

					String dosPermissions = DosFilePermissions.toString(dosFileAttributes);
					String previousDosPermissions = getAttribute(fileState, dosFilePermissions);
					if (!Objects.equals(dosPermissions, previousDosPermissions))
					{
						attrReset = true;
						DosFilePermissions.setPermissions(file, previousDosPermissions);
						System.out.printf("Set permissions: %s \t%s -> %s%n", fileState.getFileName(), dosPermissions, previousDosPermissions);
					}
				}
				else
				{
					PosixFileAttributes posixFileAttributes = Files.readAttributes(file, PosixFileAttributes.class);
					attributes = posixFileAttributes;

					String posixPermissions = PosixFilePermissions.toString(posixFileAttributes.permissions());
					String previousPosixPermissions = getAttribute(fileState, posixFilePermissions);
					if (!Objects.equals(posixPermissions, previousPosixPermissions))
					{
						attrReset = true;
						Set<PosixFilePermission> permissions = PosixFilePermissions.fromString(previousPosixPermissions);
						Files.getFileAttributeView(file, PosixFileAttributeView.class).setPermissions(permissions);
						System.out.printf("Set permissions: %s \t%s -> %s%n", fileState.getFileName(), posixPermissions, previousPosixPermissions);
					}
				}

				long creationTime = attributes.creationTime().toMillis();
				long previousCreationTime = fileState.getFileTime().getCreationTime();
				if (creationTime != previousCreationTime)
				{
					attrReset = true;
					setCreationTime(file, FileTime.fromMillis(previousCreationTime));
					System.out.printf("Set creation Time: %s \t%s -> %s%n", fileState.getFileName(), formatDate(creationTime), formatDate(previousCreationTime));
				}

				long lastModified = attributes.lastModifiedTime().toMillis();
				long previousLastModified = fileState.getFileTime().getLastModified();
				if (lastModified != previousLastModified)
				{
					attrReset = true;
					Files.setLastModifiedTime(file, FileTime.fromMillis(previousLastModified));
					System.out.printf("Set last modified: %s \t%s -> %s%n", fileState.getFileName(), formatDate(lastModified), formatDate(previousLastModified));
				}

				if (SELinux.ENABLED)
				{
					String label = SELinux.getLabel(file);
					String previousLabel = getAttribute(fileState, SELinuxLabel);
					if (!Objects.equals(label, previousLabel))
					{
						attrReset = true;
						SELinux.setLabel(file, previousLabel);
						System.out.printf("Set SELinux: %s \t%s -> %s%n", fileState.getFileName(), label, previousLabel);
					}
				}

				if (attrReset)
				{
					attrResetCount++;
				}
			}
		}

		if (attrResetCount == 0)
		{
			Logger.info("No file modification date have been reset");
		}
		else
		{
			Console.newLine();
			Logger.info(String.format("%d file attributes have been reset", attrResetCount));
		}
		return null;
	}

	private String getAttribute(FileState fileState, FileAttribute fileAttribute)
	{
		Map<String, String> fileAttributes = fileState.getFileAttributes();
		return fileAttributes != null ? fileAttributes.get(fileAttribute.name()) : null;
	}

	private void setCreationTime(Path file, FileTime creationTime) throws IOException
	{
		Files.getFileAttributeView(file, BasicFileAttributeView.class).setTimes(null, null, creationTime);
	}
}
