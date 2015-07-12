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
import java.util.Iterator;
import java.util.List;

import org.fim.model.CompareResult;
import org.fim.model.Difference;
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.HashMode;
import org.fim.model.Parameters;
import org.fim.model.State;

public class StateComparator
{
	private final Parameters parameters;

	public StateComparator(Parameters parameters)
	{
		this.parameters = parameters;
	}

	public CompareResult compare(State lastState, State currentState)
	{
		CompareResult result = new CompareResult(parameters, lastState);

		List<FileState> previousFileStates = new ArrayList<>();
		List<FileState> notFoundInCurrentFileState = new ArrayList<>();
		List<FileState> addedOrModified = new ArrayList<>();

		if (lastState != null)
		{
			logDebug("---------------------------------------------------------------------",
					"lastState", lastState.getFileStates(), "currentState", currentState.getFileStates());

			previousFileStates.addAll(lastState.getFileStates());
		}
		else
		{
			logDebug("---------------------------------------------------------------------",
					"currentState", currentState.getFileStates());
		}

		resetNewHash(previousFileStates);

		notFoundInCurrentFileState.addAll(previousFileStates);

		for (FileState fileState : currentState.getFileStates())
		{
			if (!notFoundInCurrentFileState.remove(fileState))
			{
				addedOrModified.add(fileState);
			}
		}

		logDebug("Built addedOrModified", "notFoundInCurrentFileState", notFoundInCurrentFileState, "addedOrModified", addedOrModified);

		FileState previousFileState;
		List<FileState> samePreviousHash;

		Iterator<FileState> iterator = addedOrModified.iterator();
		while (iterator.hasNext())
		{
			FileState fileState = iterator.next();
			if ((previousFileState = findFileWithSameFileName(fileState, notFoundInCurrentFileState)) != null)
			{
				notFoundInCurrentFileState.remove(previousFileState);
				if (previousFileState.getFileHash().equals(fileState.getFileHash()) && previousFileState.getLastModified() != fileState.getLastModified())
				{
					result.getDateModified().add(new Difference(previousFileState, fileState));
					iterator.remove();
				}
				else
				{
					result.getContentModified().add(new Difference(previousFileState, fileState));
					iterator.remove();

					// File has been modified so set the new hash for accurate duplicate detection
					previousFileState.setNewFileHash(new FileHash(fileState.getFileHash()));
				}
			}
		}

		logDebug("Search done using sameFileNames", "notFoundInCurrentFileState", notFoundInCurrentFileState, "addedOrModified", addedOrModified);

		iterator = addedOrModified.iterator();
		while (iterator.hasNext())
		{
			FileState fileState = iterator.next();
			if (parameters.getHashMode() != HashMode.DONT_HASH_FILES &&
					(samePreviousHash = findFilesWithSameHash(fileState, previousFileStates)).size() > 0)
			{
				FileState originalFileState = samePreviousHash.get(0);
				if (notFoundInCurrentFileState.contains(originalFileState))
				{
					result.getRenamed().add(new Difference(originalFileState, fileState));
					iterator.remove();
				}
				else
				{
					if (contentChanged(originalFileState))
					{
						result.getCopied().add(new Difference(originalFileState, fileState));
						iterator.remove();
					}
					else
					{
						result.getDuplicated().add(new Difference(originalFileState, fileState));
						iterator.remove();
					}
				}
				notFoundInCurrentFileState.remove(originalFileState);
			}
			else
			{
				result.getAdded().add(new Difference(null, fileState));
				iterator.remove();
			}
		}

		if (addedOrModified.size() != 0)
		{
			throw new IllegalStateException("Comparison algorithm error");
		}

		for (FileState fileState : notFoundInCurrentFileState)
		{
			result.getDeleted().add(new Difference(null, fileState));
		}

		result.sortResults();

		return result;
	}

	private boolean contentChanged(FileState fileState)
	{
		return !fileState.getFileHash().equals(fileState.getNewFileHash());
	}

	private void logDebug(String message, String desc, List<FileState> fileStates)
	{
		logDebug("\n-- " + message);
		logDebug(fileStatesToString(desc, fileStates));
	}

	private void logDebug(String message, String desc_1, List<FileState> fileStates_1, String desc_2, List<FileState> fileStates_2)
	{
		logDebug("\n-- " + message);
		logDebug(fileStatesToString(desc_1, fileStates_1));
		logDebug(fileStatesToString(desc_2, fileStates_2));
	}

	private void logDebug(String message)
	{
		// System.out.println(message);
	}

	private String fileStatesToString(String message, List<FileState> fileStates)
	{
		StringBuilder builder = new StringBuilder();
		builder.append("  ").append(message).append(":\n");
		for (FileState fileState : fileStates)
		{
			builder.append("      ").append(fileState).append("\n");
		}
		return builder.toString();
	}

	private void resetNewHash(List<FileState> fileStates)
	{
		for (FileState fileState : fileStates)
		{
			fileState.resetNewHash();
		}
	}

	private FileState findFileWithSameFileName(FileState search, List<FileState> fileStates)
	{
		int index = 0;
		for (FileState fileState : fileStates)
		{
			if (fileState.getFileName().equals(search.getFileName()))
			{
				return fileStates.get(index);
			}
			index++;
		}

		return null;
	}

	private List<FileState> findFilesWithSameHash(FileState search, List<FileState> fileStates)
	{
		List<FileState> sameHash = new ArrayList<>();
		for (FileState fileState : fileStates)
		{
			if (fileState.getFileHash().equals(search.getFileHash()))
			{
				sameHash.add(fileState);
			}
		}

		return sameHash;
	}
}
