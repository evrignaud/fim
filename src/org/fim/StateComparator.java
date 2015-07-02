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
import java.util.Iterator;
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
	private List<Difference> copied;
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

		resetNewHash(previousFileStates);

		differences.addAll(previousFileStates);

		for (FileState fileState : currentState.getFileStates())
		{
			if (!differences.remove(fileState))
			{
				addedOrModified.add(fileState);
			}
		}

		added = new ArrayList<>();
		copied = new ArrayList<>();
		duplicated = new ArrayList<>();
		dateModified = new ArrayList<>();
		contentModified = new ArrayList<>();
		renamed = new ArrayList<>();
		deleted = new ArrayList<>();

		FileState previousFileState;
		List<FileState> samePreviousHash;

		Iterator<FileState> iterator = addedOrModified.iterator();
		while (iterator.hasNext())
		{
			FileState fileState = iterator.next();
			if ((previousFileState = findFileWithSameFileName(fileState, differences)) != null)
			{
				differences.remove(previousFileState);
				if (previousFileState.getHash().equals(fileState.getHash()) && previousFileState.getLastModified() != fileState.getLastModified())
				{
					dateModified.add(new Difference(previousFileState, fileState));
					iterator.remove();
				}
				else
				{
					contentModified.add(new Difference(previousFileState, fileState));
					iterator.remove();

					// File has been modified so set the new hash for accurate duplicate detection
					previousFileState.setNewHash(fileState.getHash());
				}
			}
		}

		iterator = addedOrModified.iterator();
		while (iterator.hasNext())
		{
			FileState fileState = iterator.next();
			if (compareMode != CompareMode.FAST && (samePreviousHash = findFilesWithSameHash(fileState, previousFileStates)).size() > 0)
			{
				FileState originalFileState = samePreviousHash.get(0);
				if (differences.contains(originalFileState))
				{
					renamed.add(new Difference(originalFileState, fileState));
					iterator.remove();
				}
				else
				{
					if (originalFileState.contentChanged())
					{
						copied.add(new Difference(originalFileState, fileState));
						iterator.remove();
					}
					else
					{
						duplicated.add(new Difference(originalFileState, fileState));
						iterator.remove();
					}
				}
				differences.remove(originalFileState);
			}
			else
			{
				added.add(new Difference(null, fileState));
				iterator.remove();
			}
		}

		if (addedOrModified.size() != 0)
		{
			throw new IllegalStateException("Comparison algorithm error");
		}

		for (FileState fileState : differences)
		{
			deleted.add(new Difference(null, fileState));
		}

		Collections.sort(added);
		Collections.sort(copied);
		Collections.sort(duplicated);
		Collections.sort(dateModified);
		Collections.sort(contentModified);
		Collections.sort(renamed);
		Collections.sort(deleted);

		return this;
	}

	private void resetNewHash(List<FileState> fileStates)
	{
		for(FileState fileState : fileStates)
		{
			fileState.resetNewHash();
		}
	}

	public void displayChanges(boolean verbose)
	{
		if (!verbose)
		{
			displayCounts();
			return;
		}

		String stateFormat = "%-17s ";

		for (Difference diff : added)
		{
			System.out.format(String.format(stateFormat + "%s%n", "Added:", diff.getFileState().getFileName()));
		}

		for (Difference diff : copied)
		{
			System.out.format(String.format(stateFormat + "%s \t(was %s)%n", "Copied:", diff.getFileState().getFileName(), diff.getPreviousFileState().getFileName()));
		}

		for (Difference diff : duplicated)
		{
			System.out.format(String.format(stateFormat + "%s = %s%n", "Duplicated:", diff.getFileState().getFileName(), diff.getPreviousFileState().getFileName()));
		}

		for (Difference diff : dateModified)
		{
			System.out.format(stateFormat + "%s \t%s -> %s%n", "Date modified:", diff.getFileState().getFileName(), formatDate(diff.getPreviousFileState()), formatDate(diff.getFileState()));
		}

		for (Difference diff : contentModified)
		{
			System.out.format(stateFormat + "%s \t%s -> %s%n", "Content modified:", diff.getFileState().getFileName(), formatDate(diff.getPreviousFileState()), formatDate(diff.getFileState()));
		}

		for (Difference diff : renamed)
		{
			System.out.format(String.format(stateFormat + "%s -> %s%n", "Renamed:", diff.getPreviousFileState().getFileName(), diff.getFileState().getFileName()));
		}

		for (Difference diff : deleted)
		{
			System.out.format(String.format(stateFormat + "%s%n", "Deleted:", diff.getFileState().getFileName()));
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

			if (!copied.isEmpty())
			{
				message += "" + copied.size() + " copied, ";
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
		return (added.size() + copied.size() + duplicated.size() + dateModified.size() + contentModified.size() + renamed.size() + deleted.size()) > 0;
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
			if (fileState.getHash().equals(search.getHash()))
			{
				sameHash.add(fileState);
			}
		}

		return sameHash;
	}

	public List<Difference> getAdded()
	{
		return added;
	}

	public List<Difference> getCopied()
	{
		return copied;
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
