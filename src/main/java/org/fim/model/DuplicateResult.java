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

import org.apache.commons.io.FileUtils;

public class DuplicateResult
{
	private final Parameters parameters;
	private final List<DuplicateSet> duplicateSets;
	private long duplicatedFilesCount;
	private long wastedSpace;

	public DuplicateResult(Parameters parameters)
	{
		this.parameters = parameters;
		this.duplicateSets = new ArrayList<>();
		this.duplicatedFilesCount = 0;
		this.wastedSpace = 0;
	}

	public void addDuplicatedFiles(List<FileState> duplicatedFiles)
	{
		if (duplicatedFiles.size() > 1)
		{
			duplicatedFilesCount += duplicatedFiles.size() - 1;

			duplicatedFiles.stream()
					.filter(fileState -> duplicatedFiles.indexOf(fileState) > 0)
					.forEach(fileState -> wastedSpace += fileState.getFileLength());

			DuplicateSet duplicateSet = new DuplicateSet(duplicatedFiles);
			duplicateSets.add(duplicateSet);
		}
	}

	public DuplicateResult displayDuplicates()
	{
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
						System.out.printf("      %s - %s%n", FileUtils.byteCountToDisplaySize(fileState.getFileLength()), fileState.getFileName());
					}
				}
				System.out.println("");
			}
		}
		System.out.printf("%d duplicated files spread in %d duplicate sets, %s of wasted space%n%n",
				duplicatedFilesCount, duplicateSets.size(), FileUtils.byteCountToDisplaySize(wastedSpace));

		return this;
	}

	public long getDuplicatedFilesCount()
	{
		return duplicatedFilesCount;
	}

	public long getWastedSpace()
	{
		return wastedSpace;
	}

	public List<DuplicateSet> getDuplicateSets()
	{
		return duplicateSets;
	}
}
