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
package org.fim.internal.hash;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.fim.model.Contants;
import org.fim.model.HashMode;

public abstract class AbstractHasher implements Hasher
{
	public static final String HASH_ALGORITHM = "SHA-512";

	private final boolean active;

	private MessageDigest digest;
	private long bytesHashed;
	private long totalBytesHashed;

	public AbstractHasher(HashMode hashMode) throws NoSuchAlgorithmException
	{
		this.active = isCompatible(hashMode);
		this.totalBytesHashed = 0;

		if (this.active)
		{
			this.digest = MessageDigest.getInstance(HASH_ALGORITHM);
		}
	}

	protected abstract boolean isCompatible(HashMode hashMode);

	protected abstract ByteBuffer getNextBlockToHash(long filePosition, long currentPosition, ByteBuffer buffer);

	@Override
	public boolean isActive()
	{
		return active;
	}

	@Override
	public long getBytesHashed()
	{
		return bytesHashed;
	}

	@Override
	public long getTotalBytesHashed()
	{
		return totalBytesHashed;
	}

	@Override
	public String getHash()
	{
		if (active)
		{
			byte[] digestBytes = digest.digest();
			return toHexString(digestBytes);
		}
		else
		{
			return Contants.NO_HASH;
		}
	}

	@Override
	public void reset(long fileSize)
	{
		if (active)
		{
			digest.reset();
			bytesHashed = 0;
		}
	}

	@Override
	public void update(long filePosition, ByteBuffer buffer)
	{
		if (active)
		{
			long currentPosition = filePosition;
			long limitPosition = filePosition + buffer.limit();
			ByteBuffer block;
			while ((currentPosition < limitPosition) && (((block = getNextBlockToHash(filePosition, currentPosition, buffer))) != null))
			{
				currentPosition = filePosition + block.limit();

				int remaining = block.remaining();
				digest.update(block);

				bytesHashed += remaining;
				totalBytesHashed += remaining;
			}
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
