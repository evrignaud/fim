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

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.HashMode.hashAll;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.model.State;
import org.fim.tooling.RepositoryTool;
import org.junit.Before;
import org.junit.Test;

public class LinuxResetFileAttrsCommandTest
{
	private static Path rootDir = Paths.get("target/" + CorruptCommandTest.class.getSimpleName());

	private InitCommand initCommand;
	private DiffCommand diffCommand;
	private ResetFileAttributesCommand resetFileAttributesCommand;

	private RepositoryTool tool;

	@Before
	public void setup() throws IOException
	{
		assumeTrue(!SystemUtils.IS_OS_WINDOWS);

		FileUtils.deleteDirectory(rootDir.toFile());
		Files.createDirectories(rootDir);

		initCommand = new InitCommand();
		diffCommand = new DiffCommand();
		resetFileAttributesCommand = new ResetFileAttributesCommand();

		tool = new RepositoryTool(rootDir);
	}

	@Test
	public void weCanResetFileAttributes() throws Exception
	{
		Context context = tool.createContext(hashAll, true);

		tool.createASetOfFiles(5);

		State state = (State) initCommand.execute(context);
		assertThat(state.getModificationCounts().getAdded()).isEqualTo(5);

		int fileResetCount = (int) resetFileAttributesCommand.execute(context);
		assertThat(fileResetCount).isEqualTo(0);

		doSomeModifications();

		CompareResult compareResult = (CompareResult) diffCommand.execute(context);
		assertThat(compareResult.modifiedCount()).isEqualTo(3);
		assertThat(compareResult.getDateModified().size()).isEqualTo(2);
		assertThat(compareResult.getAttributesModified().size()).isEqualTo(1);

		fileResetCount = (int) resetFileAttributesCommand.execute(context);
		assertThat(fileResetCount).isEqualTo(3);

		compareResult = (CompareResult) diffCommand.execute(context);
		assertThat(compareResult.modifiedCount()).isEqualTo(0);
	}

	private void doSomeModifications() throws IOException
	{
		tool.sleepSafely(1_000); // Ensure to increase lastModified at least of 1 second

		tool.touchCreationTime("file01");
		tool.setPermissions("file01", "rwx------");

		tool.touchLastModified("file02");
		tool.setPermissions("file02", "r-x------");

		tool.touchLastModified("file03");
		tool.setPermissions("file03", "r-xr-x---");
	}
}
