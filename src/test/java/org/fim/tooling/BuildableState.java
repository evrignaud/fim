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
package org.fim.tooling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.Parameters;
import org.fim.model.State;

public class BuildableState extends State
{
	private static Comparator<FileState> fileNameComparator = new FileState.FileNameComparator();

	private transient final Parameters parameters;

	public BuildableState(Parameters parameters)
	{
		this.parameters = parameters;
	}

	public BuildableState addFiles(String... fileNames)
	{
		BuildableState newState = cloneState();
		for (String fileName : fileNames)
		{
			if (findFileState(newState, fileName, false) != null)
			{
				throw new IllegalArgumentException("New file: Duplicate fileName " + fileName);
			}

			// By default put the fileName as fileContent that will be the hash
			FileState fileState = new FileState(fileName, fileName.length(), getNow(), createHash(fileName));
			newState.getFileStates().add(fileState);
		}
		sortFileStates(newState);
		return newState;
	}

	public BuildableState copy(String sourceFileName, String targetFileName)
	{
		BuildableState newState = cloneState();
		if (findFileState(newState, targetFileName, false) != null)
		{
			throw new IllegalArgumentException("Copy: File already exist " + targetFileName);
		}

		FileState sourceFileState = findFileState(newState, sourceFileName, true);
		FileState targetFileState = new FileState(targetFileName, sourceFileState.getFileLength(), sourceFileState.getLastModified(), new FileHash(sourceFileState.getFileHash()));
		newState.getFileStates().add(targetFileState);
		sortFileStates(newState);
		return newState;
	}

	public BuildableState rename(String sourceFileName, String targetFileName)
	{
		BuildableState newState = cloneState();
		if (findFileState(newState, targetFileName, false) != null)
		{
			throw new IllegalArgumentException("Rename: File already exist " + targetFileName);
		}

		FileState fileState = findFileState(newState, sourceFileName, true);
		fileState.setFileName(targetFileName);
		sortFileStates(newState);
		return newState;
	}

	public BuildableState delete(String fileName)
	{
		BuildableState newState = cloneState();
		FileState fileState = findFileState(newState, fileName, true);
		newState.getFileStates().remove(fileState);
		return newState;
	}

	public BuildableState touch(String fileName)
	{
		BuildableState newState = cloneState();
		FileState fileState = findFileState(newState, fileName, true);
		long now = getNow();
		if (now <= fileState.getLastModified())
		{
			now = fileState.getLastModified() + 1;
		}
		fileState.setLastModified(now);
		return newState;
	}

	public BuildableState setContent(String fileName, String fileContent)
	{
		BuildableState newState = cloneState();
		FileState fileState = findFileState(newState, fileName, true);
		fileState.setFileHash(createHash(fileContent));
		return newState;
	}

	public BuildableState appendContent(String fileName, String fileContent)
	{
		BuildableState newState = cloneState();
		FileState fileState = findFileState(newState, fileName, true);
		fileState.setFileHash(appendHash(fileState.getFileHash(), fileContent));
		return newState;
	}

	public BuildableState cloneState()
	{
		BuildableState newState = new BuildableState(parameters);
		newState.setComment(getComment());

		ArrayList<FileState> newFileStates = new ArrayList<>();
		for (FileState fileState : getFileStates())
		{
			FileState newFileState = new FileState(fileState.getFileName(), fileState.getFileLength(), fileState.getLastModified(), new FileHash(fileState.getFileHash()));
			newFileStates.add(newFileState);
		}
		newState.setFileStates(newFileStates);
		sortFileStates(newState);

		return newState;
	}

	private FileHash createHash(String content)
	{
		String firstFourKiloHash = FileState.NO_HASH;
		String firstMegaHash = FileState.NO_HASH;
		String fullHash = FileState.NO_HASH;

		switch (parameters.getHashMode())
		{
			case DONT_HASH_FILES:
				// Nothing to do
				break;

			case HASH_ONLY_FIRST_FOUR_KILO:
				firstFourKiloHash = "first_four_kilo_" + content;
				break;

			case HASH_ONLY_FIRST_MEGA:
				firstMegaHash = "first_mega_" + content;
				break;

			case COMPUTE_ALL_HASH:
				firstFourKiloHash = "first_four_kilo_" + content;
				firstMegaHash = "first_mega_" + content;
				fullHash = "full_" + content;
				break;
		}

		return new FileHash(firstFourKiloHash, firstMegaHash, fullHash);
	}

	private FileHash appendHash(FileHash fileHash, String content)
	{
		String firstFourKiloHash = FileState.NO_HASH;
		String firstMegaHash = FileState.NO_HASH;
		String fullHash = FileState.NO_HASH;

		switch (parameters.getHashMode())
		{
			case DONT_HASH_FILES:
				// Nothing to do
				break;

			case HASH_ONLY_FIRST_FOUR_KILO:
				firstFourKiloHash = fileHash.getFirstFourKiloHash() + "_" + content;
				break;

			case HASH_ONLY_FIRST_MEGA:
				firstMegaHash = fileHash.getFirstMegaHash() + "_" + content;
				break;

			case COMPUTE_ALL_HASH:
				firstFourKiloHash = fileHash.getFirstFourKiloHash() + "_" + content;
				firstMegaHash = fileHash.getFirstMegaHash() + "_" + content;
				fullHash = fileHash.getFullHash() + "_" + content;
				break;
		}

		return new FileHash(firstFourKiloHash, firstMegaHash, fullHash);
	}

	private void sortFileStates(BuildableState state)
	{
		Collections.sort(state.getFileStates(), fileNameComparator);
		state.updateFileCount();
	}

	private FileState findFileState(BuildableState state, String fileName, boolean throwEx)
	{
		for (FileState fileState : state.getFileStates())
		{
			if (fileState.getFileName().equals(fileName))
			{
				return fileState;
			}
		}

		if (throwEx)
		{
			throw new IllegalArgumentException("Unknown file " + fileName);
		}
		return null;
	}

	private long getNow()
	{
		return new Date().getTime();
	}
}
