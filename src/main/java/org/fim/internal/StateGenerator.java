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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.fim.model.FileState;
import org.fim.model.Parameters;
import org.fim.model.State;
import org.fim.util.Logger;

public class StateGenerator
{
	public static final int MEGA = 1024 * 1024;
	public static final int SIZE_10_MO = 10 * MEGA;
	public static final int SIZE_20_MO = 20 * MEGA;
	public static final int SIZE_50_MO = 50 * MEGA;
	public static final int SIZE_100_MO = 100 * MEGA;
	public static final int SIZE_200_MO = 200 * MEGA;

	public static final int PROGRESS_DISPLAY_FILE_COUNT = 10;

	public static final String NO_HASH = "no_hash";

	private static Comparator<FileState> fileNameComparator = new FileState.FileNameComparator();

	private final Parameters parameters;

	private ExecutorService executorService;

	private ReentrantLock countLock = new ReentrantLock();
	private long summedFileLength;
	private int fileCount;

	public StateGenerator(Parameters parameters)
	{
		this.parameters = parameters;
	}

	public State generateState(String message, File fileTreeRootDir) throws IOException, NoSuchAlgorithmException
	{
		Logger.info(String.format("Starting to hash recursively local files using %d thread", parameters.getThreadCount()));
		System.out.printf("    ~~ Hash progress legend: x > 200Mo, l > 100Mo, m > 50Mo, s > 20Mo, : > 10Mo, . otherwise ~~%n");

		State state = new State();
		state.setMessage(message);

		long start = System.currentTimeMillis();
		progressOutputInit();

		if (parameters.getThreadCount() == 1)
		{
			state.setFileStates(new ArrayList<FileState>());
			getFileStates(state.getFileStates(), fileTreeRootDir.toString(), fileTreeRootDir);
		}
		else
		{
			executorService = Executors.newFixedThreadPool(parameters.getThreadCount());
			List<FileState> fileStates = new CopyOnWriteArrayList<>();
			getFileStates(fileStates, fileTreeRootDir.toString(), fileTreeRootDir);
			waitAllFileHashed();
			state.setFileStates(new ArrayList<>(fileStates)); // Use an ArrayList at the end, because CopyOnWriteArrayList does not support Sort.
		}

		Collections.sort(state.getFileStates(), fileNameComparator);

		progressOutputStop();
		displayTimeElapsed(start, state);

		return state;
	}

	private void waitAllFileHashed()
	{
		try
		{
			executorService.shutdown();
			executorService.awaitTermination(100, TimeUnit.DAYS);
		}
		catch (InterruptedException ex)
		{
			ex.printStackTrace();
		}
	}

	private void displayTimeElapsed(long start, State state)
	{
		long duration = System.currentTimeMillis() - start;
		long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
		long seconds = TimeUnit.MILLISECONDS.toSeconds(duration) - TimeUnit.MINUTES.toSeconds(minutes);
		if (minutes == 0)
		{
			Logger.info(String.format("Scanned %d files in %d sec using %d thread%n", state.getFileStates().size(), seconds, parameters.getThreadCount()));
		}
		else
		{
			Logger.info(String.format("Scanned %d files in %d min, %d sec using %d thread%n", state.getFileStates().size(), minutes, seconds, parameters.getThreadCount()));
		}
	}

	private void getFileStates(List<FileState> fileStates, String fileTreeRootDir, File directory) throws NoSuchAlgorithmException
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
				getFileStates(fileStates, fileTreeRootDir, file);
			}
			else
			{
				FileHasher hasher = new FileHasher(this, fileStates, fileTreeRootDir, file);
				if (parameters.getThreadCount() == 1)
				{
					hasher.run();
				}
				else
				{
					executorService.submit(hasher);
				}
			}
		}
	}

	private void progressOutputInit()
	{
		countLock.lock();
		try
		{
			summedFileLength = 0;
			fileCount = 0;
		}
		finally
		{
			countLock.unlock();
		}
	}

	public void updateProgressOutput(File file)
	{
		countLock.lock();
		try
		{
			summedFileLength += file.length();
			fileCount++;

			if (fileCount % PROGRESS_DISPLAY_FILE_COUNT == 0)
			{
				if (summedFileLength > SIZE_200_MO)
				{
					System.out.print("x");
				}
				else if (summedFileLength > SIZE_100_MO)
				{
					System.out.print("l");
				}
				else if (summedFileLength > SIZE_50_MO)
				{
					System.out.print("m");
				}
				else if (summedFileLength > SIZE_20_MO)
				{
					System.out.print("s");
				}
				else if (summedFileLength > SIZE_10_MO)
				{
					System.out.print(":");
				}
				else
				{
					System.out.print(".");
				}
				summedFileLength = 0;
			}

			if (fileCount % (100 * PROGRESS_DISPLAY_FILE_COUNT) == 0)
			{
				System.out.println("");
			}
		}
		finally
		{
			countLock.unlock();
		}
	}

	private void progressOutputStop()
	{
		countLock.lock();
		try
		{
			if (fileCount > PROGRESS_DISPLAY_FILE_COUNT)
			{
				System.out.println("");
			}
		}
		finally
		{
			countLock.unlock();
		}
	}

	public Parameters getParameters()
	{
		return parameters;
	}
}
