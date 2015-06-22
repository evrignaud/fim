package org.fim;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import org.fim.model.FileState;
import org.fim.model.State;

/**
 * Created by evrignaud on 22/06/15.
 */
public class StateBuilder
{
	private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

	private State state;

	public StateBuilder()
	{
		state = new State();
		state.setFileStates(new ArrayList<FileState>());
	}

	public StateBuilder addFile(String fileName, String modificationDate, String fileContent) throws ParseException
	{
		FileState fileState = new FileState(fileName, toMilliseconds(modificationDate), hashFileContent(fileContent));
		state.getFileStates().add(fileState);
		return this;
	}

	private long toMilliseconds(String stringDate) throws ParseException
	{
		Date date = dateFormat.parse(stringDate);
		long milliseconds = date.getTime();
		return milliseconds;
	}

	private String hashFileContent(String fileContent)
	{
		// Return the file content itself as hash
		return fileContent;
	}

	public State build()
	{
		return state;
	}
}
