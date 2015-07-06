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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.fim.model.DuplicateResult;
import org.fim.model.FileState;
import org.fim.model.State;

public class DuplicateFinder
{
	private List<FileState> duplicatedFiles;

	private Comparator<FileState> hashComparator;

	public DuplicateFinder()
	{
		duplicatedFiles = new ArrayList<>();
		hashComparator = new HashComparator();
	}

	public DuplicateResult findDuplicates(State state)
	{
		DuplicateResult result = new DuplicateResult();

		List<FileState> fileStates = new ArrayList<>(state.getFileStates());
		Collections.sort(fileStates, hashComparator);

		String previousHash = "";
		for (FileState fileState : fileStates)
		{
			if (!previousHash.equals(fileState.getHash()))
			{
				result.addDuplicatedFiles(duplicatedFiles);
				duplicatedFiles.clear();
			}

			previousHash = fileState.getHash();
			duplicatedFiles.add(fileState);
		}
		result.addDuplicatedFiles(duplicatedFiles);

		return result;
	}

	private static class HashComparator implements Comparator<FileState>
	{
		@Override
		public int compare(FileState fs1, FileState fs2)
		{
			return fs1.getHash().compareTo(fs2.getHash());
		}
	}
}
