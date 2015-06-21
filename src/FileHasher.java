import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.List;

/**
* Created by evrignaud on 21/06/15.
*/
class FileHasher implements Runnable
{
	private StateGenerator stateGenerator;
	private final String baseDirectory;
	private List<FileState> fileStates;
	private File file;

	public FileHasher(StateGenerator stateGenerator, List<FileState> fileStates, String baseDirectory, File file)
	{
		this.stateGenerator = stateGenerator;
		this.fileStates = fileStates;
		this.baseDirectory = baseDirectory;
		this.file = file;
	}

	@Override
	public void run()
	{
		stateGenerator.updateProgressOutput(file);

		String hash = hashFile(file);
		String fileName = file.toString();
		fileName = getRelativeFileName(baseDirectory, fileName);
		fileStates.add(new FileState(fileName, file.lastModified(), hash));
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
		if (stateGenerator.getCompareMode() == CompareMode.FAST)
		{
			return StateGenerator.NO_HASH;
		}

		try
		{
			MessageDigest md = MessageDigest.getInstance("SHA-512");
			FileInputStream fis = new FileInputStream(file);

			byte[] dataBytes;

			if (file.length() < StateGenerator.SIZE_50_MO)
			{
				dataBytes = Files.readAllBytes(file.toPath());
				md.update(dataBytes, 0, dataBytes.length);
			}
			else
			{
				dataBytes = new byte[1024];
				int nread;
				while ((nread = fis.read(dataBytes)) != -1)
				{
					md.update(dataBytes, 0, nread);
				}
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
}
