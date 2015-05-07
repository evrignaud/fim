import static java.nio.file.StandardOpenOption.CREATE;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;

/**
 * Created by evrignaud on 05/05/15.
 */
public class StateManager
{
	private Charset utf8 = Charset.forName("UTF-8");

	private File stateDir;
	private int lastStateNumber = -1;

	public StateManager(File stateDir)
	{
		this.stateDir = stateDir;
	}

	public void createNewState(State state) throws IOException
	{
		state.writeToZipFile(getNextStateFile());
		writeLastStateNumber();
	}

	public File getNextStateFile()
	{
		findLastStateNumber();
		lastStateNumber++;
		File statFile = getStateFile(lastStateNumber);
		return statFile;
	}

	public State loadLastState() throws IOException
	{
		findLastStateNumber();
		File stateFile = getStateFile(lastStateNumber);

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

	private void findLastStateNumber()
	{
		readLastStateNumber();
		if (lastStateNumber != -1)
		{
			return;
		}

		for (int index = 1; ; index++)
		{
			File statFile = getStateFile(index);
			if (!statFile.exists())
			{
				lastStateNumber = index - 1;
				return;
			}
		}
	}

	private void readLastStateNumber()
	{
		lastStateNumber = -1;

		File lastStateFile = new File(stateDir, "lastState");
		if (lastStateFile.exists())
		{
			try
			{
				List<String> strings = Files.readAllLines(lastStateFile.toPath(), utf8);
				if (strings.size() > 0)
				{
					lastStateNumber = Integer.parseInt(strings.get(0));
				}
			}
			catch (IOException ex)
			{
				ex.printStackTrace();
			}
		}
	}

	private void writeLastStateNumber()
	{
		if (lastStateNumber != -1)
		{
			File lastStateFile = new File(stateDir, "lastState");
			String content = "" + lastStateNumber;
			try
			{
				Files.write(lastStateFile.toPath(), content.getBytes(), CREATE);
			}
			catch (IOException ex)
			{
				ex.printStackTrace();
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
			System.out.printf("No file modification date have been reset%n");
		}
		else
		{
			System.out.printf("%d file modification dates have been reset%n", dateResetCount);
		}
	}
}
