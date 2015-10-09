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
package org.fim;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.fim.command.CommitCommand;
import org.fim.command.DiffCommand;
import org.fim.command.DisplayIgnoredFilesCommand;
import org.fim.command.FindDuplicatesCommand;
import org.fim.command.InitCommand;
import org.fim.command.LogCommand;
import org.fim.command.RollbackCommand;
import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.model.DuplicateResult;
import org.fim.model.HashMode;
import org.fim.model.LogResult;
import org.fim.model.State;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class CompleteScenarioTest
{
	private static Path rootDir = Paths.get("target/" + CompleteScenarioTest.class.getSimpleName());

	private Context context;
	private Path dir01;

	private InitCommand initCommand;
	private DiffCommand diffCommand;
	private CommitCommand commitCommand;
	private FindDuplicatesCommand findDuplicatesCommand;
	private LogCommand logCommand;
	private DisplayIgnoredFilesCommand displayIgnoredFilesCommand;
	private RollbackCommand rollbackCommand;

	private int fileCount;

	@BeforeClass
	public static void setupOnce() throws NoSuchAlgorithmException, IOException
	{
		FileUtils.deleteDirectory(rootDir.toFile());
		Files.createDirectories(rootDir);
	}

	@Before
	public void setup()
	{
		context = new Context();
		context.setHashMode(HashMode.hashAll);
		context.setAlwaysYes(true);
		context.setCurrentDirectory(rootDir);
		context.setRepositoryRootDir(rootDir);

		dir01 = rootDir.resolve("dir01");

		initCommand = new InitCommand();
		diffCommand = new DiffCommand();
		commitCommand = new CommitCommand();
		findDuplicatesCommand = new FindDuplicatesCommand();
		logCommand = new LogCommand();
		displayIgnoredFilesCommand = new DisplayIgnoredFilesCommand();
		rollbackCommand = new RollbackCommand();

		fileCount = 0;
	}

	@Test
	public void fullScenario() throws Exception
	{
		createASetOfFiles(10);

		State state = (State) initCommand.execute(context);

		assertThat(state.getModificationCounts().getAdded()).isEqualTo(10);
		assertThat(state.getFileStates().size()).isEqualTo(10);
		Path dotFim = rootDir.resolve(".fim");
		assertThat(Files.exists(dotFim)).isTrue();
		assertThat(Files.exists(dotFim.resolve("settings.json"))).isTrue();
		assertThat(Files.exists(dotFim.resolve("states/state_1.json.gz"))).isTrue();

		doSomeModifications();

		CompareResult compareResult = (CompareResult) diffCommand.execute(context);
		assertThat(compareResult.modifiedCount()).isEqualTo(10);
		assertThat(compareResult.getModificationCounts().getRenamed()).isEqualTo(1);
		assertThat(compareResult.getModificationCounts().getDeleted()).isEqualTo(1);

		DuplicateResult duplicateResult = (DuplicateResult) findDuplicatesCommand.execute(context);
		assertThat(duplicateResult.getDuplicateSets().size()).isEqualTo(2);

		addIgnoredFiles();

		runTheCommandsFrom_dir01();

		compareResult = (CompareResult) commitCommand.execute(context);
		assertThat(compareResult.modifiedCount()).isEqualTo(14);
		assertThat(compareResult.getModificationCounts().getRenamed()).isEqualTo(0);
		assertThat(compareResult.getModificationCounts().getDeleted()).isEqualTo(3);

		compareResult = (CompareResult) diffCommand.execute(context);
		assertThat(compareResult.modifiedCount()).isEqualTo(0);

		LogResult logResult = (LogResult) logCommand.execute(context);
		assertThat(logResult.getLogEntries().size()).isEqualTo(3);

		Set<String> ignoredFiles = (Set<String>) displayIgnoredFilesCommand.execute(context);
		assertThat(ignoredFiles.size()).isEqualTo(6);

		rollbackCommand.execute(context);

		compareResult = (CompareResult) diffCommand.execute(context);
		assertThat(compareResult.modifiedCount()).isEqualTo(14);

		logResult = (LogResult) logCommand.execute(context);
		assertThat(logResult.getLogEntries().size()).isEqualTo(2);

		ignoredFiles = (Set<String>) displayIgnoredFilesCommand.execute(context);
		assertThat(ignoredFiles.size()).isEqualTo(2);
	}

	private void createASetOfFiles(int fileCount) throws IOException
	{
		for (int index = 1; index <= fileCount; index++)
		{
			createFile("file" + String.format("%02d", index));
		}
	}

	private void doSomeModifications() throws IOException
	{
		Files.createDirectories(dir01);

		Files.move(rootDir.resolve("file01"), dir01.resolve("file01"));

		touch("file02");

		Files.copy(rootDir.resolve("file03"), rootDir.resolve("file03.dup1"));
		Files.copy(rootDir.resolve("file03"), rootDir.resolve("file03.dup2"));

		setFileContent("file04", "foo");

		Files.copy(rootDir.resolve("file05"), rootDir.resolve("file11"));
		setFileContent("file05", "bar");

		Files.delete(rootDir.resolve("file06"));

		Files.copy(rootDir.resolve("file07"), rootDir.resolve("file07.dup1"));

		setFileContent("file12", "New file 12");
	}

	private void addIgnoredFiles() throws Exception
	{
		createFile("ignored_type1");
		createFile("ignored_type2");

		createFile(dir01.resolve("ignored_type1"));
		createFile(dir01.resolve("ignored_type2"));

		createFile("media.mp3");
		createFile("media.mp4");

		createFile(dir01.resolve("media.mp3"));
		createFile(dir01.resolve("media.mp4"));

		CompareResult compareResult = (CompareResult) diffCommand.execute(context);
		assertThat(compareResult.modifiedCount()).isEqualTo(18);

		createFimIgnore(rootDir, "**/*.mp3\n" + "ignored_type1");

		compareResult = (CompareResult) diffCommand.execute(context);
		assertThat(compareResult.modifiedCount()).isEqualTo(16);
	}

	private void runTheCommandsFrom_dir01() throws Exception
	{
		Context dir01Context = context.clone();
		dir01Context.setCurrentDirectory(dir01);
		dir01Context.setInvokedFromSubDirectory(true);

		CompareResult compareResult = (CompareResult) diffCommand.execute(dir01Context);
		assertThat(compareResult.modifiedCount()).isEqualTo(5);

		createFimIgnore(dir01, "*.mp4\n" + "ignored_type2");

		compareResult = (CompareResult) diffCommand.execute(dir01Context);
		assertThat(compareResult.modifiedCount()).isEqualTo(4);

		DuplicateResult duplicateResult = (DuplicateResult) findDuplicatesCommand.execute(dir01Context);
		assertThat(duplicateResult.getDuplicateSets().size()).isEqualTo(0);

		compareResult = (CompareResult) commitCommand.execute(dir01Context);
		assertThat(compareResult.modifiedCount()).isEqualTo(4);

		compareResult = (CompareResult) diffCommand.execute(dir01Context);
		assertThat(compareResult.modifiedCount()).isEqualTo(0);
	}

	private void touch(String fileName) throws IOException
	{
		Path file = rootDir.resolve(fileName);
		long timeStamp = Math.max(System.currentTimeMillis(), Files.getLastModifiedTime(file).toMillis());
		timeStamp += 1000;
		Files.setLastModifiedTime(file, FileTime.fromMillis(timeStamp));
	}

	private void createFimIgnore(Path directory, String content) throws IOException
	{
		Path file = directory.resolve(".fimignore");
		setFileContent(file, content);
	}

	private void createFile(String fileName) throws IOException
	{
		Path file = rootDir.resolve(fileName);
		createFile(file);
	}

	private void createFile(Path file) throws IOException
	{
		setFileContent(file, "File content " + String.format("%02d", fileCount));
		fileCount++;
	}

	private void setFileContent(String fileName, String content) throws IOException
	{
		Path file = rootDir.resolve(fileName);
		setFileContent(file, content);
	}

	private void setFileContent(Path file, String content) throws IOException
	{
		if (Files.exists(file))
		{
			Files.delete(file);
		}
		Files.write(file, content.getBytes(), CREATE, APPEND);
	}
}
