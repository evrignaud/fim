import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by evrignaud on 05/05/15.
 */
public class StateComparator
{
	public List<FileState> added;
	public List<Difference> duplicated;
	public List<Difference> dateModified;
	public List<Difference> contentModified;
	public List<Difference> moved;
	public List<FileState> deleted;

	private final boolean verbose;
	private final boolean fastCompare;

	public StateComparator(boolean verbose, Boolean fastCompare)
	{
		this.verbose = verbose;
		this.fastCompare = fastCompare;
	}

	public StateComparator compare(State previousState, State currentState)
	{
		List<FileState> previousFileStates = new ArrayList<>();
		List<FileState> differences = new ArrayList<>();
		List<FileState> addedOrModified = new ArrayList<>();

		if (previousState != null)
		{
			System.out.println("Comparing with previous state from " + FormatUtil.formatDate(previousState.timestamp));
			if (previousState.message.length() > 0)
			{
				System.out.println("Message: " + previousState.message);
			}
			System.out.println("");

			previousFileStates.addAll(previousState.fileStates);
		}

		differences.addAll(previousFileStates);

		for (FileState fileState : currentState.fileStates)
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
				if (originalState.hash.equals(fileState.hash))
				{
					dateModified.add(new Difference(originalState, fileState));
				}
				else
				{
					contentModified.add(new Difference(originalState, fileState));
				}

				differences.remove(diffIndex);
			}
			else if (!fastCompare && (index = findSameHash(fileState, previousFileStates)) != -1)
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
			System.out.format(changeTypeFormat + "%s \t%s -> %s%n", "Date modified:", diff.fileState.fileName, FormatUtil.formatDate(diff.originalState), FormatUtil.formatDate(diff.fileState));
		}

		Collections.sort(contentModified);
		for (Difference diff : contentModified)
		{
			System.out.format(changeTypeFormat + "%s \t%s -> %s%n", "Content modified:", diff.fileState.fileName, FormatUtil.formatDate(diff.originalState), FormatUtil.formatDate(diff.fileState));
		}

		Collections.sort(moved);
		for (Difference diff : moved)
		{
			System.out.format(String.format(changeTypeFormat + "%s -> %s%n", "Moved:", diff.originalState.fileName, diff.fileState.fileName));
		}

		Collections.sort(duplicated);
		for (Difference diff : duplicated)
		{
			System.out.format(String.format(changeTypeFormat + "%s = %s%n", "Duplicated:", diff.fileState.fileName, diff.originalState.fileName));
		}

		Collections.sort(added);
		for (FileState fileState : added)
		{
			System.out.format(String.format(changeTypeFormat + "%s%n", "Added:", fileState.fileName));
		}

		Collections.sort(deleted);
		for (FileState fileState : deleted)
		{
			System.out.format(String.format(changeTypeFormat + "%s%n", "Deleted:", fileState.fileName));
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
			if (fileState.fileName.equals(toFind.fileName))
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
			if (fileState.hash.equals(toFind.hash))
			{
				return index;
			}
			index++;
		}

		return -1;
	}
}
