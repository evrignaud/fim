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

import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.Objects;

import com.google.common.base.Charsets;
import com.google.common.base.MoreObjects;
import com.google.common.hash.Hasher;

public class FileState implements Hashable
{
	private String fileName;
	private long fileLength;
	private FileTime fileTime;
	private Modification modification;
	private FileHash fileHash;

	private transient FileHash newFileHash; // Used by StateComparator to detect accurately duplicates

	public FileState(String fileName, long fileLength, FileTime fileTime, FileHash fileHash)
	{
		if (fileName == null)
		{
			throw new IllegalArgumentException("Invalid null fileName");
		}
		if (fileHash == null)
		{
			throw new IllegalArgumentException("Invalid null hash");
		}

		setFileName(fileName);
		setFileLength(fileLength);
		setFileTime(fileTime);
		setFileHash(fileHash);
	}

	public FileState(String fileName, BasicFileAttributes attributes, FileHash fileHash)
	{
		this(fileName, attributes.size(), new FileTime(attributes), fileHash);
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

	public FileTime getFileTime()
	{
		return fileTime;
	}

	public void setFileTime(FileTime fileTime)
	{
		this.fileTime = fileTime;
	}

	public Modification getModification()
	{
		return modification;
	}

	public void setModification(Modification modification)
	{
		this.modification = modification;
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
				&& Objects.equals(this.fileTime, otherFileState.fileTime)
				&& Objects.equals(this.fileHash, otherFileState.fileHash);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(fileName, fileLength, fileTime, fileHash);
	}

	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
				.add("fileName", fileName)
				.add("fileLength", fileLength)
				.add("fileTime", fileTime)
				.add("fileHash", fileHash)
				.add("newFileHash", newFileHash)
				.toString();
	}

	@Override
	public void hashObject(Hasher hasher)
	{
		hasher
				.putString("FileState", Charsets.UTF_8)
				.putChar(HASH_FIELD_SEPARATOR)
				.putString(fileName, Charsets.UTF_8)
				.putChar(HASH_FIELD_SEPARATOR)
				.putLong(fileLength);

		hasher.putChar(HASH_OBJECT_SEPARATOR);
		fileTime.hashObject(hasher);

		hasher.putChar(HASH_OBJECT_SEPARATOR);
		fileHash.hashObject(hasher);
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
