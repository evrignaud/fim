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
	private final File stateDir;
	private final CompareMode compareMode;
	private Charset utf8 = Charset.forName("UTF-8");
	private int previousStateNumber = -1;

	public StateManager(File stateDir, CompareMode compareMode)
	{
		this.stateDir = stateDir;
		this.compareMode = compareMode;
	}

	public void createNewState(State state) throws IOException
	{
		state.writeToZipFile(getNextStateFile());
		writePreviousStateNumber();
	}

	public File getNextStateFile()
	{
		findPreviousStateNumber();
		previousStateNumber++;
		File statFile = getStateFile(previousStateNumber);
		return statFile;
	}

	public State loadPreviousState() throws IOException
	{
		findPreviousStateNumber();
		return loadState(previousStateNumber);
	}

	public State loadState(int stateNumber) throws IOException
	{
		File stateFile = getStateFile(stateNumber);

		if (!stateFile.exists())
		{
			throw new IllegalStateException("No state file found for this directory");
		}

		State state = new State();
		state.loadFromZipFile(stateFile);

		if (compareMode == CompareMode.FAST)
		{
			// Replace the real file hash by 'no_hash' to be able to compare the FileState entry
			for (FileState fileState : state.getFileStates())
			{
				fileState.setHash(StateGenerator.NO_HASH);
			}
		}

		return state;
	}

	private File getStateFile(int stateNumber)
	{
		return new File(stateDir, "state_" + stateNumber + ".zjson");
	}

	private void findPreviousStateNumber()
	{
		readPreviousStateNumber();
		if (previousStateNumber != -1)
		{
			return;
		}

		for (int index = 1; ; index++)
		{
			File statFile = getStateFile(index);
			if (!statFile.exists())
			{
				previousStateNumber = index - 1;
				return;
			}
		}
	}

	private void readPreviousStateNumber()
	{
		previousStateNumber = -1;

		File previousStateFile = new File(stateDir, "previousState");
		if (previousStateFile.exists())
		{
			try
			{
				List<String> strings = Files.readAllLines(previousStateFile.toPath(), utf8);
				if (strings.size() > 0)
				{
					previousStateNumber = Integer.parseInt(strings.get(0));
				}
			}
			catch (IOException ex)
			{
				ex.printStackTrace();
			}
		}
	}

	private void writePreviousStateNumber()
	{
		if (previousStateNumber != -1)
		{
			File lastStateFile = new File(stateDir, "lastState");
			String content = "" + previousStateNumber;
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
		System.out.println("Reset file modification dates based on previous state done " + FormatUtil.formatDate(state.getTimestamp()));
		if (state.getMessage().length() > 0)
		{
			System.out.println("Message: " + state.getMessage());
		}
		System.out.println("");

		int dateResetCount = 0;
		for (FileState fileState : state.getFileStates())
		{
			File file = new File(fileState.getFileName());
			if (file.exists())
			{
				long lastModified = file.lastModified();
				if (lastModified != fileState.getLastModified())
				{
					dateResetCount++;
					file.setLastModified(fileState.getLastModified());
					System.out.printf("Set file modification: %s\t%s -> %s%n", fileState.getFileName(),
							FormatUtil.formatDate(lastModified), FormatUtil.formatDate(fileState.getLastModified()));
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

	public void displayLog() throws IOException
	{
		readPreviousStateNumber();
		if (previousStateNumber == -1)
		{
			System.out.println("No state created");
			return;
		}

		for (int stateNumber = 1; stateNumber <= previousStateNumber; stateNumber++)
		{
			File statFile = getStateFile(stateNumber);
			if (statFile.exists())
			{
				State state = loadState(stateNumber);
				System.out.printf("State #%d: %s%n", stateNumber, FormatUtil.formatDate(state.getTimestamp()));
				if (state.getMessage().length() > 0)
				{
					System.out.printf("\tMessage: %s%n", state.getMessage());
				}
				System.out.printf("\tContains %d files%n", state.getFileStates().size());
				System.out.println("");
			}
		}
	}
}
