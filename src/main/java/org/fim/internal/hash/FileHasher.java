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
package org.fim.internal.hash;

import static org.fim.model.Contants.NO_HASH;
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

public class FileHasher implements Runnable
{
	private final HashProgress hashProgress;
	private final BlockingDeque<Path> filesToHashQueue;
	private final String rootDir;

	private final List<FileState> fileStates;

	private final Hashers hashers;

	private long totalFileContentLength;

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

	public Hashers getHashers()
	{
		return hashers;
	}

	public long getTotalFileContentLength()
	{
		return totalFileContentLength;
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

					hashProgress.updateOutput(attributes.size());

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

		long filePosition = 0;

		try (final FileChannel channel = FileChannel.open(file))
		{
			while (filePosition < fileSize)
			{
				Range nextRange = hashers.getNextRange(filePosition);
				if (nextRange == null)
				{
					break;
				}

				filePosition = nextRange.getFrom();
				if (filePosition > fileSize)
				{
					break;
				}

				long blockSize = nextRange.getTo() - nextRange.getFrom();
				int bufferSize = hashBuffer(channel, filePosition, blockSize);
				filePosition += bufferSize;
			}
		}
		finally
		{
			totalFileContentLength += fileSize;
		}

		if (false == hashers.hashComplete())
		{
			throw new RuntimeException("Fim cannot work correctly. hashers have not completed");
		}

		return hashers.getFileHash();
	}

	private int hashBuffer(FileChannel channel, long filePosition, long size) throws IOException
	{
		MappedByteBuffer buffer = null;
		try
		{
			buffer = channel.map(FileChannel.MapMode.READ_ONLY, filePosition, size);
			int bufferSize = buffer.remaining();

			hashers.update(filePosition, buffer);

			return bufferSize;
		}
		finally
		{
			unmap(buffer);
		}
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
