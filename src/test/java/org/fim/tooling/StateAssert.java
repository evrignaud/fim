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
package org.fim.tooling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.fim.model.CompareResult;
import org.fim.model.Difference;
import org.fim.model.FileState;

public class StateAssert
{
	protected BuildableParameters defaultParameters()
	{
		return new BuildableParameters();
	}

	protected void assertNothingModified(CompareResult result)
	{
		assertThat(result.somethingModified()).isFalse();
	}

	protected void assertOnlyFilesAdded(CompareResult result, String... fileNames)
	{
		assertGotOnlyModifications(result, Modification.ADDED);
		assertFilesModified(result, Modification.ADDED, fileNames);
	}

	protected void assertOnlyFileCopied(CompareResult result, FileNameDiff... fileNameDiffs)
	{
		assertGotOnlyModifications(result, Modification.COPIED);
		assertFilesModified(result, Modification.COPIED, fileNameDiffs);
	}

	protected void assertOnlyFileDuplicated(CompareResult result, FileNameDiff... fileNameDiffs)
	{
		assertGotOnlyModifications(result, Modification.DUPLICATED);
		assertFilesModified(result, Modification.DUPLICATED, fileNameDiffs);
	}

	protected void assertOnlyDatesModified(CompareResult result, String... fileNames)
	{
		assertGotOnlyModifications(result, Modification.DATE_MODIFIED);
		assertFilesModified(result, Modification.DATE_MODIFIED, fileNames);
	}

	protected void assertOnlyContentModified(CompareResult result, String... fileNames)
	{
		assertGotOnlyModifications(result, Modification.CONTENT_MODIFIED);
		assertFilesModified(result, Modification.CONTENT_MODIFIED, fileNames);
	}

	protected void assertOnlyFileRenamed(CompareResult result, FileNameDiff... fileNameDiffs)
	{
		assertGotOnlyModifications(result, Modification.RENAMED);
		assertFilesModified(result, Modification.RENAMED, fileNameDiffs);
	}

	protected void assertOnlyFileDeleted(CompareResult result, String... fileNames)
	{
		assertGotOnlyModifications(result, Modification.DELETED);
		assertFilesModified(result, Modification.DELETED, fileNames);
	}

	protected void assertGotOnlyModifications(CompareResult result, Modification... modifications)
	{
		List<Modification> modificationsList = Arrays.asList(modifications);

		for (Modification modification : Modification.values())
		{
			List<Difference> differences = getDifferences(result, modification);
			if (modificationsList.contains(modification) == false)
			{
				assertThat(differences.isEmpty()).isTrue();
			}
			else
			{
				assertThat(differences.isEmpty()).isFalse();
			}
		}
	}

	protected void assertFilesModified(CompareResult result, Modification modification, String... fileNames)
	{
		List<String> fileNamesList = Arrays.asList(fileNames);

		List<Difference> differences = getDifferences(result, modification);
		assertThat(fileNamesList.size()).isEqualTo(differences.size());

		for (Difference difference : differences)
		{
			assertThat(fileNamesList.contains(difference.getFileState().getFileName())).isTrue();
		}
	}

	protected void assertFilesModified(CompareResult result, Modification modification, FileNameDiff... fileNameDiffs)
	{
		List<FileNameDiff> fileNameDiffsList = Arrays.asList(fileNameDiffs);

		List<Difference> differences = getDifferences(result, modification);
		assertThat(fileNameDiffsList.size()).isEqualTo(differences.size());

		for (Difference difference : differences)
		{
			assertThat(fileNameDiffsList.contains(new FileNameDiff(difference))).isTrue();
		}
	}

	private List<Difference> getDifferences(CompareResult result, Modification modification)
	{
		switch (modification)
		{
			case ADDED:
				return result.getAdded();

			case COPIED:
				return result.getCopied();

			case DUPLICATED:
				return result.getDuplicated();

			case DATE_MODIFIED:
				return result.getDateModified();

			case CONTENT_MODIFIED:
				return result.getContentModified();

			case RENAMED:
				return result.getRenamed();

			case DELETED:
				return result.getDeleted();
		}

		throw new IllegalArgumentException("Invalid Modification " + modification);
	}

	protected List<String> toFileNames(List<FileState> fileStates)
	{
		List<String> fileNames = fileStates.stream().map(FileState::getFileName).collect(Collectors.toList());
		return fileNames;
	}
}
