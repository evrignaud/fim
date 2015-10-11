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

import static java.nio.file.StandardOpenOption.CREATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.HashMode.dontHash;
import static org.fim.model.HashMode.hashAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.fim.command.exception.BadFimUsageException;
import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.model.Difference;
import org.fim.model.FileState;
import org.fim.model.State;
import org.fim.tooling.RepositoryTool;
import org.junit.Before;
import org.junit.Test;

public class CorruptCommandTest
{
	private static Path rootDir = Paths.get("target/" + CorruptCommandTest.class.getSimpleName());

	private InitCommand initCommand;
	private DiffCommand diffCommand;
	private CommitCommand commitCommand;
	private CorruptCommand corruptCommand;

	private RepositoryTool tool;

	@Before
	public void setup() throws IOException
	{
		FileUtils.deleteDirectory(rootDir.toFile());
		Files.createDirectories(rootDir);

		initCommand = new InitCommand();
		diffCommand = new DiffCommand();
		commitCommand = new CommitCommand();
		corruptCommand = new CorruptCommand();

		tool = new RepositoryTool(rootDir);
	}

	@Test(expected = BadFimUsageException.class)
	public void fileContentHashingIsMandatory() throws Exception
	{
		Context context = tool.createContext(dontHash, false);
		corruptCommand.execute(context);
	}

	@Test
	public void weCanDetectHardwareCorruption() throws Exception
	{
		Context context = tool.createContext(hashAll, true);

		tool.createASetOfFiles(5);

		State state = (State) initCommand.execute(context);

		assertThat(state.getModificationCounts().getAdded()).isEqualTo(5);

		doSomeModifications();

		CompareResult compareResult = (CompareResult) diffCommand.execute(context);
		assertThat(compareResult.modifiedCount()).isEqualTo(3);

		compareResult = (CompareResult) corruptCommand.execute(context);
		List<Difference> corrupted = compareResult.getCorrupted();
		assertThat(corrupted.size()).isEqualTo(1);
		FileState fileState = corrupted.get(0).getFileState();
		assertThat(fileState.getFileName()).isEqualTo("file04");
	}

	private void doSomeModifications() throws IOException
	{
		tool.touchCreationTime("file01");

		tool.touchLastModified("file02");

		tool.setFileContent("file03", "file03 new content");

		simulateHardwareCorruption("file04");

		// Do nothing on file05
	}

	private void simulateHardwareCorruption(String fileName) throws IOException
	{
		Path file = rootDir.resolve(fileName);
		// Keep original timestamps
		BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);

		// Modify the file content
		byte[] bytes = Files.readAllBytes(file);
		bytes[bytes.length / 2] = 12;

		Files.delete(file);
		Files.write(file, bytes, CREATE);

		// Restore the original timestamps
		Files.getFileAttributeView(file, BasicFileAttributeView.class).setTimes(attributes.lastModifiedTime(), attributes.lastAccessTime(), attributes.creationTime());
	}
}
