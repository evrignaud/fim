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

	public void compare(State previousState, State currentState, boolean verbose)
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

		int addedCount = 0;
		int duplicatedCount = 0;
		int dateModifiedCount = 0;
		int contentModifiedCount = 0;
		int deletedCount = 0;

		int index;
		for (FileState fileState : addedOrModified)
		{
			if ((index = findSameFileName(fileState, differences)) != -1)
			{
				FileState originalState = differences.get(index);
				if (originalState.hash.equals(fileState.hash))
				{
					dateModifiedCount++;
					if (verbose)
					{
						System.out.println("Date modified:\t" + fileState.fileName + " \t" + formatDate(originalState) + " -> " + formatDate(fileState));
					}
				}
				else
				{
					contentModifiedCount++;
					if (verbose)
					{
						System.out.println("Content modified:\t" + fileState.fileName);
					}
				}

				differences.remove(index);
			}
			else if (findSameHash(fileState, previousFileStates) != -1)
			{
				duplicatedCount++;
				if (verbose)
				{
					System.out.println("Duplicated:\t" + fileState.fileName);
				}
			}
			else
			{
				addedCount++;
				if (verbose)
				{
					System.out.println("Added:    \t" + fileState.fileName);
				}
			}
		}

		for (FileState fileState : differences)
		{
			deletedCount++;
			if (verbose)
			{
				System.out.println("Deleted:  \t" + fileState.fileName);
			}
		}

		if ((addedCount + duplicatedCount + dateModifiedCount + contentModifiedCount + deletedCount) > 0)
		{
			if (verbose)
			{
				System.out.println("");
			}
			System.out.println(addedCount + " added, " + duplicatedCount + " duplicated, " + dateModifiedCount + " date modified, " +
					contentModifiedCount + " content modified, " + deletedCount + " deleted");
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
