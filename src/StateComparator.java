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

	public void compare(State oldState, State newState)
	{
		String oldStateDate = dateFormat.format(new Date(oldState.timestamp));
		System.out.println("Comparing with previous state from " + oldStateDate);
		System.out.println("With message: " + oldState.message);
		System.out.println("");

		List<FileState> diffState = new ArrayList<>(oldState.fileStates);
		boolean isModified = false;
		for (FileState fileState : newState.fileStates)
		{
			if (!diffState.remove(fileState))
			{
				System.out.println("+A " + fileState.fileName);
				isModified = true;
			}
		}

		for (FileState fileState : diffState)
		{
			System.out.println("-D " + fileState.fileName);
			isModified = true;
		}

		if (!isModified)
		{
			System.out.println("Nothing modified");
		}
	}
}
