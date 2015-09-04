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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.fim.Main;
import org.fim.internal.StateGenerator;
import org.fim.internal.StateManager;
import org.fim.model.Context;
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.HashMode;
import org.fim.model.State;
import org.fim.util.Console;
import org.fim.util.Logger;

public class RemoveDuplicatesCommand extends AbstractCommand
{
	@Override
	public String getCmdName()
	{
		return "remove-duplicates";
	}

	@Override
	public String getShortCmdName()
	{
		return "rdup";
	}

	@Override
	public String getDescription()
	{
		return "Remove duplicated files from local directory based on a remote master Fim repository";
	}

	@Override
	public FimReposConstraint getFimReposConstraint()
	{
		return FimReposConstraint.DONT_CARE;
	}

	@Override
	public void execute(Context context) throws Exception
	{
		if (context.getMasterFimRepositoryDir() == null)
		{
			Logger.error("The master Fim directory must be provided");
			Main.printUsage();
			System.exit(-1);
		}

		checkHashMode(context, Option.ALLOW_COMPATIBLE);

		fileContentHashingMandatory(context);

		if (context.getHashMode() == HashMode.hashSmallBlock)
		{
			System.out.println("You are going to detect duplicates and remove them based only on the hash of the second 4 KB block of the files.");
			if (!confirmAction(context, "continue"))
			{
				System.exit(0);
			}
		}

		if (context.getHashMode() == HashMode.hashMediumBlock)
		{
			System.out.println("You are going to detect duplicates and remove them based only on the hash of the second 1 MB block of the files.");
			if (!confirmAction(context, "continue"))
			{
				System.exit(0);
			}
		}

		Path masterFimRepository = Paths.get(context.getMasterFimRepositoryDir());
		if (!Files.exists(masterFimRepository))
		{
			Logger.error(String.format("Directory %s does not exist", context.getMasterFimRepositoryDir()));
			System.exit(-1);
		}

		Path normalizedMasterFimRepository = masterFimRepository.toAbsolutePath().normalize();
		Path normalizedCurrentDir = CURRENT_DIRECTORY.toAbsolutePath().normalize();

		if (normalizedMasterFimRepository.equals(normalizedCurrentDir))
		{
			Logger.error("Cannot remove duplicates into the master directory");
			System.exit(-1);
		}

		if (normalizedCurrentDir.startsWith(normalizedMasterFimRepository))
		{
			Logger.error("Cannot remove duplicates into a sub-directory of the master directory");
			System.exit(-1);
		}

		Path masterDotFimDir = masterFimRepository.resolve(Context.DOT_FIM_DIR);
		if (!Files.exists(masterDotFimDir))
		{
			Logger.error(String.format("Directory %s is not a Fim repository", context.getMasterFimRepositoryDir()));
			System.exit(-1);
		}
		context.setRepositoryRootDir(masterFimRepository);

		Logger.info(String.format("Searching for duplicated files using the %s directory as master", context.getMasterFimRepositoryDir()));
		Console.newLine();

		State masterState = new StateManager(context).loadLastState();
		Map<FileHash, FileState> masterFilesHash = buildFileHashMap(masterState);

		long totalFilesRemoved = 0;
		State localState = new StateGenerator(context).generateState("", CURRENT_DIRECTORY, CURRENT_DIRECTORY);
		for (FileState localFileState : localState.getFileStates())
		{
			FileState masterFileState = masterFilesHash.get(localFileState.getFileHash());
			if (masterFileState != null)
			{
				System.out.printf("'%s' is a duplicate of '%s/%s'%n", localFileState.getFileName(),
						context.getMasterFimRepositoryDir(), masterFileState.getFileName());
				if (confirmAction(context, "remove it"))
				{
					Path localFile = Paths.get(localFileState.getFileName());
					try
					{
						Files.delete(localFile);
						System.out.printf("  '%s' removed%n", localFileState.getFileName());
						totalFilesRemoved++;
					}
					catch (IOException ex)
					{
						Logger.error("Error deleting file", ex);
					}
				}
			}
		}

		Console.newLine();
		Logger.info(String.format("Removed %d duplicated files", totalFilesRemoved));
	}

	private Map<FileHash, FileState> buildFileHashMap(State state)
	{
		Map<FileHash, FileState> filesHashMap = new HashMap<>();
		for (FileState fileState : state.getFileStates())
		{
			filesHashMap.put(fileState.getFileHash(), fileState);
		}
		return filesHashMap;
	}
}
