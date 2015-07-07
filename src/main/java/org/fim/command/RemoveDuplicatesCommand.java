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

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.fim.Main;
import org.fim.internal.StateGenerator;
import org.fim.internal.StateManager;
import org.fim.model.CompareMode;
import org.fim.model.FileState;
import org.fim.model.FimOptions;
import org.fim.model.State;

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
		return "Remove duplicated files from local directory based on a master Fim repository";
	}

	@Override
	public void execute(FimOptions fimOptions) throws Exception
	{
		if (fimOptions.getFimRepositoryDirectory() == null)
		{
			System.out.println("The Fim repository directory must be provided");
			Main.printUsage();
			System.exit(-1);
		}

		StateGenerator generator = new StateGenerator(fimOptions.getThreadCount(), fimOptions.getCompareMode());

		fastCompareNotSupported(fimOptions.getCompareMode());

		File repository = new File(fimOptions.getFimRepositoryDirectory());
		if (!repository.exists())
		{
			System.out.printf("Directory %s does not exist%n", fimOptions.getFimRepositoryDirectory());
			System.exit(-1);
		}

		if (repository.getCanonicalPath().equals(fimOptions.getBaseDirectory().getCanonicalPath()))
		{
			System.out.printf("Cannot remove duplicates from the current directory%n");
			System.exit(-1);
		}

		File fimDir = new File(repository, StateGenerator.FIM_DIR);
		if (!fimDir.exists())
		{
			System.out.printf("Directory %s is not a Fim repository%n", fimOptions.getFimRepositoryDirectory());
			System.exit(-1);
		}

		System.out.println("Searching for duplicated files using the " + fimOptions.getFimRepositoryDirectory() + " directory as master");
		System.out.println("");

		File otherStateDir = new File(fimDir, "states");
		StateManager otherManager = new StateManager(otherStateDir, CompareMode.FULL);
		State otherState = otherManager.loadLastState();
		Map<String, FileState> otherHashes = new HashMap<>();
		for (FileState otherFileState : otherState.getFileStates())
		{
			otherHashes.put(otherFileState.getHash(), otherFileState);
		}

		State localState = generator.generateState(fimOptions.getMessage(), fimOptions.getBaseDirectory());
		for (FileState localFileState : localState.getFileStates())
		{
			FileState otherFileState = otherHashes.get(localFileState.getHash());
			if (otherFileState != null)
			{
				System.out.printf("%s is a duplicate of %s/%s%n", localFileState.getFileName(), fimOptions.getFimRepositoryDirectory(), otherFileState.getFileName());
				if (fimOptions.isAlwaysYes() || confirmCommand("remove it"))
				{
					System.out.printf("  %s removed%n", localFileState.getFileName());
					File localFile = new File(localFileState.getFileName());
					localFile.delete();
				}
			}
		}
	}
}
