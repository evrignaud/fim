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

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.fim.model.FileState;
import org.fim.model.HashMode;
import org.fim.util.HashModeUtil;

public abstract class Hasher
{
	public static final String HASH_ALGORITHM = "SHA-512";

	private final HashMode blockHashMode;
	private final long sizeToHash;
	private final boolean active;

	private long startPosition;

	private MessageDigest digest;
	private long bytesHashed;
	private long totalBytesHashed;

	public Hasher(HashMode hashMode, HashMode blockHashMode) throws NoSuchAlgorithmException
	{
		this.blockHashMode = blockHashMode;
		this.active = HashModeUtil.isCompatible(hashMode, blockHashMode);
		this.sizeToHash = computeSizeToHash(blockHashMode);
		this.totalBytesHashed = 0;

		if (this.active)
		{
			this.digest = MessageDigest.getInstance(HASH_ALGORITHM);
		}
	}

	protected abstract long computeSizeToHash(HashMode blockHashMode);

	protected abstract long computeStartPosition(long fileSize);

	protected abstract ByteBuffer getBlockToHash(long position, ByteBuffer buffer);

	public long getSizeToHash()
	{
		return sizeToHash;
	}

	public long getStartPosition()
	{
		return startPosition;
	}

	public final long getBytesHashed()
	{
		return bytesHashed;
	}

	public final long getTotalBytesHashed()
	{
		return totalBytesHashed;
	}

	public final String getHash()
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

	public final void reset(long fileSize)
	{
		if (active)
		{
			this.bytesHashed = 0;
			this.startPosition = computeStartPosition(fileSize);

			digest.reset();
		}
	}

	public final void update(long position, ByteBuffer buffer)
	{
		if (!active)
		{
			return;
		}

		ByteBuffer block = getBlockToHash(position, buffer);
		if (block != null)
		{
			int size = block.limit();
			digest.update(block);

			bytesHashed += size;
			totalBytesHashed += size;
		}
	}

	protected final String toHexString(byte[] digestBytes)
	{
		StringBuilder hexString = new StringBuilder();
		for (byte b : digestBytes)
		{
			hexString.append(Character.forDigit((b >> 4) & 0xF, 16));
			hexString.append(Character.forDigit((b & 0xF), 16));
		}

		return hexString.toString();
	}

	public final boolean isHashComplete()
	{
		return getBytesHashed() == sizeToHash;
	}
}
