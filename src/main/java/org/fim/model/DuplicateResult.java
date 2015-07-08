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
package org.fim.model;

import java.util.ArrayList;
import java.util.List;

public class DuplicateResult
{
	private final Parameters parameters;
	private final List<DuplicateSet> duplicateSets;
	private long duplicatedFilesCount;

	public DuplicateResult(Parameters parameters)
	{
		this.parameters = parameters;
		this.duplicateSets = new ArrayList<>();
		this.duplicatedFilesCount = 0;
	}

	public void addDuplicatedFiles(List<FileState> duplicatedFiles)
	{
		if (duplicatedFiles.size() > 1)
		{
			duplicatedFilesCount += duplicatedFiles.size() - 1;

			DuplicateSet duplicateSet = new DuplicateSet(duplicatedFiles);
			duplicateSets.add(duplicateSet);
		}
	}

	public DuplicateResult displayDuplicates()
	{
		System.out.println(duplicatedFilesCount + " duplicated files\n");

		if (parameters.isVerbose())
		{
			for (DuplicateSet duplicateSet : duplicateSets)
			{
				System.out.println("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
				System.out.println("- Duplicate set #" + (duplicateSets.indexOf(duplicateSet) + 1));
				List<FileState> duplicatedFiles = duplicateSet.getDuplicatedFiles();
				for (FileState fileState : duplicatedFiles)
				{
					if (duplicatedFiles.indexOf(fileState) == 0)
					{
						System.out.printf("  %s duplicated %d times%n", fileState.getFileName(), duplicatedFiles.size() - 1);
					}
					else
					{
						System.out.printf("      %s%n", fileState.getFileName());
					}
				}
				System.out.println("");
			}
		}

		return this;
	}

	public long getDuplicatedFilesCount()
	{
		return duplicatedFilesCount;
	}

	public List<DuplicateSet> getDuplicateSets()
	{
		return duplicateSets;
	}
}
