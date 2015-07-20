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
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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
	private long totalBytesHashed;

	public StateGenerator(Parameters parameters)
	{
		this.parameters = parameters;
		this.progressLock = new ReentrantLock();
	}

	public State generateState(String comment, File fimRepositoryRootDir) throws IOException, NoSuchAlgorithmException
	{
		Logger.info(String.format("Scanning recursively local files, %s, using %d thread", hashModeToString(), parameters.getThreadCount()));
		System.out.printf("    (Hash progress legend: " + hashProgressLegend() + ")%n");

		State state = new State();
		state.setComment(comment);

		long start = System.currentTimeMillis();
		progressOutputInit();

		BlockingDeque<File> filesToHash = new LinkedBlockingDeque<>(1000);

		List<FileHasher> hashers = new ArrayList<>();
		executorService = Executors.newFixedThreadPool(parameters.getThreadCount());
		for (int index = 0; index < parameters.getThreadCount(); index++)
		{
			FileHasher hasher = new FileHasher(this, filesToHash, fimRepositoryRootDir.toString());
			executorService.submit(hasher);
			hashers.add(hasher);
		}

		scanFileTree(filesToHash, fimRepositoryRootDir);

		waitAllFileHashed();

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

	private void waitAllFileHashed()
	{
		try
		{
			executorService.shutdown();
			executorService.awaitTermination(3, TimeUnit.DAYS);
		}
		catch (InterruptedException ex)
		{
			ex.printStackTrace();
		}
	}

	private void displayStatistics(long start, State state)
	{
		long duration = System.currentTimeMillis() - start;

		String totalFileContentLengthStr = FileUtils.byteCountToDisplaySize(totalFileContentLength);
		String totalBytesHashedStr = FileUtils.byteCountToDisplaySize(totalBytesHashed);
		String durationStr = DurationFormatUtils.formatDuration(duration, "HH:mm:ss");

		if (parameters.getHashMode() == HashMode.DONT_HASH_FILES)
		{
			Logger.info(String.format("Scanned %d files (%s), during %s, using %d thread%n",
					state.getFileStates().size(), totalBytesHashedStr, durationStr, parameters.getThreadCount()));
		}
		else
		{
			Logger.info(String.format("Scanned %d files (%s), hashed %s bytes, during %s, using %d thread%n",
					state.getFileStates().size(), totalFileContentLengthStr, totalBytesHashedStr, durationStr, parameters.getThreadCount()));
		}
	}

	private void scanFileTree(BlockingDeque<File> filesToHash, File directory) throws NoSuchAlgorithmException
	{
		List<File> files = Arrays.asList(directory.listFiles());
		Collections.sort(files);

		for (File file : files)
		{
			if (file.isDirectory() && file.getName().equals(Parameters.DOT_FIM_DIR))
			{
				continue;
			}

			if (Files.isSymbolicLink(file.toPath()))
			{
				continue;
			}

			if (file.isDirectory())
			{
				scanFileTree(filesToHash, file);
			}
			else
			{
				try
				{
					filesToHash.offer(file, 60, TimeUnit.MINUTES);
				}
				catch (InterruptedException ex)
				{
					ex.printStackTrace();
				}
			}
		}
	}

	private void progressOutputInit()
	{
		progressLock.lock();
		try
		{
			summedFileLength = 0;
			fileCount = 0;
		}
		finally
		{
			progressLock.unlock();
		}
	}

	public void updateProgressOutput(File file)
	{
		progressLock.lock();
		try
		{
			summedFileLength += file.length();
			fileCount++;

			if (fileCount % PROGRESS_DISPLAY_FILE_COUNT == 0)
			{
				System.out.print(getProgressChar());
				summedFileLength = 0;
			}

			if (fileCount % (100 * PROGRESS_DISPLAY_FILE_COUNT) == 0)
			{
				System.out.println("");

				// Very important to avoid os::commit_memory error caused by very big usage of FileChannel.map()
				System.gc();
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

	private char getProgressChar()
	{
		int progressIndex;
		for (progressIndex = hashProgress.length - 1; progressIndex >= 0; progressIndex--)
		{
			if (summedFileLength > hashProgress[progressIndex].getRight())
			{
				break;
			}
		}
		return hashProgress[progressIndex].getLeft();
	}

	private void progressOutputStop()
	{
		progressLock.lock();
		try
		{
			if (fileCount > PROGRESS_DISPLAY_FILE_COUNT)
			{
				System.out.println("");
			}
		}
		finally
		{
			progressLock.unlock();
		}
	}

	public Parameters getParameters()
	{
		return parameters;
	}
}
