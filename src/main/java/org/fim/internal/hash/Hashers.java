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

import static java.lang.Math.max;
import static org.fim.model.HashMode.hashAll;
import static org.fim.model.HashMode.hashMediumBlock;
import static org.fim.model.HashMode.hashSmallBlock;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;

import org.fim.model.FileHash;
import org.fim.model.HashMode;

public class Hashers
{
	private final Hasher smallBlockHasher;
	private final Hasher mediumBlockHasher;
	private final Hasher fullHasher;

	public Hashers(HashMode hashMode) throws NoSuchAlgorithmException
	{
		this.smallBlockHasher = new SmallBlockHasher(hashMode, hashSmallBlock);
		this.mediumBlockHasher = new MediumBlockHasher(hashMode, hashMediumBlock);
		this.fullHasher = new FullHasher(hashMode, hashAll);
	}

	public void reset(long fileSize)
	{
		smallBlockHasher.reset(fileSize);
		mediumBlockHasher.reset(fileSize);
		fullHasher.reset(fileSize);
	}

	public void update(long position, ByteBuffer buffer)
	{
		update(smallBlockHasher, position, buffer);
		update(mediumBlockHasher, position, buffer);
		update(fullHasher, position, buffer);
	}

	private void update(Hasher hasher, long position, ByteBuffer buffer)
	{
		int bufferPosition = buffer.position();
		int bufferLimit = buffer.limit();
		try
		{
			hasher.update(position, buffer);
		}
		finally
		{
			buffer.position(bufferPosition);
			buffer.limit(bufferLimit);
		}
	}

	public long getTotalBytesHashed()
	{
		long totalBytesHashed =
				max(smallBlockHasher.getTotalBytesHashed(),
						max(mediumBlockHasher.getTotalBytesHashed(), fullHasher.getTotalBytesHashed()));
		return totalBytesHashed;
	}

	public boolean isSmallBlockHashed()
	{
		return smallBlockHasher.isHashComplete();
	}

	public boolean isMediumBlockHashed()
	{
		return mediumBlockHasher.isHashComplete();
	}

	public FileHash getFileHash()
	{
		return new FileHash(smallBlockHasher.getHash(), mediumBlockHasher.getHash(), fullHasher.getHash());
	}

	public Hasher getSmallBlockHasher()
	{
		return smallBlockHasher;
	}

	public Hasher getMediumBlockHasher()
	{
		return mediumBlockHasher;
	}

	public Hasher getFullHasher()
	{
		return fullHasher;
	}
}
