/**
 * Fim is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Fim is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.fim;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.List;

import org.fim.model.FileState;

/**
 * @author evrignaud
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
