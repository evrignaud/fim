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

import java.util.Comparator;
import java.util.Objects;

import com.google.common.base.MoreObjects;

public class FileState
{
	public static final int SIZE_1_KB = 1024;
	public static final int SIZE_4_KB = 4 * SIZE_1_KB;

	public static final int SIZE_1_MB = 1024 * SIZE_1_KB;
	public static final int SIZE_10_MB = 10 * SIZE_1_MB;
	public static final int SIZE_20_MB = 20 * SIZE_1_MB;
	public static final int SIZE_50_MB = 50 * SIZE_1_MB;
	public static final int SIZE_100_MB = 100 * SIZE_1_MB;
	public static final int SIZE_200_MB = 200 * SIZE_1_MB;

	public static final int SIZE_UNLIMITED = -1;

	public static final String NO_HASH = "no_hash";

	private String fileName;
	private long fileLength;
	private long lastModified;
	private FileHash fileHash;

	private transient FileHash newFileHash; // Used by StateComparator to detect accurately duplicates

	public FileState(String fileName, long fileLength, long lastModified, FileHash fileHash)
	{
		if (fileName == null)
		{
			throw new IllegalArgumentException("Invalid null fileName");
		}
		if (fileHash == null)
		{
			throw new IllegalArgumentException("Invalid null hash");
		}

		this.setFileName(fileName);
		this.setFileLength(fileLength);
		this.setLastModified(lastModified);
		this.setFileHash(fileHash);
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}

		if (other == null || !(other instanceof FileState))
		{
			return false;
		}

		FileState otherFileState = (FileState) other;

		return Objects.equals(this.fileName, otherFileState.fileName)
				&& Objects.equals(this.fileLength, otherFileState.fileLength)
				&& Objects.equals(this.lastModified, otherFileState.lastModified)
				&& Objects.equals(this.fileHash, otherFileState.fileHash);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(fileName, fileLength, lastModified, fileHash);
	}

	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
				.add("fileName", fileName)
				.add("fileLength", fileLength)
				.add("lastModified", lastModified)
				.add("fileHash", fileHash)
				.add("newFileHash", newFileHash)
				.toString();
	}

	public String getFileName()
	{
		return fileName;
	}

	public void setFileName(String fileName)
	{
		this.fileName = fileName;
	}

	public long getFileLength()
	{
		return fileLength;
	}

	public void setFileLength(long fileLength)
	{
		this.fileLength = fileLength;
	}

	public long getLastModified()
	{
		return lastModified;
	}

	public void setLastModified(long lastModified)
	{
		this.lastModified = lastModified;
	}

	public FileHash getFileHash()
	{
		return fileHash;
	}

	public void setFileHash(FileHash fileHash)
	{
		this.fileHash = fileHash;
	}

	public FileHash getNewFileHash()
	{
		return newFileHash;
	}

	public void setNewFileHash(FileHash newFileHash)
	{
		this.newFileHash = newFileHash;
	}

	public void resetNewHash()
	{
		newFileHash = fileHash;
	}

	public static class FileNameComparator implements Comparator<FileState>
	{
		@Override
		public int compare(FileState fs1, FileState fs2)
		{
			return fs1.getFileName().compareTo(fs2.getFileName());
		}
	}

	public static class HashComparator implements Comparator<FileState>
	{
		@Override
		public int compare(FileState fs1, FileState fs2)
		{
			return fs1.getFileHash().compareTo(fs2.getFileHash());
		}
	}
}
