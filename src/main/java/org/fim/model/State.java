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

import static org.fim.model.HashMode.hashAll;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.google.common.base.Charsets;
import com.google.common.base.MoreObjects;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rits.cloning.Cloner;
import org.fim.util.FileUtil;
import org.fim.util.Logger;

public class State implements Hashable
{
	public static final String CURRENT_MODEL_VERSION = "4";

	private static final Cloner CLONER = new Cloner();
	private static Comparator<FileState> fileNameComparator = new FileState.FileNameComparator();

	private String stateHash; // Ensure the integrity of the complete State content

	private String modelVersion;
	private long timestamp;
	private String comment;
	private int fileCount;
	private HashMode hashMode;

	private ModificationCounts modificationCounts; // Not taken in account in equals(), hashCode(), hashObject()
	private Set<String> ignoredFiles;
	private List<FileState> fileStates;

	public State()
	{
		modelVersion = CURRENT_MODEL_VERSION;
		timestamp = System.currentTimeMillis();
		comment = "";
		fileCount = 0;
		hashMode = hashAll;
		modificationCounts = new ModificationCounts();
		ignoredFiles = new HashSet<>();
		fileStates = new ArrayList<>();
	}

	public static State loadFromGZipFile(Path stateFile) throws IOException, CorruptedStateException
	{
		try (Reader reader = new InputStreamReader(new GZIPInputStream(new FileInputStream(stateFile.toFile()))))
		{
			Gson gson = new Gson();
			State state = gson.fromJson(reader, State.class);

			if (!CURRENT_MODEL_VERSION.equals(state.getModelVersion()))
			{
				Logger.warning(String.format("State %s use a different model version. Some features will not work completely.", stateFile.getFileName().toString()));
			}
			else
			{
				checkIntegrity(state);
			}
			return state;
		}
	}

	private static void checkIntegrity(State state) throws CorruptedStateException
	{
		String hash = state.hashState();
		if (!state.stateHash.equals(hash))
		{
			throw new CorruptedStateException();
		}
	}

	public void saveToGZipFile(Path stateFile) throws IOException
	{
		Collections.sort(fileStates, fileNameComparator);

		updateFileCount();
		stateHash = hashState();

		try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(stateFile.toFile()))))
		{
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			gson.toJson(this, writer);
		}
	}

	public State filterDirectory(Path repositoryRootDir, Path currentDirectory, boolean keepFilesInside)
	{
		State filteredState = clone();

		String rootDir = FileUtil.getNormalizedFileName(repositoryRootDir);
		String curDir = FileUtil.getNormalizedFileName(currentDirectory);
		String subDirectory = FileUtil.getRelativeFileName(rootDir, curDir);

		Iterator<FileState> iterator = filteredState.getFileStates().iterator();
		while (iterator.hasNext())
		{
			FileState fileState = iterator.next();
			if (fileState.getFileName().startsWith(subDirectory) != keepFilesInside)
			{
				iterator.remove();
			}
		}

		return filteredState;
	}

	public void updateFileCount()
	{
		fileCount = fileStates.size();
	}

	public String getModelVersion()
	{
		return modelVersion;
	}

	public void setModelVersion(String modelVersion)
	{
		this.modelVersion = modelVersion;
	}

	public long getTimestamp()
	{
		return timestamp;
	}

	protected void setTimestamp(long timestamp)
	{
		this.timestamp = timestamp;
	}

	public String getComment()
	{
		return comment;
	}

	public void setComment(String comment)
	{
		this.comment = comment;
	}

	public int getFileCount()
	{
		updateFileCount();
		return fileCount;
	}

	protected void setFileCount(int fileCount)
	{
		// This value is computed dynamically when getFileCount() is called
	}

	public HashMode getHashMode()
	{
		return hashMode;
	}

	public void setHashMode(HashMode hashMode)
	{
		this.hashMode = hashMode;
	}

	public Set<String> getIgnoredFiles()
	{
		return ignoredFiles;
	}

	public void setIgnoredFiles(Set<String> ignoredFiles)
	{
		this.ignoredFiles = ignoredFiles;
	}

	public List<FileState> getFileStates()
	{
		return fileStates;
	}

	public void setFileStates(List<FileState> fileStates)
	{
		this.fileStates = fileStates;
	}

	public ModificationCounts getModificationCounts()
	{
		return modificationCounts;
	}

	public void setModificationCounts(ModificationCounts modificationCounts)
	{
		this.modificationCounts = modificationCounts;
	}

	public String hashState()
	{
		HashFunction hashFunction = Hashing.sha512();
		Hasher hasher = hashFunction.newHasher(FileState.SIZE_10_MB);
		hashObject(hasher);
		HashCode hash = hasher.hash();
		return hash.toString();
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}

		if (other == null || !(other instanceof State))
		{
			return false;
		}

		State state = (State) other;

		return Objects.equals(this.modelVersion, state.modelVersion)
				&& Objects.equals(this.timestamp, state.timestamp)
				&& Objects.equals(this.comment, state.comment)
				&& Objects.equals(this.fileCount, state.fileCount)
				&& Objects.equals(this.hashMode, state.hashMode)
				&& Objects.equals(this.ignoredFiles, state.ignoredFiles)
				&& Objects.equals(this.fileStates, state.fileStates);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(modelVersion, timestamp, comment, fileCount, hashMode, ignoredFiles, fileStates);
	}

	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
				.add("modelVersion", modelVersion)
				.add("timestamp", timestamp)
				.add("comment", comment)
				.add("fileCount", fileCount)
				.add("hashMode", hashMode)
				.add("ignoredFiles", ignoredFiles)
				.add("fileStates", fileStates)
				.toString();
	}

	@Override
	public void hashObject(Hasher hasher)
	{
		hasher
				.putString("State", Charsets.UTF_8)
				.putChar(HASH_FIELD_SEPARATOR)
				.putString(modelVersion, Charsets.UTF_8)
				.putChar(HASH_FIELD_SEPARATOR)
				.putLong(timestamp)
				.putChar(HASH_FIELD_SEPARATOR)
				.putString(comment, Charsets.UTF_8)
				.putChar(HASH_FIELD_SEPARATOR)
				.putInt(fileCount)
				.putChar(HASH_FIELD_SEPARATOR)
				.putString(hashMode.name(), Charsets.UTF_8);

		hasher.putChar(HASH_OBJECT_SEPARATOR);
		for (String ignoredFile : ignoredFiles)
		{
			hasher
					.putString(ignoredFile, Charsets.UTF_8)
					.putChar(HASH_OBJECT_SEPARATOR);
		}

		hasher.putChar(HASH_OBJECT_SEPARATOR);
		for (FileState fileState : fileStates)
		{
			fileState.hashObject(hasher);
			hasher.putChar(HASH_OBJECT_SEPARATOR);
		}
	}

	public State clone()
	{
		return CLONER.deepClone(this);
	}
}
