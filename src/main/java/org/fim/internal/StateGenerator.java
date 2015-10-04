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

import static org.fim.internal.hash.HashProgress.PROGRESS_DISPLAY_FILE_COUNT;
import static org.fim.model.HashMode.dontHash;
import static org.fim.util.HashModeUtil.hashModeToString;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
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
import org.atteo.evo.inflector.English;
import org.fim.internal.hash.FileHasher;
import org.fim.internal.hash.HashProgress;
import org.fim.model.Context;
import org.fim.model.FileState;
import org.fim.model.FimIgnore;
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
	private boolean fileHashersStarted;
	private List<FileHasher> fileHashers;
	private long overallTotalBytesHashed;

	public StateGenerator(Context context)
	{
		this.context = context;
		this.hashProgress = new HashProgress(context);
		this.fimIgnoreManager = new FimIgnoreManager(context);
	}

	public State generateState(String comment, Path rootDir, Path dirToScan) throws NoSuchAlgorithmException
	{
		this.rootDir = rootDir;

		int threadCount = context.getThreadCount();
		Logger.info(String.format("Scanning recursively local files, using '%s' mode and %d %s",
				hashModeToString(context.getHashMode()), threadCount, English.plural("thread", threadCount)));
		if (hashProgress.isProgressDisplayed())
		{
			System.out.printf("(Hash progress legend for files grouped %d by %d: %s)%n", PROGRESS_DISPLAY_FILE_COUNT, PROGRESS_DISPLAY_FILE_COUNT, hashProgress.hashLegend());
		}

		State state = new State();
		state.setComment(comment);
		state.setHashMode(context.getHashMode());

		long start = System.currentTimeMillis();
		hashProgress.outputInit();

		filesToHashQueue = new LinkedBlockingDeque<>(FILES_QUEUE_CAPACITY);
		initializeFileHashers();

		FimIgnore initialFimIgnore = fimIgnoreManager.loadInitialFimIgnore();
		scanFileTree(filesToHashQueue, dirToScan, initialFimIgnore);

		// In case the FileHashers have not already been started
		startFileHashers();

		waitAllFilesToBeHashed();

		overallTotalBytesHashed = 0;
		for (FileHasher fileHasher : fileHashers)
		{
			state.getFileStates().addAll(fileHasher.getFileStates());
			totalFileContentLength += fileHasher.getTotalFileContentLength();
			overallTotalBytesHashed += fileHasher.getHashers().getTotalBytesHashed();
		}

		Collections.sort(state.getFileStates(), fileNameComparator);

		state.setIgnoredFiles(fimIgnoreManager.getIgnoredFiles());

		hashProgress.outputStop();
		displayStatistics(start, state);

		return state;
	}

	private void initializeFileHashers()
	{
		fileHashersStarted = false;
		fileHashers = new ArrayList<>();
		executorService = Executors.newFixedThreadPool(context.getThreadCount());
	}

	private void startFileHashers() throws NoSuchAlgorithmException
	{
		if (!fileHashersStarted)
		{
			String normalizedRootDir = FileUtil.getNormalizedFileName(rootDir);
			for (int index = 0; index < context.getThreadCount(); index++)
			{
				FileHasher hasher = new FileHasher(hashProgress, filesToHashQueue, normalizedRootDir);
				executorService.submit(hasher);
				fileHashers.add(hasher);
			}
			fileHashersStarted = true;
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
		String totalBytesHashedStr = FileUtils.byteCountToDisplaySize(overallTotalBytesHashed);
		String durationStr = DurationFormatUtils.formatDuration(duration, "HH:mm:ss");

		long durationSeconds = duration / 1000;
		if (durationSeconds <= 0)
		{
			durationSeconds = 1;
		}

		long globalThroughput = overallTotalBytesHashed / durationSeconds;
		String throughputStr = FileUtils.byteCountToDisplaySize(globalThroughput);

		if (context.getHashMode() == dontHash)
		{
			Logger.info(String.format("Scanned %d files (%s), during %s%n",
					state.getFileStates().size(), totalFileContentLengthStr, durationStr));
		}
		else
		{
			Logger.info(String.format("Scanned %d files (%s), hashed %s (avg %s/s), during %s%n",
					state.getFileStates().size(), totalFileContentLengthStr, totalBytesHashedStr, throughputStr, durationStr));
		}
	}

	private void scanFileTree(BlockingDeque<Path> filesToHashQueue, Path directory, FimIgnore parentFimIgnore) throws NoSuchAlgorithmException
	{
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory))
		{
			FimIgnore fimIgnore = fimIgnoreManager.loadLocalIgnore(directory, parentFimIgnore);

			for (Path file : stream)
			{
				if (!fileHashersStarted && filesToHashQueue.size() > FILES_QUEUE_CAPACITY / 2)
				{
					startFileHashers();
				}

				BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
				if (fimIgnoreManager.isIgnored(file, attributes, fimIgnore))
				{
					fimIgnoreManager.ignoreThisFiles(file, attributes);
				}
				else
				{
					if (attributes.isRegularFile())
					{
						enqueueFile(filesToHashQueue, file);
					}
					else if (attributes.isDirectory())
					{
						scanFileTree(filesToHashQueue, file, fimIgnore);
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
