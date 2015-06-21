package org.fic;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.List;

import org.fic.model.FileState;

/**
 * Created by evrignaud on 21/06/15.
 */
class FileHasher implements Runnable
{
	private final StateGenerator stateGenerator;
	private final List<FileState> fileStates;
	private final String baseDirectory;
	private final File file;

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
			MessageDigest digest = MessageDigest.getInstance("SHA-512");
			byte[] dataBytes;

			if (file.length() < StateGenerator.SIZE_50_MO)
			{
				dataBytes = Files.readAllBytes(file.toPath());
				digest.update(dataBytes, 0, dataBytes.length);
			}
			else
			{
				try (FileInputStream fis = new FileInputStream(file))
				{
					dataBytes = new byte[1024];
					int nread;
					while ((nread = fis.read(dataBytes)) != -1)
					{
						digest.update(dataBytes, 0, nread);
					}
				}
			}

			byte[] digestBytes = digest.digest();

			StringBuffer hexString = new StringBuffer();
			for (byte b : digestBytes)
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
