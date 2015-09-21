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
import java.util.Objects;

import com.google.common.base.Charsets;
import com.google.common.base.MoreObjects;
import com.google.common.hash.Hasher;

public class FileTime implements Comparable<FileTime>, Hashable
{
	private long creationTime;
	private long lastModified;

	public FileTime(long creationTime, long lastModified)
	{
		setCreationTime(creationTime);
		setLastModified(lastModified);
	}

	public FileTime(BasicFileAttributes attributes)
	{
		this(attributes.creationTime().toMillis(), attributes.lastModifiedTime().toMillis());
	}

	public FileTime(long timestamp)
	{
		this(timestamp, timestamp);
	}

	public FileTime(FileTime fileTime)
	{
		this(fileTime.getCreationTime(), fileTime.getLastModified());
	}

	public long getCreationTime()
	{
		return creationTime;
	}

	public void setCreationTime(long creationTime)
	{
		this.creationTime = creationTime;
	}

	public long getLastModified()
	{
		return lastModified;
	}

	public void setLastModified(long lastModified)
	{
		this.lastModified = lastModified;
	}

	public void reset(long timestamp)
	{
		setCreationTime(timestamp);
		setLastModified(timestamp);
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}

		if (other == null || !(other instanceof FileTime))
		{
			return false;
		}

		FileTime otherFileTime = (FileTime) other;

		return Objects.equals(this.creationTime, otherFileTime.creationTime)
				&& Objects.equals(this.lastModified, otherFileTime.lastModified);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(creationTime, lastModified);
	}

	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
				.add("creationTime", creationTime)
				.add("lastModified", lastModified)
				.toString();
	}

	@Override
	public int compareTo(FileTime other)
	{
		if (creationTime != other.creationTime)
		{
			return Long.compare(creationTime, other.creationTime);
		}

		return Long.compare(lastModified, other.lastModified);
	}

	@Override
	public void hashObject(Hasher hasher)
	{
		hasher
				.putString("FileTime", Charsets.UTF_8)
				.putChar(HASH_FIELD_SEPARATOR)
				.putLong(creationTime)
				.putChar(HASH_FIELD_SEPARATOR)
				.putLong(lastModified);
	}
}
