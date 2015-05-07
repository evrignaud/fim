import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Comparator;

/**
 * Created by evrignaud on 05/05/15.
 */
public class StateGenerator
{
	private Comparator<FileState> fileNameComparator = new FileNameComparator();

	public State generateState(String message, File baseDirectory) throws IOException
	{
		State state = new State();
		state.message = message;
		getFileStates(state, baseDirectory.toString(), baseDirectory);
		return state;
	}

	private void getFileStates(State state, String baseDirectory, File directory)
	{
		File[] files = directory.listFiles();
		for (File file : files)
		{
			if (file.isDirectory() && file.getName().equals(".bm"))
			{
				continue;
			}

			if (file.isDirectory())
			{
				getFileStates(state, baseDirectory, file);
			}
			else
			{
				String hash = hashFile(file);
				String fileName = file.toString();
				fileName = getRelativeFileName(baseDirectory, fileName);
				state.fileStates.add(new FileState(fileName, file.lastModified(), hash));
			}
		}

		Collections.sort(state.fileStates, fileNameComparator);
	}

	private String getRelativeFileName(String baseDirectory, String fileName)
	{
		if (fileName.startsWith(baseDirectory))
		{
			fileName = fileName.substring(baseDirectory.length());
		}
		if (fileName.startsWith("/"))
		{
			fileName = fileName.substring(1);
		}
		return fileName;
	}

	private String hashFile(File file)
	{
		try
		{
			MessageDigest md = MessageDigest.getInstance("SHA-512");
			FileInputStream fis = new FileInputStream(file);

			byte[] dataBytes = new byte[1024];

			int nread;
			while ((nread = fis.read(dataBytes)) != -1)
			{
				md.update(dataBytes, 0, nread);
			}

			byte[] mdbytes = md.digest();

			StringBuffer hexString = new StringBuffer();
			for (byte b : mdbytes)
			{
				hexString.append(String.format("%x", b));
			}

			return hexString.toString();

		}
		catch (Exception e)
		{
			e.printStackTrace();
			return "????";
		}
	}

	private class FileNameComparator implements Comparator<FileState>
	{
		@Override
		public int compare(FileState fs1, FileState fs2)
		{
			return fs1.fileName.compareTo(fs2.fileName);
		}
	}
}
