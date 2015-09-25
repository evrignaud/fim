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

import static java.lang.Math.min;
import static org.fim.model.FileState.NO_HASH;
import static org.fim.model.FileState.SIZE_1_MB;
import static org.fim.model.FileState.SIZE_4_KB;
import static org.fim.model.HashMode.dontHash;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;

import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.HashMode;
import org.fim.util.Console;
import org.fim.util.FileUtil;
import org.fim.util.Logger;
import sun.misc.Cleaner;
import sun.nio.ch.DirectBuffer;

class FileHasher implements Runnable
{
	private final HashProgress hashProgress;
	private final BlockingDeque<Path> filesToHashQueue;
	private final String rootDir;

	private final List<FileState> fileStates;

	private final Hashers hashers;

	private long remainder;
	private long position;

	private long totalFileContentLength;
	private long totalBytesHashed;

	public FileHasher(HashProgress hashProgress, BlockingDeque<Path> filesToHashQueue, String rootDir) throws NoSuchAlgorithmException
	{
		this.hashProgress = hashProgress;
		this.filesToHashQueue = filesToHashQueue;
		this.rootDir = rootDir;

		this.fileStates = new ArrayList<>();

		HashMode hashMode = hashProgress.getContext().getHashMode();
		hashers = new Hashers(hashMode);
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
			while ((file = filesToHashQueue.poll(500, TimeUnit.MILLISECONDS)) != null)
			{
				try
				{
					BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);

					hashProgress.updateProgressOutput(attributes.size());

					FileHash fileHash = hashFile(file, attributes.size());
					String normalizedFileName = FileUtil.getNormalizedFileName(file);
					String relativeFileName = FileUtil.getRelativeFileName(rootDir, normalizedFileName);

					fileStates.add(new FileState(relativeFileName, attributes, fileHash));
				}
				catch (Exception ex)
				{
					Console.newLine();
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
		HashMode hashMode = hashProgress.getContext().getHashMode();

		if (hashMode == dontHash)
		{
			totalFileContentLength += fileSize;
			return new FileHash(NO_HASH, NO_HASH, NO_HASH);
		}

		hashers.reset(fileSize);

		remainder = fileSize;
		position = 0;

		try (final FileChannel channel = FileChannel.open(file))
		{
			// Start hashing 4 KB for the smallBlock hash
			hashBlock(channel, min(remainder, SIZE_4_KB), hashers);

			// If the file size is at least 8 KB we can skip the header, so hash once again 4 KB for the smallBlock hash
			hashBlock(channel, min(remainder, SIZE_4_KB), hashers);

			if ((position >= fileSize) || (hashMode == HashMode.hashSmallBlock))
			{
				return hashers.getFileHash();
			}

			// Hash the remaining part of the 1 MB block for the mediumBlock hash
			hashBlock(channel, min(remainder, SIZE_1_MB - position), hashers);

			// If the file size is at least 2 MB we can skip the header, so in the loop we will hash again 1 MB for the mediumBlock hash
			while (position < fileSize)
			{
				hashBlock(channel, min(remainder, SIZE_1_MB), hashers);
			}
		}
		finally
		{
			totalFileContentLength += fileSize;
			totalBytesHashed += position;
		}

		return hashers.getFileHash();
	}

	private int hashBlock(FileChannel channel, long blockSize, Hashers hashers) throws IOException
	{
		if (blockSize > 0)
		{
			MappedByteBuffer buffer = null;
			try
			{
				buffer = channel.map(FileChannel.MapMode.READ_ONLY, position, blockSize);
				int bufferSize = buffer.limit();

				hashers.update(position, buffer);

				position += bufferSize;
				remainder -= bufferSize;

				return bufferSize;
			}
			finally
			{
				unmap(buffer);
			}
		}
		return 0;
	}

	/**
	 * Comes from here: http://stackoverflow.com/questions/8553158/prevent-outofmemory-when-using-java-nio-mappedbytebuffer
	 */
	private void unmap(MappedByteBuffer bb)
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
}
