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

import org.fim.model.Constants;
import org.fim.model.Context;
import org.fim.model.DuplicateResult;
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.State;

public class DuplicateFinder
{
	private final Context context;
	private final Comparator<FileState> hashComparator;

	public DuplicateFinder(Context context)
	{
		this.context = context;
		this.hashComparator = new FileState.HashComparator();
	}

	public DuplicateResult findDuplicates(State state)
	{
		DuplicateResult result = new DuplicateResult(context);

		List<FileState> fileStates = new ArrayList<>(state.getFileStates());
		Collections.sort(fileStates, hashComparator);

		List<FileState> duplicatedFiles = new ArrayList<>();
		FileHash previousFileHash = new FileHash(Constants.NO_HASH, Constants.NO_HASH, Constants.NO_HASH);
		for (FileState fileState : fileStates)
		{
			if (!previousFileHash.equals(fileState.getFileHash()))
			{
				result.addDuplicatedFiles(duplicatedFiles);
				duplicatedFiles.clear();
			}

			previousFileHash = fileState.getFileHash();
			duplicatedFiles.add(fileState);
		}
		result.addDuplicatedFiles(duplicatedFiles);

		return result;
	}
}
