/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2017  Etienne Vrignaud
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
 * along with Fim.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.fim.internal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.fim.command.exception.RepositoryException;
import org.fim.model.Context;
import org.fim.model.HashMode;
import org.fim.model.Settings;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class SettingsManager {
    public static final String SETTINGS_FILE = "settings.json";

    private final Path settingsFile;
    private Settings settings;

    public SettingsManager(Context context) {
        settingsFile = context.getRepositoryDotFimDir().resolve(SETTINGS_FILE);
        settings = new Settings();

        if (isCreated()) {
            load();
        }
    }

    public Path getSettingsFile() {
        return settingsFile;
    }

    public boolean isCreated() {
        return Files.exists(settingsFile);
    }

    private void load() {
        try (Reader reader = new InputStreamReader(new FileInputStream(settingsFile.toFile()))) {
            Gson gson = new Gson();
            settings = gson.fromJson(reader, Settings.class);
        } catch (IOException ex) {
            throw new RepositoryException("Error reading settings", ex);
        }
    }

    public void save() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(settingsFile.toFile()))) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(settings, writer);
        } catch (IOException ex) {
            throw new RepositoryException("Error saving settings", ex);
        }
    }

    public HashMode getGlobalHashMode() {
        return settings.getGlobalHashMode();
    }

    public void setGlobalHashMode(HashMode globalHashMode) {
        settings.setGlobalHashMode(globalHashMode);
    }

    public int getLastStateNumber() {
        return settings.getLastStateNumber();
    }

    public void setLastStateNumber(int lastStateNumber) {
        settings.setLastStateNumber(lastStateNumber);
    }
}
