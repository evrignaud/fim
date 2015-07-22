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
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;

import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.HashMode;

class FileHasher implements Runnable
{
	public static final String HASH_ALGORITHM = "SHA-512";

	private final StateGenerator stateGenerator;
	private final BlockingDeque<File> filesToHash;
	private final String fimRepositoryRootDir;

	private final List<FileState> fileStates;

	private final MessageDigest firstFourKiloDigest;
	private final MessageDigest firstMegaDigest;
	private final MessageDigest fullDigest;

	private long totalFileContentLength;
	private long totalBytesHashed;

	public FileHasher(StateGenerator stateGenerator, BlockingDeque<File> filesToHash, String fimRepositoryRootDir) throws NoSuchAlgorithmException
	{
		this.stateGenerator = stateGenerator;
		this.filesToHash = filesToHash;
		this.fimRepositoryRootDir = fimRepositoryRootDir;

		this.fileStates = new ArrayList<>();

		this.firstFourKiloDigest = MessageDigest.getInstance(HASH_ALGORITHM);
		this.firstMegaDigest = MessageDigest.getInstance(HASH_ALGORITHM);
		this.fullDigest = MessageDigest.getInstance(HASH_ALGORITHM);
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
			while ((file = filesToHash.poll(500, TimeUnit.MILLISECONDS)) != null)
			{
				stateGenerator.updateProgressOutput(file);

				try
				{
					FileHash fileHash = hashFile(file);
					String normalizedFileName = getNormalizedFileName(file);
					normalizedFileName = getRelativeFileName(fimRepositoryRootDir, normalizedFileName);
					fileStates.add(new FileState(normalizedFileName, file.length(), file.lastModified(), fileHash));
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

	private String getNormalizedFileName(File file)
	{
		String normalizedFileName = file.toString();
		if (file.separatorChar != '/')
		{
			normalizedFileName = normalizedFileName.replace(File.separatorChar, '/');
		}
		return normalizedFileName;
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
		HashMode hashMode = stateGenerator.getParameters().getHashMode();

		if (hashMode == HashMode.DONT_HASH_FILES)
		{
			totalFileContentLength += file.length();
			return new FileHash(FileState.NO_HASH, FileState.NO_HASH, FileState.NO_HASH);
		}

		firstFourKiloDigest.reset();
		firstMegaDigest.reset();
		fullDigest.reset();

		MappedByteBuffer data;
		long fileSize = 0;
		long remainder = 0;
		long position = 0;
		final Path path = file.toPath();

		try (final FileChannel channel = FileChannel.open(path))
		{
			fileSize = channel.size();
			remainder = fileSize;

			long size = Math.min(remainder, FileState.SIZE_4_KB);
			data = channel.map(FileChannel.MapMode.READ_ONLY, position, size);
			position += data.limit();
			remainder -= data.limit();

			firstFourKiloDigest.update(data);
			if (hashMode == HashMode.HASH_ONLY_FIRST_FOUR_KILO)
			{
				return new FileHash(getHash(firstFourKiloDigest), FileState.NO_HASH, FileState.NO_HASH);
			}

			data.flip();
			firstMegaDigest.update(data);

			data.flip();
			fullDigest.update(data);

			if (position < fileSize)
			{
				size = Math.min(remainder, FileState.SIZE_1_MB - position);
				data = channel.map(FileChannel.MapMode.READ_ONLY, position, size);
				position += data.limit();
				remainder -= data.limit();

				firstMegaDigest.update(data);
				if (hashMode == HashMode.HASH_ONLY_FIRST_MEGA)
				{
					return new FileHash(FileState.NO_HASH, getHash(firstMegaDigest), FileState.NO_HASH);
				}

				data.flip();
				fullDigest.update(data);

				while (position < fileSize)
				{
					size = Math.min(remainder, FileState.SIZE_1_MB);
					data = channel.map(FileChannel.MapMode.READ_ONLY, position, size);
					position += data.limit();
					remainder -= data.limit();

					fullDigest.update(data);
				}
			}
		}
		finally
		{
			totalFileContentLength += fileSize;
			totalBytesHashed += position;
		}

		if (hashMode == HashMode.HASH_ONLY_FIRST_MEGA)
		{
			return new FileHash(FileState.NO_HASH, getHash(firstMegaDigest), FileState.NO_HASH);
		}
		return new FileHash(getHash(firstFourKiloDigest), getHash(firstMegaDigest), getHash(fullDigest));
	}

	private String getHash(MessageDigest digest)
	{
		byte[] digestBytes = digest.digest();
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
