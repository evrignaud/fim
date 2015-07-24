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

import com.google.common.base.Charsets;
import com.google.common.base.MoreObjects;
import com.google.common.hash.Hasher;

public class FileHash implements Comparable<FileHash>, Hashable
{
	private String firstFourKiloHash;
	private String firstMegaHash;
	private String fullHash;

	public FileHash(String firstFourKiloHash, String firstMegaHash, String fullHash)
	{
		this.firstFourKiloHash = firstFourKiloHash;
		this.firstMegaHash = firstMegaHash;
		this.fullHash = fullHash;
	}

	public FileHash(FileHash fileHash)
	{
		this.firstFourKiloHash = fileHash.getFirstFourKiloHash();
		this.firstMegaHash = fileHash.getFirstMegaHash();
		this.fullHash = fileHash.getFullHash();
	}

	public String getFirstFourKiloHash()
	{
		return firstFourKiloHash;
	}

	public void setFirstFourKiloHash(String firstFourKiloHash)
	{
		this.firstFourKiloHash = firstFourKiloHash;
	}

	public String getFirstMegaHash()
	{
		return firstMegaHash;
	}

	public void setFirstMegaHash(String firstMegaHash)
	{
		this.firstMegaHash = firstMegaHash;
	}

	public String getFullHash()
	{
		return fullHash;
	}

	public void setFullHash(String fullHash)
	{
		this.fullHash = fullHash;
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

		return Objects.equals(this.firstFourKiloHash, otherFileHash.firstFourKiloHash)
				&& Objects.equals(this.firstMegaHash, otherFileHash.firstMegaHash)
				&& Objects.equals(this.fullHash, otherFileHash.fullHash);

	}

	@Override
	public int hashCode()
	{
		return Objects.hash(firstFourKiloHash, firstMegaHash, fullHash);
	}

	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
				.add("firstFourKiloHash", firstFourKiloHash)
				.add("firstMegaHash", firstMegaHash)
				.add("fullHash", fullHash)
				.toString();
	}

	@Override
	public int compareTo(FileHash other)
	{
		int value = firstFourKiloHash.compareTo(other.firstFourKiloHash);
		if (value != 0)
		{
			return value;
		}

		value = firstMegaHash.compareTo(other.firstMegaHash);
		if (value != 0)
		{
			return value;
		}

		return fullHash.compareTo(other.fullHash);
	}

	@Override
	public void hashObject(Hasher hasher)
	{
		hasher
				.putString(firstFourKiloHash, Charsets.UTF_8)
				.putChar(HASH_SEPARATOR)
				.putString(firstMegaHash, Charsets.UTF_8)
				.putChar(HASH_SEPARATOR)
				.putString(fullHash, Charsets.UTF_8);
	}
}
