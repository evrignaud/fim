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
package org.fim.model;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.fim.util.Logger;

public class Settings
{
	public static final String SETTINGS_FILE = "settings.json";

	private HashMode globalHashMode = HashMode.computeAllHash;
	private int lastStateNumber = 0;

	private transient Path stateDir;

	public Settings(Path stateDir)
	{
		this.stateDir = stateDir;
	}

	public boolean isSaved()
	{
		Path settingsFile = stateDir.resolve(SETTINGS_FILE);
		return Files.exists(settingsFile);
	}

	public void load()
	{
		Settings settings;
		Path settingsFile = stateDir.resolve(SETTINGS_FILE);
		try (Reader reader = new InputStreamReader(new FileInputStream(settingsFile.toFile())))
		{
			Gson gson = new Gson();
			settings = gson.fromJson(reader, Settings.class);

			globalHashMode = settings.globalHashMode;
			lastStateNumber = settings.lastStateNumber;
		}
		catch (IOException ex)
		{
			Logger.error("Error reading settings", ex);
		}
	}

	public void save()
	{
		Path settingsFile = stateDir.resolve(SETTINGS_FILE);
		try (Writer writer = new OutputStreamWriter(new FileOutputStream(settingsFile.toFile())))
		{
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			gson.toJson(this, writer);
		}
		catch (IOException ex)
		{
			Logger.error("Error saving settings", ex);
		}
	}

	public HashMode getGlobalHashMode()
	{
		return globalHashMode;
	}

	public void setGlobalHashMode(HashMode globalHashMode)
	{
		this.globalHashMode = globalHashMode;
	}

	public int getLastStateNumber()
	{
		return lastStateNumber;
	}

	public void setLastStateNumber(int lastStateNumber)
	{
		this.lastStateNumber = lastStateNumber;
	}
}
