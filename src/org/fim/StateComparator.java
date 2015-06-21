package org.fim;

import static org.fim.util.FormatUtil.formatDate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.fim.model.Difference;
import org.fim.model.FileState;
import org.fim.model.State;

/**
 * Created by evrignaud on 05/05/15.
 */
public class StateComparator
{
	private final boolean verbose;
	private final CompareMode compareMode;
	private List<FileState> added;
	private List<Difference> duplicated;
	private List<Difference> dateModified;
	private List<Difference> contentModified;
	private List<Difference> moved;
	private List<FileState> deleted;

	public StateComparator(boolean verbose, CompareMode compareMode)
	{
		this.verbose = verbose;
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
		moved = new ArrayList<>();
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
					moved.add(new Difference(originalState, fileState));
				}
				else
				{
					FileState originalState = previousFileStates.get(index);
					duplicated.add(new Difference(originalState, fileState));
				}
			}
			else
			{
				added.add(fileState);
			}
		}

		for (FileState fileState : differences)
		{
			deleted.add(fileState);
		}

		return this;
	}

	public void displayChanges()
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

		Collections.sort(moved);
		for (Difference diff : moved)
		{
			System.out.format(String.format(changeTypeFormat + "%s -> %s%n", "Moved:", diff.getOriginalState().getFileName(), diff.getFileState().getFileName()));
		}

		Collections.sort(duplicated);
		for (Difference diff : duplicated)
		{
			System.out.format(String.format(changeTypeFormat + "%s = %s%n", "Duplicated:", diff.getFileState().getFileName(), diff.getOriginalState().getFileName()));
		}

		Collections.sort(added);
		for (FileState fileState : added)
		{
			System.out.format(String.format(changeTypeFormat + "%s%n", "Added:", fileState.getFileName()));
		}

		Collections.sort(deleted);
		for (FileState fileState : deleted)
		{
			System.out.format(String.format(changeTypeFormat + "%s%n", "Deleted:", fileState.getFileName()));
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

			if (!moved.isEmpty())
			{
				message += "" + moved.size() + " moved, ";
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
		return (added.size() + duplicated.size() + dateModified.size() + contentModified.size() + moved.size() + deleted.size()) > 0;
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

	public List<FileState> getAdded()
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

	public List<Difference> getMoved()
	{
		return moved;
	}

	public List<FileState> getDeleted()
	{
		return deleted;
	}
}
