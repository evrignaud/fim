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

import java.util.Objects;

import com.google.common.base.MoreObjects;

public class FileHash implements Comparable<FileHash>
{
	private String firstMbHash;
	private String fullHash;

	public FileHash(String firstMbHash, String fullHash)
	{
		this.firstMbHash = firstMbHash;
		this.fullHash = fullHash;
	}

	public FileHash(FileHash fileHash)
	{
		this.firstMbHash = fileHash.getFirstMbHash();
		this.fullHash = fileHash.getFullHash();
	}

	public String getFirstMbHash()
	{
		return firstMbHash;
	}

	public String getFullHash()
	{
		return fullHash;
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}

		if (other == null || !(other instanceof FileHash))
		{
			return false;
		}

		FileHash otherFileHash = (FileHash) other;

		return Objects.equals(this.firstMbHash, otherFileHash.firstMbHash)
				&& Objects.equals(this.fullHash, otherFileHash.fullHash);

	}

	@Override
	public int hashCode()
	{
		return Objects.hash(firstMbHash, fullHash);
	}

	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
				.add("firstMbHash", firstMbHash)
				.add("fullHash", fullHash)
				.toString();
	}

	@Override
	public int compareTo(FileHash other)
	{
		int first = firstMbHash.compareTo(other.firstMbHash);
		return first == 0 ? fullHash.compareTo(other.fullHash) : first;
	}
}
