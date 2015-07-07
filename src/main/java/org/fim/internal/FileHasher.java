/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2015  Etienne Vrignaud
 *
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
 * along with Fim.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.fim.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import org.fim.model.CompareMode;
import org.fim.model.FileState;

class FileHasher implements Runnable
{
	public static final String HASH_ALGORITHM = "SHA-512";

	private final StateGenerator stateGenerator;
	private final List<FileState> fileStates;
	private final String baseDirectory;
	private final File file;
	private final MessageDigest messageDigest;

	public FileHasher(StateGenerator stateGenerator, List<FileState> fileStates, String baseDirectory, File file) throws NoSuchAlgorithmException
	{
		this.stateGenerator = stateGenerator;
		this.fileStates = fileStates;
		this.baseDirectory = baseDirectory;
		this.file = file;
		this.messageDigest = MessageDigest.getInstance(HASH_ALGORITHM);
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

	protected String getRelativeFileName(String baseDirectory, String fileName)
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

	protected String hashFile(File file)
	{
		if (stateGenerator.getCompareMode() == CompareMode.FAST)
		{
			return StateGenerator.NO_HASH;
		}

		try
		{
			if (file.length() < StateGenerator.SIZE_50_MO)
			{
				return hashOnceTheCompleteFileContent(file);
			}
			else
			{
				return hashFileChunkByChunk(file);
			}
		}
		catch (Exception ex)
		{
			throw new RuntimeException("Exception occurred during file hashing", ex);
		}
	}

	protected String hashOnceTheCompleteFileContent(File file) throws IOException
	{
		messageDigest.reset();

		byte[] dataBytes = Files.readAllBytes(file.toPath());
		messageDigest.update(dataBytes);

		byte[] digestBytes = messageDigest.digest();
		return toHexString(digestBytes);
	}

	protected String hashFileChunkByChunk(File file) throws IOException
	{
		messageDigest.reset();

		try (FileInputStream fis = new FileInputStream(file))
		{
			byte[] dataBytes = new byte[1024];
			int nread;
			while ((nread = fis.read(dataBytes)) != -1)
			{
				messageDigest.update(dataBytes, 0, nread);
			}
		}

		byte[] digestBytes = messageDigest.digest();
		return toHexString(digestBytes);
	}

	protected String toHexString(byte[] digestBytes)
	{
		StringBuilder hexString = new StringBuilder();
		for (byte b : digestBytes)
		{
			hexString.append(Character.forDigit((b >> 4) & 0xF, 16));
			hexString.append(Character.forDigit((b & 0xF), 16));
		}

		return hexString.toString();
	}
}
