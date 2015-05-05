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
		this.stateDir.mkdirs();
	}

	public void createNewState(State state) throws IOException
	{
		state.writeToFile(getNextStateFile());
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
		State state = new State();
		state.loadFromFile(stateFile);
		return state;
	}

	private File getStateFile(int stateNumber)
	{
		return new File(stateDir, "state_" + stateNumber + ".json");
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

}
