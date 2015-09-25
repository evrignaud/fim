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

import static org.fim.internal.HashProgress.PROGRESS_DISPLAY_FILE_COUNT;
import static org.fim.model.HashMode.dontHash;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.fim.model.Context;
import org.fim.model.FileState;
import org.fim.model.HashMode;
import org.fim.model.State;
import org.fim.util.Console;
import org.fim.util.FileUtil;
import org.fim.util.Logger;

public class StateGenerator
{
	public static final int FILES_QUEUE_CAPACITY = 500;

	private static Comparator<FileState> fileNameComparator = new FileState.FileNameComparator();

	private final Context context;
	private final HashProgress hashProgress;
	private final FimIgnoreManager fimIgnoreManager;

	private ExecutorService executorService;
	private long totalFileContentLength;

	private Path rootDir;
	private BlockingDeque<Path> filesToHashQueue;
	private boolean hashersStarted;
	private List<FileHasher> hashers;
	private long totalBytesHashed;

	public StateGenerator(Context context)
	{
		this.context = context;
		this.hashProgress = new HashProgress(context);
		this.fimIgnoreManager = new FimIgnoreManager(context);
	}

	public static String hashModeToString(HashMode hashMode)
	{
		switch (hashMode)
		{
			case dontHash:
				return "retrieve only file attributes";

			case hashSmallBlock:
				return "hash second 4 KB block";

			case hashMediumBlock:
				return "hash second 1 MB block";

			case hashAll:
				return "hash the complete file";
		}

		throw new IllegalArgumentException("Invalid hash mode " + hashMode);
	}

	public State generateState(String comment, Path rootDir, Path dirToScan) throws NoSuchAlgorithmException
	{
		this.rootDir = rootDir;

		Logger.info(String.format("Scanning recursively local files, %s, using %d thread", hashModeToString(context.getHashMode()), context.getThreadCount()));
		if (hashProgress.isProgressDisplayed())
		{
			System.out.printf("(Hash progress legend for files grouped %d by %d: %s)%n", PROGRESS_DISPLAY_FILE_COUNT, PROGRESS_DISPLAY_FILE_COUNT, hashProgress.hashProgressLegend());
		}

		State state = new State();
		state.setComment(comment);
		state.setHashMode(context.getHashMode());

		long start = System.currentTimeMillis();
		hashProgress.progressOutputInit();

		filesToHashQueue = new LinkedBlockingDeque<>(FILES_QUEUE_CAPACITY);
		initializeFileHashers();

		Path userDir = Paths.get(System.getProperty("user.dir"));
		List<FileToIgnore> globalIgnore = fimIgnoreManager.loadFimIgnore(userDir);
		scanFileTree(filesToHashQueue, dirToScan, globalIgnore);

		// In case the FileHashers have not already been started
		startFileHashers();

		waitAllFilesToBeHashed();

		for (FileHasher hasher : hashers)
		{
			state.getFileStates().addAll(hasher.getFileStates());
			totalFileContentLength += hasher.getTotalFileContentLength();
			totalBytesHashed += hasher.getTotalBytesHashed();
		}

		Collections.sort(state.getFileStates(), fileNameComparator);

		state.setIgnoredFiles(fimIgnoreManager.getIgnoredFiles());

		hashProgress.progressOutputStop();
		displayStatistics(start, state);

		return state;
	}

	private void initializeFileHashers()
	{
		hashersStarted = false;
		hashers = new ArrayList<>();
		executorService = Executors.newFixedThreadPool(context.getThreadCount());
	}

	private void startFileHashers() throws NoSuchAlgorithmException
	{
		if (!hashersStarted)
		{
			String normalizedRootDir = FileUtil.getNormalizedFileName(rootDir);
			for (int index = 0; index < context.getThreadCount(); index++)
			{
				FileHasher hasher = new FileHasher(hashProgress, filesToHashQueue, normalizedRootDir);
				executorService.submit(hasher);
				hashers.add(hasher);
			}
			hashersStarted = true;
		}
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
		if (durationSeconds <= 0)
		{
			durationSeconds = 1;
		}

		long globalThroughput = totalBytesHashed / durationSeconds;
		String throughputStr = FileUtils.byteCountToDisplaySize(globalThroughput);

		if (context.getHashMode() == dontHash)
		{
			Logger.info(String.format("Scanned %d files (%s), during %s, using %d thread%n",
					state.getFileStates().size(), totalFileContentLengthStr, durationStr, context.getThreadCount()));
		}
		else
		{
			Logger.info(String.format("Scanned %d files (%s), hashed %s (avg %s/s), during %s, using %d thread%n",
					state.getFileStates().size(), totalFileContentLengthStr, totalBytesHashedStr, throughputStr, durationStr, context.getThreadCount()));
		}
	}

	private void scanFileTree(BlockingDeque<Path> filesToHashQueue, Path directory, List<FileToIgnore> thisDirectoryIgnoreList) throws NoSuchAlgorithmException
	{
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory))
		{
			List<FileToIgnore> currentIgnoreList = fimIgnoreManager.loadFimIgnore(directory);
			List<FileToIgnore> subDirectoriesIgnoreList = fimIgnoreManager.buildSubDirectoriesIgnoreList(thisDirectoryIgnoreList, currentIgnoreList);
			currentIgnoreList.addAll(subDirectoriesIgnoreList);

			for (Path file : stream)
			{
				if (!hashersStarted && filesToHashQueue.size() > FILES_QUEUE_CAPACITY / 2)
				{
					startFileHashers();
				}

				BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
				if (fimIgnoreManager.isIgnored(file, attributes, currentIgnoreList))
				{
					fimIgnoreManager.addToIgnoredFiles(file, attributes);
				}
				else
				{
					if (attributes.isRegularFile())
					{
						enqueueFile(filesToHashQueue, file);
					}
					else if (attributes.isDirectory())
					{
						scanFileTree(filesToHashQueue, file, subDirectoriesIgnoreList);
					}
				}
			}
		}
		catch (IOException ex)
		{
			Console.newLine();
			Logger.error("Skipping - Error scanning directory", ex);
		}
	}

	private void enqueueFile(BlockingDeque<Path> filesToHashQueue, Path file)
	{
		try
		{
			filesToHashQueue.offer(file, 120, TimeUnit.MINUTES);
		}
		catch (InterruptedException ex)
		{
			Logger.error(ex);
		}
	}
}
