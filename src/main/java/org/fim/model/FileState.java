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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class FileState
{
	private String fileName;
	private long lastModified;
	private String hash;

	private transient String newHash; // Used by StateComparator to detect accurately duplicates

	public FileState(String fileName, long lastModified, String hash)
	{
		if (fileName == null)
		{
			throw new IllegalArgumentException("Invalid null fileName");
		}
		if (hash == null)
		{
			throw new IllegalArgumentException("Invalid null hash");
		}

		this.setFileName(fileName);
		this.setLastModified(lastModified);
		this.setHash(hash);
	}

	public boolean contentChanged()
	{
		return !hash.equals(newHash);
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

		FileState fileState = (FileState) other;

		return new EqualsBuilder()
				.append(lastModified, fileState.lastModified)
				.append(fileName, fileState.fileName)
				.append(hash, fileState.hash)
				.isEquals();
	}

	@Override
	public int hashCode()
	{
		return new HashCodeBuilder(17, 37)
				.append(fileName)
				.append(lastModified)
				.append(hash)
				.toHashCode();
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(this)
				.append("fileName", fileName)
				.append("lastModified", lastModified)
				.append("hash", hash)
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

	public long getLastModified()
	{
		return lastModified;
	}

	public void setLastModified(long lastModified)
	{
		this.lastModified = lastModified;
	}

	public String getHash()
	{
		return hash;
	}

	public void setHash(String hash)
	{
		this.hash = hash;
	}

	public void setNewHash(String newHash)
	{
		this.newHash = newHash;
	}

	public void resetNewHash()
	{
		newHash = hash;
	}
}
