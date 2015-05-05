import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Created by evrignaud on 05/05/15.
 */
public class State
{
	public String baseDirectory;
	long timestamp = System.currentTimeMillis();
	String message = "";
	List<FileState> fileStates = new ArrayList<>();

	public void writeToFile(File stateFile) throws IOException
	{
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(stateFile)))
		{
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			gson.toJson(this, writer);
		}
	}

	public void loadFromFile(File stateFile) throws IOException
	{
		try (BufferedReader reader = new BufferedReader(new FileReader(stateFile)))
		{
			Gson gson = new Gson();
			State state = gson.fromJson(reader, State.class);
			baseDirectory = state.baseDirectory;
			timestamp = state.timestamp;
			message = state.message;
			fileStates = state.fileStates;
		}
	}
}
