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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;

import org.fim.model.FileHash;
import org.fim.model.FileState;

class FileHasher implements Runnable
{
	public static final String HASH_ALGORITHM = "SHA-512";

	private final StateGenerator stateGenerator;
	private final BlockingDeque<File> filesToHash;
	private final String fimRepositoryRootDir;

	private final List<FileState> fileStates;
	private final MessageDigest messageDigest;

	private long totalFileContentLength;
	private long totalBytesHashed;

	public FileHasher(StateGenerator stateGenerator, BlockingDeque<File> filesToHash, String fimRepositoryRootDir) throws NoSuchAlgorithmException
	{
		this.stateGenerator = stateGenerator;
		this.filesToHash = filesToHash;
		this.fimRepositoryRootDir = fimRepositoryRootDir;

		this.fileStates = new ArrayList<>();
		this.messageDigest = MessageDigest.getInstance(HASH_ALGORITHM);
	}

	public List<FileState> getFileStates()
	{
		return fileStates;
	}

	public long getTotalFileContentLength()
	{
		return totalFileContentLength;
	}

	public long getTotalBytesHashed()
	{
		return totalBytesHashed;
	}

	@Override
	public void run()
	{
		try
		{
			File file;
			while ((file = filesToHash.poll(10, TimeUnit.SECONDS)) != null)
			{
				stateGenerator.updateProgressOutput(file);

				try
				{
					FileHash fileHash = hashFile(file);
					String fileName = file.toString();
					fileName = getRelativeFileName(fimRepositoryRootDir, fileName);
					fileStates.add(new FileState(fileName, file.length(), file.lastModified(), fileHash));
				}
				catch (Exception ex)
				{
					System.err.printf("%nSkipping file hash. Not able to hash '%s': %s%n", file.getName(), ex.getMessage());
				}
			}
		}
		catch (InterruptedException ex)
		{
			ex.printStackTrace();
		}
	}

	protected String getRelativeFileName(String directory, String fileName)
	{
		if (fileName.startsWith(directory))
		{
			fileName = fileName.substring(directory.length());
		}

		if (fileName.startsWith("/"))
		{
			fileName = fileName.substring(1);
		}
		return fileName;
	}

	protected FileHash hashFile(File file) throws IOException
	{
		String firstFourKiloHash = FileState.NO_HASH;
		String firstMegaHash = FileState.NO_HASH;
		String fullHash = FileState.NO_HASH;

		switch (stateGenerator.getParameters().getHashMode())
		{
			case DONT_HASH_FILES:
				break;

			case HASH_ONLY_FIRST_FOUR_KILO:
				firstFourKiloHash = hashFileChunkByChunk(file, FileState.SIZE_4_KB);
				break;

			case HASH_ONLY_FIRST_MEGA:
				firstMegaHash = hashFileChunkByChunk(file, FileState.SIZE_1_MB);
				break;

			case COMPUTE_ALL_HASH:
				firstFourKiloHash = hashFileChunkByChunk(file, FileState.SIZE_4_KB);
				firstMegaHash = hashFileChunkByChunk(file, FileState.SIZE_1_MB);
				fullHash = hashFileChunkByChunk(file, FileState.SIZE_UNLIMITED);
				break;
		}

		totalFileContentLength += file.length();

		return new FileHash(firstFourKiloHash, firstMegaHash, fullHash);
	}

	protected String hashFileUsingNIO(File file) throws IOException
	{
		messageDigest.reset();

		byte[] dataBytes = Files.readAllBytes(file.toPath());
		messageDigest.update(dataBytes);

		totalBytesHashed += file.length();

		byte[] digestBytes = messageDigest.digest();
		return toHexString(digestBytes);
	}

	protected String hashFileChunkByChunk(File file, long lengthToHash) throws IOException
	{
		messageDigest.reset();

		try (FileInputStream fis = new FileInputStream(file))
		{
			byte[] dataBytes = new byte[FileState.SIZE_4_KB];
			long bytesHashed = 0;
			int bytesRead;
			while ((bytesRead = fis.read(dataBytes)) != -1)
			{
				messageDigest.update(dataBytes, 0, bytesRead);
				bytesHashed += bytesRead;

				if (lengthToHash != FileState.SIZE_UNLIMITED && bytesHashed >= lengthToHash)
				{
					break;
				}
			}

			totalBytesHashed += bytesHashed;
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
