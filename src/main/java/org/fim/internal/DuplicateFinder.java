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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.fim.model.DuplicateResult;
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.Parameters;
import org.fim.model.State;

public class DuplicateFinder
{
	private final Parameters parameters;
	private final List<FileState> duplicatedFiles;
	private final Comparator<FileState> fullHashComparator;

	public DuplicateFinder(Parameters parameters)
	{
		this.parameters = parameters;
		this.duplicatedFiles = new ArrayList<>();
		this.fullHashComparator = new FileState.HashComparator();
	}

	public DuplicateResult findDuplicates(State state)
	{
		DuplicateResult result = new DuplicateResult(parameters);

		List<FileState> fileStates = new ArrayList<>(state.getFileStates());
		Collections.sort(fileStates, fullHashComparator);

		FileHash previousHash = new FileHash(FileState.NO_HASH, FileState.NO_HASH, FileState.NO_HASH);
		for (FileState fileState : fileStates)
		{
			if (!previousHash.equals(fileState.getFileHash()))
			{
				result.addDuplicatedFiles(duplicatedFiles);
				duplicatedFiles.clear();
			}

			previousHash = fileState.getFileHash();
			duplicatedFiles.add(fileState);
		}
		result.addDuplicatedFiles(duplicatedFiles);

		return result;
	}
}
