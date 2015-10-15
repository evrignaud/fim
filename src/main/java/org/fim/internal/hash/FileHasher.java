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
import static org.fim.model.FileAttribute.SELinuxLabel;
import static org.fim.model.FileAttribute.dosFilePermissions;
import static org.fim.model.FileAttribute.posixFilePermissions;
import static org.fim.model.HashMode.dontHash;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.SystemUtils;
import org.fim.model.Attribute;
import org.fim.model.FileAttribute;
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.HashMode;
import org.fim.model.Range;
import org.fim.util.Console;
import org.fim.util.DosFilePermissions;
import org.fim.util.FileUtil;
import org.fim.util.Logger;
import org.fim.util.SELinux;
import sun.misc.Cleaner;
import sun.nio.ch.DirectBuffer;

public class FileHasher implements Runnable
{
	private final HashProgress hashProgress;
	private final BlockingDeque<Path> filesToHashQueue;
	private final String rootDir;

	private final List<FileState> fileStates;

	private final FrontHasher frontHasher;

	private long totalFileContentLength;

	public FileHasher(HashProgress hashProgress, BlockingDeque<Path> filesToHashQueue, String rootDir) throws NoSuchAlgorithmException
	{
		this.hashProgress = hashProgress;
		this.filesToHashQueue = filesToHashQueue;
		this.rootDir = rootDir;

		this.fileStates = new ArrayList<>();

		HashMode hashMode = hashProgress.getContext().getHashMode();
		frontHasher = new FrontHasher(hashMode);
	}

	public List<FileState> getFileStates()
	{
		return fileStates;
	}

	public long getTotalBytesHashed()
	{
		return frontHasher.getTotalBytesHashed();
	}

	public long getTotalFileContentLength()
	{
		return totalFileContentLength;
	}

	protected FrontHasher getFrontHasher()
	{
		return frontHasher;
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
					BasicFileAttributes attributes;
					List<Attribute> fileAttributes = null;

					if (SystemUtils.IS_OS_WINDOWS)
					{
						DosFileAttributes dosFileAttributes = Files.readAttributes(file, DosFileAttributes.class);
						fileAttributes = addAttribute(fileAttributes, dosFilePermissions, DosFilePermissions.toString(dosFileAttributes));
						attributes = dosFileAttributes;
					}
					else
					{
						PosixFileAttributes posixFileAttributes = Files.readAttributes(file, PosixFileAttributes.class);
						fileAttributes = addAttribute(fileAttributes, posixFilePermissions, PosixFilePermissions.toString(posixFileAttributes.permissions()));
						if (SELinux.ENABLED)
						{
							fileAttributes = addAttribute(fileAttributes, SELinuxLabel, SELinux.getLabel(file));
						}
						attributes = posixFileAttributes;
					}

					hashProgress.updateOutput(attributes.size());

					FileHash fileHash = hashFile(file, attributes.size());
					String normalizedFileName = FileUtil.getNormalizedFileName(file);
					String relativeFileName = FileUtil.getRelativeFileName(rootDir, normalizedFileName);

					fileStates.add(new FileState(relativeFileName, attributes, fileHash, fileAttributes));
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

	private List<Attribute> addAttribute(List<Attribute> attributes, FileAttribute attribute, String value)
	{
		if (value == null)
		{
			return attributes;
		}
		
		List<Attribute> newAttributes = attributes;
		if (newAttributes == null)
		{
			newAttributes = new ArrayList<>();
		}

		newAttributes.add(new Attribute(attribute.name(), value));

		return newAttributes;
	}

	protected FileHash hashFile(Path file, long fileSize) throws IOException
	{
		HashMode hashMode = hashProgress.getContext().getHashMode();

		if (hashMode == dontHash)
		{
			totalFileContentLength += fileSize;
			return new FileHash(NO_HASH, NO_HASH, NO_HASH);
		}

		frontHasher.reset(fileSize);

		long filePosition = 0;
		long blockSize;
		int bufferSize;

		try (final FileChannel channel = FileChannel.open(file))
		{
			while (filePosition < fileSize)
			{
				Range nextRange = frontHasher.getNextRange(filePosition);
				if (nextRange == null)
				{
					break;
				}

				filePosition = nextRange.getFrom();
				blockSize = nextRange.getTo() - nextRange.getFrom();
				bufferSize = hashBuffer(channel, filePosition, blockSize);
				filePosition += bufferSize;
			}
		}
		finally
		{
			totalFileContentLength += fileSize;
		}

		if (false == frontHasher.hashComplete())
		{
			throw new RuntimeException("Fim is not working correctly. Some Hasher have not completed: " +
					"small=" + frontHasher.getSmallBlockHasher().hashComplete() + ", " +
					"medium=" + frontHasher.getMediumBlockHasher().hashComplete() + ", " +
					"full=" + frontHasher.getFullHasher().hashComplete());
		}

		return frontHasher.getFileHash();
	}

	private int hashBuffer(FileChannel channel, long filePosition, long size) throws IOException
	{
		MappedByteBuffer buffer = null;
		try
		{
			buffer = channel.map(FileChannel.MapMode.READ_ONLY, filePosition, size);
			int bufferSize = buffer.remaining();

			frontHasher.update(filePosition, buffer);

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
