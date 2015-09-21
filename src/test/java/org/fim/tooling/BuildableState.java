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

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

import org.fim.model.Context;
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.FileTime;
import org.fim.model.State;

public class BuildableState extends State
{
	private static final Comparator<FileState> fileNameComparator = new FileState.FileNameComparator();

	private transient final Context context;

	public BuildableState(Context context)
	{
		this.context = context;
	}

	public BuildableState addFiles(String... fileNames)
	{
		BuildableState newState = clone();
		for (String fileName : fileNames)
		{
			if (findFileState(newState, fileName, false) != null)
			{
				throw new IllegalArgumentException("New file: Duplicate fileName " + fileName);
			}

			// By default put the fileName as fileContent that will be the hash
			FileState fileState = new FileState(fileName, fileName.length(), new FileTime(getNow()), createHash(fileName));
			newState.getFileStates().add(fileState);
		}
		sortFileStates(newState);
		return newState;
	}

	public BuildableState copy(String sourceFileName, String targetFileName)
	{
		BuildableState newState = clone();
		if (findFileState(newState, targetFileName, false) != null)
		{
			throw new IllegalArgumentException("Copy: File already exist " + targetFileName);
		}

		FileState sourceFileState = findFileState(newState, sourceFileName, true);
		FileState targetFileState = new FileState(targetFileName, sourceFileState.getFileLength(), new FileTime(sourceFileState.getFileTime()), new FileHash(sourceFileState.getFileHash()));
		newState.getFileStates().add(targetFileState);
		sortFileStates(newState);
		return newState;
	}

	public BuildableState rename(String sourceFileName, String targetFileName)
	{
		BuildableState newState = clone();
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
		BuildableState newState = clone();
		FileState fileState = findFileState(newState, fileName, true);
		newState.getFileStates().remove(fileState);
		return newState;
	}

	public BuildableState touch(String fileName)
	{
		BuildableState newState = clone();
		FileState fileState = findFileState(newState, fileName, true);
		long now = getNow();
		if (now <= fileState.getFileTime().getLastModified())
		{
			now = fileState.getFileTime().getLastModified() + 1;
		}
		fileState.getFileTime().setLastModified(now);
		return newState;
	}

	public BuildableState setContent(String fileName, String fileContent)
	{
		BuildableState newState = clone();
		FileState fileState = findFileState(newState, fileName, true);
		fileState.setFileHash(createHash(fileContent));
		return newState;
	}

	public BuildableState appendContent(String fileName, String fileContent)
	{
		BuildableState newState = clone();
		FileState fileState = findFileState(newState, fileName, true);
		fileState.setFileHash(appendHash(fileState.getFileHash(), fileContent));
		return newState;
	}

	private FileHash createHash(String content)
	{
		String smallBlockHash = "small_block_" + content;
		String mediumBlockHash = "medium_block_" + content;
		String fullHash = "full_" + content;

		switch (context.getHashMode())
		{
			case dontHash:
				smallBlockHash = FileState.NO_HASH;
				mediumBlockHash = FileState.NO_HASH;
				fullHash = FileState.NO_HASH;
				break;

			case hashSmallBlock:
				mediumBlockHash = FileState.NO_HASH;
				fullHash = FileState.NO_HASH;
				break;

			case hashMediumBlock:
				fullHash = FileState.NO_HASH;
				break;

			case hashAll:
				// Nothing to do
				break;
		}

		return new FileHash(smallBlockHash, mediumBlockHash, fullHash);
	}

	private FileHash appendHash(FileHash fileHash, String content)
	{
		String smallBlockHash = fileHash.getSmallBlockHash() + "_" + content;
		String mediumBlockHash = fileHash.getMediumBlockHash() + "_" + content;
		String fullHash = fileHash.getFullHash() + "_" + content;

		switch (context.getHashMode())
		{
			case dontHash:
				smallBlockHash = FileState.NO_HASH;
				mediumBlockHash = FileState.NO_HASH;
				fullHash = FileState.NO_HASH;
				break;

			case hashSmallBlock:
				mediumBlockHash = FileState.NO_HASH;
				fullHash = FileState.NO_HASH;
				break;

			case hashMediumBlock:
				fullHash = FileState.NO_HASH;
				break;

			case hashAll:
				// Nothing to do
				break;
		}

		return new FileHash(smallBlockHash, mediumBlockHash, fullHash);
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

	@Override
	public BuildableState clone()
	{
		return (BuildableState) super.clone();
	}
}
