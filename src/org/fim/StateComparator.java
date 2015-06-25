/*
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.fim;

import static org.fim.util.FormatUtil.formatDate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.fim.model.Difference;
import org.fim.model.FileState;
import org.fim.model.State;

/**
 * @author evrignaud
 */
public class StateComparator
{
	private final CompareMode compareMode;

	private List<Difference> added;
	private List<Difference> duplicated;
	private List<Difference> dateModified;
	private List<Difference> contentModified;
	private List<Difference> renamed;
	private List<Difference> deleted;

	public StateComparator(CompareMode compareMode)
	{
		this.compareMode = compareMode;
	}

	public StateComparator compare(State previousState, State currentState)
	{
		List<FileState> previousFileStates = new ArrayList<>();
		List<FileState> differences = new ArrayList<>();
		List<FileState> addedOrModified = new ArrayList<>();

		if (previousState != null)
		{
			System.out.println("Comparing with previous state from " + formatDate(previousState.getTimestamp()));
			if (previousState.getMessage().length() > 0)
			{
				System.out.println("Message: " + previousState.getMessage());
			}
			System.out.println("");

			previousFileStates.addAll(previousState.getFileStates());
		}

		differences.addAll(previousFileStates);

		for (FileState fileState : currentState.getFileStates())
		{
			if (!differences.remove(fileState))
			{
				addedOrModified.add(fileState);
			}
		}

		added = new ArrayList<>();
		duplicated = new ArrayList<>();
		dateModified = new ArrayList<>();
		contentModified = new ArrayList<>();
		renamed = new ArrayList<>();
		deleted = new ArrayList<>();

		int index;
		int diffIndex;
		for (FileState fileState : addedOrModified)
		{
			if ((diffIndex = findSameFileName(fileState, differences)) != -1)
			{
				FileState originalState = differences.get(diffIndex);
				if (originalState.getHash().equals(fileState.getHash()))
				{
					dateModified.add(new Difference(originalState, fileState));
				}
				else
				{
					contentModified.add(new Difference(originalState, fileState));
				}

				differences.remove(diffIndex);
			}
			else if (compareMode != CompareMode.FAST && (index = findSameHash(fileState, previousFileStates)) != -1)
			{
				if ((diffIndex = findSameHash(fileState, differences)) != -1)
				{
					FileState originalState = differences.remove(diffIndex);
					renamed.add(new Difference(originalState, fileState));
				}
				else
				{
					FileState originalState = previousFileStates.get(index);
					duplicated.add(new Difference(originalState, fileState));
				}
			}
			else
			{
				added.add(new Difference(null, fileState));
			}
		}

		for (FileState fileState : differences)
		{
			deleted.add(new Difference(null, fileState));
		}

		return this;
	}

	public void displayChanges(boolean verbose)
	{
		if (!verbose)
		{
			displayCounts();
			return;
		}

		String changeTypeFormat = "%-17s ";

		Collections.sort(dateModified);
		for (Difference diff : dateModified)
		{
			System.out.format(changeTypeFormat + "%s \t%s -> %s%n", "Date modified:", diff.getFileState().getFileName(), formatDate(diff.getOriginalState()), formatDate(diff.getFileState()));
		}

		Collections.sort(contentModified);
		for (Difference diff : contentModified)
		{
			System.out.format(changeTypeFormat + "%s \t%s -> %s%n", "Content modified:", diff.getFileState().getFileName(), formatDate(diff.getOriginalState()), formatDate(diff.getFileState()));
		}

		Collections.sort(renamed);
		for (Difference diff : renamed)
		{
			System.out.format(String.format(changeTypeFormat + "%s -> %s%n", "Renamed:", diff.getOriginalState().getFileName(), diff.getFileState().getFileName()));
		}

		Collections.sort(duplicated);
		for (Difference diff : duplicated)
		{
			System.out.format(String.format(changeTypeFormat + "%s = %s%n", "Duplicated:", diff.getFileState().getFileName(), diff.getOriginalState().getFileName()));
		}

		Collections.sort(added);
		for (Difference diff : added)
		{
			System.out.format(String.format(changeTypeFormat + "%s%n", "Added:", diff.getFileState().getFileName()));
		}

		Collections.sort(deleted);
		for (Difference diff : deleted)
		{
			System.out.format(String.format(changeTypeFormat + "%s%n", "Deleted:", diff.getFileState().getFileName()));
		}

		if (somethingModified())
		{
			System.out.println("");
		}

		displayCounts();
	}

	public void displayCounts()
	{
		if (somethingModified())
		{
			String message = "";
			if (!added.isEmpty())
			{
				message += "" + added.size() + " added, ";
			}

			if (!duplicated.isEmpty())
			{
				message += "" + duplicated.size() + " duplicated, ";
			}

			if (!dateModified.isEmpty())
			{
				message += "" + dateModified.size() + " date modified, ";
			}

			if (!contentModified.isEmpty())
			{
				message += "" + contentModified.size() + " content modified, ";
			}

			if (!renamed.isEmpty())
			{
				message += "" + renamed.size() + " renamed, ";
			}

			if (!deleted.isEmpty())
			{
				message += "" + deleted.size() + " deleted, ";
			}

			message = message.replaceAll(", $", "");
			System.out.println(message);
		}
		else
		{
			System.out.println("Nothing modified");
		}
	}

	public boolean somethingModified()
	{
		return (added.size() + duplicated.size() + dateModified.size() + contentModified.size() + renamed.size() + deleted.size()) > 0;
	}

	private int findSameFileName(FileState toFind, List<FileState> differences)
	{
		int index = 0;
		for (FileState fileState : differences)
		{
			if (fileState.getFileName().equals(toFind.getFileName()))
			{
				return index;
			}
			index++;
		}

		return -1;
	}

	private int findSameHash(FileState toFind, List<FileState> differences)
	{
		int index = 0;
		for (FileState fileState : differences)
		{
			if (fileState.getHash().equals(toFind.getHash()))
			{
				return index;
			}
			index++;
		}

		return -1;
	}

	public List<Difference> getAdded()
	{
		return added;
	}

	public List<Difference> getDuplicated()
	{
		return duplicated;
	}

	public List<Difference> getDateModified()
	{
		return dateModified;
	}

	public List<Difference> getContentModified()
	{
		return contentModified;
	}

	public List<Difference> getRenamed()
	{
		return renamed;
	}

	public List<Difference> getDeleted()
	{
		return deleted;
	}
}
