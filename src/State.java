import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Created by evrignaud on 05/05/15.
 */
public class State
{
	public long timestamp = System.currentTimeMillis();
	public String message = "";
	public List<FileState> fileStates = null;

	public void writeToZipFile(File stateFile) throws IOException
	{

		try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(stateFile))))
		{
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			gson.toJson(this, writer);
		}
	}

	public void loadFromZipFile(File stateFile) throws IOException
	{
		try (Reader reader = new InputStreamReader(new GZIPInputStream(new FileInputStream(stateFile))))
		{
			Gson gson = new Gson();
			State state = gson.fromJson(reader, State.class);
			timestamp = state.timestamp;
			message = state.message;
			fileStates = state.fileStates;
		}
	}
}
