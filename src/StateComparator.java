import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by evrignaud on 05/05/15.
 */
public class StateComparator
{
	public static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

	private boolean verbose;

	private int addedCount;
	private int duplicatedCount;
	private int dateModifiedCount;
	private int contentModifiedCount;
	private int moveCount;
	private int deletedCount;

	public StateComparator(boolean verbose)
	{
		this.verbose = verbose;
	}

	public void compare(State previousState, State currentState)
	{
		List<FileState> previousFileStates = new ArrayList<>();
		List<FileState> differences = new ArrayList<>();
		List<FileState> addedOrModified = new ArrayList<>();

		if (previousState != null)
		{
			String previousStateDate = formatDate(previousState.timestamp);
			System.out.println("Comparing with previous state from " + previousStateDate);
			if (previousState.message.length() > 0)
			{
				System.out.println("With message: " + previousState.message);
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

		addedCount = 0;
		duplicatedCount = 0;
		dateModifiedCount = 0;
		contentModifiedCount = 0;
		moveCount = 0;
		deletedCount = 0;

		int index;
		int diffIndex;
		for (FileState fileState : addedOrModified)
		{
			if ((diffIndex = findSameFileName(fileState, differences)) != -1)
			{
				FileState originalState = differences.get(diffIndex);
				if (originalState.hash.equals(fileState.hash))
				{
					dateModifiedCount++;
					verbosePrint(String.format("%-18s%s \t%s -> %s", "Date modified:", fileState.fileName, formatDate(originalState), formatDate(fileState)));
				}
				else
				{
					contentModifiedCount++;
					verbosePrint(String.format("%-18s%s", "Content modified:", fileState.fileName));
				}

				differences.remove(diffIndex);
			}
			else if ((index = findSameHash(fileState, previousFileStates)) != -1)
			{
				if ((diffIndex = findSameHash(fileState, differences)) != -1)
				{
					FileState originalState = differences.get(diffIndex);

					moveCount++;
					verbosePrint(String.format("%-18s%s -> %s", "Moved:", originalState.fileName, fileState.fileName));

					differences.remove(diffIndex);
				}
				else
				{
					FileState originalState = previousFileStates.get(index);
					duplicatedCount++;
					verbosePrint(String.format("%-18s%s = %s", "Duplicated:", fileState.fileName, originalState.fileName));
				}
			}
			else
			{
				addedCount++;
				verbosePrint(String.format("%-18s%s", "Added:", fileState.fileName));
			}
		}

		for (FileState fileState : differences)
		{
			deletedCount++;
			verbosePrint(String.format("%-18s%s", "Deleted:", fileState.fileName));
		}

		displayCounts();
	}

	private void displayCounts()
	{
		if ((addedCount + duplicatedCount + dateModifiedCount + contentModifiedCount + moveCount + deletedCount) > 0)
		{
			verbosePrint("");

			String message = "";
			if (addedCount > 0)
			{
				message += "" + addedCount + " added, ";
			}

			if (duplicatedCount > 0)
			{
				message += "" + duplicatedCount + " duplicated, ";
			}

			if (dateModifiedCount > 0)
			{
				message += "" + dateModifiedCount + " date modified, ";
			}

			if (contentModifiedCount > 0)
			{
				message += "" + contentModifiedCount + " content modified, ";
			}

			if (moveCount > 0)
			{
				message += "" + moveCount + " moved, ";
			}

			if (deletedCount > 0)
			{
				message += "" + deletedCount + " deleted, ";
			}

			message = message.replaceAll(", $", "");
			System.out.println(message);
		}
		else
		{
			System.out.println("Nothing modified");
		}
	}

	private String formatDate(FileState fileState)
	{
		return dateFormat.format(new Date(fileState.lastModified));
	}

	private String formatDate(long timestamp)
	{
		return dateFormat.format(new Date(timestamp));
	}

	private void verbosePrint(String message)
	{
		if (verbose)
		{
			System.out.println(message);
		}
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
