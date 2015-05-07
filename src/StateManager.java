import java.io.File;
import java.io.IOException;

/**
 * Created by evrignaud on 05/05/15.
 */
public class StateManager
{
	private File stateDir;

	public StateManager(File stateDir)
	{
		this.stateDir = stateDir;
	}

	public void createNewState(State state) throws IOException
	{
		state.writeToZipFile(getNextStateFile());
	}

	public File getNextStateFile()
	{
		int stateNumber = getLastStateNumber();
		stateNumber++;
		File statFile = getStateFile(stateNumber);
		return statFile;
	}

	public State loadLastState() throws IOException
	{
		int stateNumber = getLastStateNumber();
		File stateFile = getStateFile(stateNumber);

		if (!stateFile.exists())
		{
			throw new IllegalStateException("No state file found for this directory");
		}

		State state = new State();
		state.loadFromZipFile(stateFile);
		return state;
	}

	private File getStateFile(int stateNumber)
	{
		return new File(stateDir, "state_" + stateNumber + ".zjson");
	}

	private int getLastStateNumber()
	{
		for (int index = 1; ; index++)
		{
			File statFile = getStateFile(index);
			if (!statFile.exists())
			{
				return index - 1;
			}
		}
	}

	public void resetDates(State state)
	{
		System.out.println("Reset file modification dates based on previous state done " + FormatUtil.formatDate(state.timestamp));
		if (state.message.length() > 0)
		{
			System.out.println("With message: " + state.message);
		}
		System.out.println("");

		int dateResetCount = 0;
		for (FileState fileState : state.fileStates)
		{
			File file = new File(fileState.fileName);
			if (file.exists())
			{
				long lastModified = file.lastModified();
				if (lastModified != fileState.lastModified)
				{
					dateResetCount++;
					file.setLastModified(fileState.lastModified);
					System.out.printf("Set file modification: %s\t%s -> %s%n", fileState.fileName,
							FormatUtil.formatDate(lastModified), FormatUtil.formatDate(fileState.lastModified));
				}
			}
		}

		if (dateResetCount == 0)
		{
			System.out.printf("Nothing have been reset%n");
		}
		else
		{
			System.out.printf("%d file modification dates have been reset%n", dateResetCount);
		}
	}
}
