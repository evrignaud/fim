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

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;

import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.HashMode;
import org.fim.util.FileUtils;
import org.fim.util.Logger;
import sun.misc.Cleaner;
import sun.nio.ch.DirectBuffer;

class FileHasher implements Runnable
{
	public static final String HASH_ALGORITHM = "SHA-512";

	private final StateGenerator stateGenerator;
	private final BlockingDeque<Path> filesToHash;
	private final String rootDir;

	private final List<FileState> fileStates;

	private final MessageDigest smallBlockDigest;
	private final MessageDigest mediumBlockDigest;
	private final MessageDigest fullDigest;

	private long totalFileContentLength;
	private long totalBytesHashed;

	public FileHasher(StateGenerator stateGenerator, BlockingDeque<Path> filesToHash, String rootDir) throws NoSuchAlgorithmException
	{
		this.stateGenerator = stateGenerator;
		this.filesToHash = filesToHash;
		this.rootDir = rootDir;

		this.fileStates = new ArrayList<>();

		this.smallBlockDigest = MessageDigest.getInstance(HASH_ALGORITHM);
		this.mediumBlockDigest = MessageDigest.getInstance(HASH_ALGORITHM);
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
			Path file;
			while ((file = filesToHash.poll(500, TimeUnit.MILLISECONDS)) != null)
			{
				try
				{
					BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);

					stateGenerator.updateProgressOutput(attributes.size());

					FileHash fileHash = hashFile(file, attributes.size());
					String normalizedFileName = FileUtils.getNormalizedFileName(file);
					normalizedFileName = FileUtils.getRelativeFileName(rootDir, normalizedFileName);

					fileStates.add(new FileState(normalizedFileName, attributes.size(), attributes.lastModifiedTime().toMillis(), fileHash));
				}
				catch (Exception ex)
				{
					System.out.println("");
					Logger.error("Skipping - Error hashing file", ex);
				}
			}
		}
		catch (InterruptedException ex)
		{
			Logger.error(ex);
		}
	}

	protected FileHash hashFile(Path file, long fileSize) throws IOException
	{
		HashMode hashMode = stateGenerator.getContext().getHashMode();

		if (hashMode == HashMode.dontHashFiles)
		{
			totalFileContentLength += fileSize;
			return new FileHash(FileState.NO_HASH, FileState.NO_HASH, FileState.NO_HASH);
		}

		smallBlockDigest.reset();
		mediumBlockDigest.reset();
		fullDigest.reset();

		MappedByteBuffer buffer = null;
		long remainder = fileSize;
		long position = 0;

		try (final FileChannel channel = FileChannel.open(file))
		{
			long size = Math.min(remainder, FileState.SIZE_4_KB);
			buffer = channel.map(FileChannel.MapMode.READ_ONLY, position, size);
			position += buffer.limit();
			remainder -= buffer.limit();

			smallBlockDigest.update(buffer);
			if (hashMode == HashMode.hashSmallBlock)
			{
				return new FileHash(getHash(smallBlockDigest), FileState.NO_HASH, FileState.NO_HASH);
			}

			buffer.flip();
			mediumBlockDigest.update(buffer);

			buffer.flip();
			fullDigest.update(buffer);

			if (position < fileSize)
			{
				size = Math.min(remainder, FileState.SIZE_1_MB - position);
				unmap(buffer);
				buffer = channel.map(FileChannel.MapMode.READ_ONLY, position, size);
				position += buffer.limit();
				remainder -= buffer.limit();

				mediumBlockDigest.update(buffer);
				if (hashMode == HashMode.hashMediumBlock)
				{
					return new FileHash(getHash(smallBlockDigest), getHash(mediumBlockDigest), FileState.NO_HASH);
				}

				buffer.flip();
				fullDigest.update(buffer);

				while (position < fileSize)
				{
					size = Math.min(remainder, FileState.SIZE_1_MB);
					unmap(buffer);
					buffer = channel.map(FileChannel.MapMode.READ_ONLY, position, size);
					position += buffer.limit();
					remainder -= buffer.limit();

					fullDigest.update(buffer);
				}
			}
		}
		finally
		{
			unmap(buffer);
			totalFileContentLength += fileSize;
			totalBytesHashed += position;
		}

		if (hashMode == HashMode.hashMediumBlock)
		{
			return new FileHash(getHash(smallBlockDigest), getHash(mediumBlockDigest), FileState.NO_HASH);
		}

		return new FileHash(getHash(smallBlockDigest), getHash(mediumBlockDigest), getHash(fullDigest));
	}

	/**
	 * Comes from here: http://stackoverflow.com/questions/8553158/prevent-outofmemory-when-using-java-nio-mappedbytebuffer
	 */
	public void unmap(MappedByteBuffer bb)
	{
		if (bb == null)
		{
			return;
		}
		Cleaner cleaner = ((DirectBuffer) bb).cleaner();
		if (cleaner != null)
		{
			cleaner.clean();
		}
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
