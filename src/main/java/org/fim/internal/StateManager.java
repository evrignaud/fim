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

import static java.nio.file.StandardOpenOption.CREATE;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;

import org.fim.model.FileState;
import org.fim.model.Parameters;
import org.fim.model.State;
import org.fim.util.Logger;

public class StateManager
{
	public static final String STATE_EXTENSION = ".json.gz";

	public static final String LAST_STATE_FILE_NAME = "lastState";

	private final Parameters parameters;
	private final Charset utf8 = Charset.forName("UTF-8");
	private final File stateDir;

	public StateManager(Parameters parameters)
	{
		this(parameters, parameters.getDefaultStateDir());
	}

	public StateManager(Parameters parameters, File stateDir)
	{
		this.parameters = parameters;
		this.stateDir = stateDir;
	}

	public void createNewState(State state) throws IOException
	{
		int lastStateNumber = getLastStateNumber();
		lastStateNumber++;
		state.saveToGZipFile(getStateFile(lastStateNumber));
		saveLastStateNumber(lastStateNumber);
	}

	public State loadLastState() throws IOException
	{
		int lastStateNumber = getLastStateNumber();
		return loadState(lastStateNumber);
	}

	public State loadState(int stateNumber) throws IOException
	{
		File stateFile = getStateFile(stateNumber);
		if (!stateFile.exists())
		{
			throw new IllegalStateException(String.format("Unable to load State file %d from directory %s", stateNumber, stateDir));
		}

		State state = State.loadFromGZipFile(stateFile);

		adjustAccordingToHashMode(state);

		return state;
	}

	private void adjustAccordingToHashMode(State state)
	{
		// Replace by 'no_hash' accurately to be able to compare the FileState entry
		switch (parameters.getHashMode())
		{
			case DONT_HASH_FILES:
				for (FileState fileState : state.getFileStates())
				{
					fileState.getFileHash().setFirstFourKiloHash(FileState.NO_HASH);
					fileState.getFileHash().setFirstMegaHash(FileState.NO_HASH);
					fileState.getFileHash().setFullHash(FileState.NO_HASH);
				}
				break;

			case HASH_ONLY_FIRST_FOUR_KILO:
				for (FileState fileState : state.getFileStates())
				{
					fileState.getFileHash().setFirstMegaHash(FileState.NO_HASH);
					fileState.getFileHash().setFullHash(FileState.NO_HASH);
				}
				break;

			case HASH_ONLY_FIRST_MEGA:
				for (FileState fileState : state.getFileStates())
				{
					fileState.getFileHash().setFirstFourKiloHash(FileState.NO_HASH);
					fileState.getFileHash().setFullHash(FileState.NO_HASH);
				}
				break;

			case COMPUTE_ALL_HASH:
				// Nothing to do
				break;
		}
	}

	/**
	 * @return the State file formatted like this: <stateDir>/state_<stateNumber>.json.gz
	 */
	public File getStateFile(int stateNumber)
	{
		StringBuilder builder = new StringBuilder();
		builder.append("state_").append(stateNumber).append(STATE_EXTENSION);
		return new File(stateDir, builder.toString());
	}

	public int getLastStateNumber()
	{
		boolean lastStateFileDesynchronized = false;
		File lastStateFile = new File(stateDir, LAST_STATE_FILE_NAME);
		if (lastStateFile.exists())
		{
			try
			{
				List<String> strings = Files.readAllLines(lastStateFile.toPath(), utf8);
				if (strings.size() > 0)
				{
					int number = Integer.parseInt(strings.get(0));
					File statFile = getStateFile(number);
					if (statFile.exists())
					{
						return number;
					}
				}
			}
			catch (IOException ex)
			{
				ex.printStackTrace();
			}

			lastStateFileDesynchronized = true;
		}

		for (int index = 1; ; index++)
		{
			File statFile = getStateFile(index);
			if (!statFile.exists())
			{
				int number = index - 1;
				if (lastStateFileDesynchronized)
				{
					Logger.error(String.format("'%s' file desynchronized. Resetting it to %d.", lastStateFile, number));
					saveLastStateNumber(number);
				}
				return number;
			}
		}
	}

	private void saveLastStateNumber(int lastStateNumber)
	{
		if (lastStateNumber != -1)
		{
			File lastStateFile = new File(stateDir, LAST_STATE_FILE_NAME);
			String content = Integer.toString(lastStateNumber);
			try
			{
				Files.write(lastStateFile.toPath(), content.getBytes(), CREATE);
			}
			catch (IOException ex)
			{
				ex.printStackTrace();
			}
		}
	}
}
