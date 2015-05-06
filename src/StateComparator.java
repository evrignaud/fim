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
		List<FileState> diffState = new ArrayList<>();

		if (previousState != null)
		{
			String previousStateDate = dateFormat.format(new Date(previousState.timestamp));
			System.out.println("Comparing with previous state from " + previousStateDate);
			if (previousState.message.length() > 0)
			{
				System.out.println("With message: " + previousState.message);
			}
			System.out.println("");

			diffState.addAll(previousState.fileStates);
		}

		long addedCount = 0;
		boolean isModified = false;
		for (FileState fileState : currentState.fileStates)
		{
			if (!diffState.remove(fileState))
			{
				if (verbose)
				{
					System.out.println("Add " + fileState.fileName);
				}
				addedCount++;
				isModified = true;
			}
		}

		long deletedCount = 0;
		for (FileState fileState : diffState)
		{
			if (verbose)
			{
				System.out.println("Del " + fileState.fileName);
			}
			deletedCount++;
			isModified = true;
		}

		if (!isModified)
		{
			System.out.println("Nothing modified");
		}
		else
		{
			System.out.println("");
			System.out.println(addedCount + " added, " + deletedCount + " deleted");
		}
	}
}
