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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.fim.model.FileState;
import org.fim.model.HashMode;
import org.fim.model.Parameters;
import org.fim.model.State;
import org.fim.util.Logger;

public class StateGenerator
{
	public static final int PROGRESS_DISPLAY_FILE_COUNT = 10;
	public static final int FILES_QUEUE_CAPACITY = 500;

	private static final Set ignoredDirectories = new HashSet<>(Arrays.asList(Parameters.DOT_FIM_DIR, ".git", ".svn", ".cvs"));

	private static final Pair<Character, Integer>[] hashProgress = new Pair[]
			{
					Pair.of('.', 0),
					Pair.of('o', FileState.SIZE_20_MB),
					Pair.of('O', FileState.SIZE_50_MB),
					Pair.of('@', FileState.SIZE_100_MB),
					Pair.of('#', FileState.SIZE_200_MB)
			};

	private static Comparator<FileState> fileNameComparator = new FileState.FileNameComparator();

	private final Parameters parameters;
	private final ReentrantLock progressLock;

	private ExecutorService executorService;
	private long summedFileLength;
	private int fileCount;
	private long totalFileContentLength;

	private Path fimRepositoryRootDir;
	private BlockingDeque<Path> filesToHash;
	private boolean hashersStarted;
	private List<FileHasher> hashers;
	private long totalBytesHashed;

	public StateGenerator(Parameters parameters)
	{
		this.parameters = parameters;
		this.progressLock = new ReentrantLock();
	}

	public State generateState(String comment, Path fimRepositoryRootDir) throws IOException, NoSuchAlgorithmException
	{
		this.fimRepositoryRootDir = fimRepositoryRootDir;

		Logger.info(String.format("Scanning recursively local files, %s, using %d thread", hashModeToString(), parameters.getThreadCount()));
		if (displayHashLegend())
		{
			System.out.printf("(Hash progress legend for files grouped %d by %d: %s)%n", PROGRESS_DISPLAY_FILE_COUNT, PROGRESS_DISPLAY_FILE_COUNT, hashProgressLegend());
		}

		State state = new State();
		state.setComment(comment);

		long start = System.currentTimeMillis();
		progressOutputInit();

		filesToHash = new LinkedBlockingDeque<>(FILES_QUEUE_CAPACITY);
		InitializeFileHashers();

		scanFileTree(filesToHash, fimRepositoryRootDir);

		// In case the FileHashers have not been started
		startFileHashers();

		waitAllFilesToBeHashed();

		for (FileHasher hasher : hashers)
		{
			state.getFileStates().addAll(hasher.getFileStates());
			totalFileContentLength += hasher.getTotalFileContentLength();
			totalBytesHashed += hasher.getTotalBytesHashed();
		}

		Collections.sort(state.getFileStates(), fileNameComparator);

		progressOutputStop();
		displayStatistics(start, state);

		return state;
	}

	private void InitializeFileHashers()
	{
		hashersStarted = false;
		hashers = new ArrayList<>();
		executorService = Executors.newFixedThreadPool(parameters.getThreadCount());
	}

	private void startFileHashers() throws NoSuchAlgorithmException
	{
		if (!hashersStarted)
		{
			for (int index = 0; index < parameters.getThreadCount(); index++)
			{
				FileHasher hasher = new FileHasher(this, filesToHash, fimRepositoryRootDir.toString());
				executorService.submit(hasher);
				hashers.add(hasher);
			}
			hashersStarted = true;
		}
	}

	private String hashModeToString()
	{
		switch (parameters.getHashMode())
		{
			case DONT_HASH_FILES:
				return "retrieving only file attributes";

			case HASH_ONLY_FIRST_FOUR_KILO:
				return "hashing only first four kilo";

			case HASH_ONLY_FIRST_MEGA:
				return "hashing only first mega";

			case COMPUTE_ALL_HASH:
				return "computing all hash";
		}

		throw new IllegalArgumentException("Invalid hash mode " + parameters.getHashMode());
	}

	private void waitAllFilesToBeHashed()
	{
		try
		{
			executorService.shutdown();
			executorService.awaitTermination(3, TimeUnit.DAYS);
		}
		catch (InterruptedException ex)
		{
			Logger.error(ex);
		}
	}

	private void displayStatistics(long start, State state)
	{
		long duration = System.currentTimeMillis() - start;

		String totalFileContentLengthStr = FileUtils.byteCountToDisplaySize(totalFileContentLength);
		String totalBytesHashedStr = FileUtils.byteCountToDisplaySize(totalBytesHashed);
		String durationStr = DurationFormatUtils.formatDuration(duration, "HH:mm:ss");

		long durationSeconds = duration / 1000;
		long totalMegaHashed = totalBytesHashed / (1024 * 1024);
		long globalThroughput = totalMegaHashed / durationSeconds;
		String throughputStr = FileUtils.byteCountToDisplaySize(globalThroughput);

		if (parameters.getHashMode() == HashMode.DONT_HASH_FILES)
		{
			Logger.info(String.format("Scanned %d files (%s), during %s, using %d thread%n",
					state.getFileStates().size(), totalFileContentLengthStr, durationStr, parameters.getThreadCount()));
		}
		else
		{
			Logger.info(String.format("Scanned %d files (%s), hashed %s bytes (global throughput %s/s), during %s, using %d thread%n",
					state.getFileStates().size(), totalFileContentLengthStr, totalBytesHashedStr, throughputStr, durationStr, parameters.getThreadCount()));
		}
	}

	private void scanFileTree(BlockingDeque<Path> filesToHash, Path directory) throws IOException, NoSuchAlgorithmException
	{
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory))
		{
			for (Path file : stream)
			{
				if (!hashersStarted && filesToHash.size() > FILES_QUEUE_CAPACITY / 2)
				{
					startFileHashers();
				}

				BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
				if (attributes.isRegularFile())
				{
					try
					{
						filesToHash.offer(file, 120, TimeUnit.MINUTES);
					}
					catch (InterruptedException ex)
					{
						Logger.error(ex);
					}
				}
				else if (attributes.isDirectory())
				{
					String fileName = file.getFileName().toString();
					if (ignoredDirectories.contains(fileName))
					{
						continue;
					}

					scanFileTree(filesToHash, file);
				}
			}
		}
	}

	private void progressOutputInit()
	{
		summedFileLength = 0;
		fileCount = 0;
	}

	public void updateProgressOutput(long fileSize) throws IOException
	{
		progressLock.lock();
		try
		{
			fileCount++;

			if (displayHashLegend())
			{
				summedFileLength += fileSize;

				if (fileCount % PROGRESS_DISPLAY_FILE_COUNT == 0)
				{
					System.out.print(getProgressChar(summedFileLength));
					summedFileLength = 0;
				}
			}

			if (fileCount % (100 * PROGRESS_DISPLAY_FILE_COUNT) == 0)
			{
				if (displayHashLegend())
				{
					System.out.println("");
				}
			}
		}
		finally
		{
			progressLock.unlock();
		}
	}

	private String hashProgressLegend()
	{
		StringBuilder sb = new StringBuilder();
		for (int progressIndex = hashProgress.length - 1; progressIndex >= 0; progressIndex--)
		{
			char marker = hashProgress[progressIndex].getLeft();
			sb.append(marker);

			int fileLength = hashProgress[progressIndex].getRight();
			if (fileLength == 0)
			{
				sb.append(" otherwise");
			}
			else
			{
				sb.append(" > ").append(FileUtils.byteCountToDisplaySize(fileLength));
			}
			sb.append(", ");
		}
		String legend = sb.toString();
		legend = legend.substring(0, legend.length() - 2);
		return legend;
	}

	protected char getProgressChar(long fileLength)
	{
		int progressIndex;
		for (progressIndex = hashProgress.length - 1; progressIndex >= 0; progressIndex--)
		{
			if (fileLength >= hashProgress[progressIndex].getRight())
			{
				break;
			}
		}
		return hashProgress[progressIndex].getLeft();
	}

	private void progressOutputStop()
	{
		if (displayHashLegend())
		{
			if (fileCount > PROGRESS_DISPLAY_FILE_COUNT)
			{
				System.out.println("");
			}
		}
	}

	private boolean displayHashLegend()
	{
		return parameters.isVerbose() && parameters.getHashMode() != HashMode.DONT_HASH_FILES;
	}

	public Parameters getParameters()
	{
		return parameters;
	}
}
