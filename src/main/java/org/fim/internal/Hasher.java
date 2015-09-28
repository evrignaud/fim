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
package org.fim.internal;

import java.nio.MappedByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.fim.model.FileState;
import org.fim.model.HashMode;
import org.fim.util.HashModeUtil;

public class Hasher
{
	public static final String HASH_ALGORITHM = "SHA-512";

	private final long sizeToHash;
	private final boolean active;

	private MessageDigest digest;
	private long bytesHashed;
	private long totalBytesHashed;
	private long startPosition;

	public Hasher(long sizeToHash, HashMode hashMode, HashMode blockHashMode) throws NoSuchAlgorithmException
	{
		this.sizeToHash = sizeToHash;
		this.active = HashModeUtil.isCompatible(hashMode, blockHashMode);

		this.totalBytesHashed = 0;

		if (this.active)
		{
			this.digest = MessageDigest.getInstance(HASH_ALGORITHM);
		}
	}

	public long getBytesHashed()
	{
		return bytesHashed;
	}

	public long getTotalBytesHashed()
	{
		return totalBytesHashed;
	}

	public String getHash()
	{
		if (active)
		{
			byte[] digestBytes = digest.digest();
			return toHexString(digestBytes);
		}
		else
		{
			return FileState.NO_HASH;
		}
	}

	public void reset(long fileSize)
	{
		if (active)
		{
			if ((sizeToHash != FileState.SIZE_UNLIMITED) && (fileSize >= (sizeToHash * 2)))
			{
				// File size is at least twice the size we want to hash.
				// So skip the first block to ensure that the headers don't increase the collision probability when doing a rapid check.
				startPosition = sizeToHash;
			}
			else
			{
				startPosition = 0;
			}

			digest.reset();
			bytesHashed = 0;
		}
	}

	public void update(long position, MappedByteBuffer buffer)
	{
		if (active && (position >= startPosition) && ((sizeToHash == FileState.SIZE_UNLIMITED) || (position < (startPosition + sizeToHash))))
		{
			int limit = buffer.limit();
			digest.update(buffer);
			buffer.flip(); // Reset the buffer to be usable after
			bytesHashed += limit;
			totalBytesHashed += limit;
		}
	}

	protected String toHexString(byte[] digestBytes)
	{
		StringBuilder hexString = new StringBuilder();
		for (byte b : digestBytes)
		{
			hexString.append(Character.forDigit((b >> 4) & 0xF, 16));
			hexString.append(Character.forDigit((b & 0xF), 16));
		}

		return hexString.toString();
	}
}
