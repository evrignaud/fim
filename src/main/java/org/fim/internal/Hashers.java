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

import static java.lang.Math.max;
import static org.fim.model.FileState.SIZE_1_MB;
import static org.fim.model.FileState.SIZE_4_KB;
import static org.fim.model.FileState.SIZE_UNLIMITED;
import static org.fim.model.HashMode.hashAll;
import static org.fim.model.HashMode.hashMediumBlock;
import static org.fim.model.HashMode.hashSmallBlock;

import java.nio.MappedByteBuffer;
import java.security.NoSuchAlgorithmException;

import org.fim.model.FileHash;
import org.fim.model.HashMode;

public class Hashers
{
	public static final int SMALL_BLOCK_SIZE = SIZE_4_KB;
	public static final int MEDIUM_BLOCK_SIZE = SIZE_1_MB;

	private final Hasher smallBlockHasher;
	private final Hasher mediumBlockHasher;
	private final Hasher fullHasher;

	public Hashers(HashMode hashMode) throws NoSuchAlgorithmException
	{
		this.smallBlockHasher = new Hasher(SMALL_BLOCK_SIZE, hashMode, hashSmallBlock);
		this.mediumBlockHasher = new Hasher(MEDIUM_BLOCK_SIZE, hashMode, hashMediumBlock);
		this.fullHasher = new Hasher(SIZE_UNLIMITED, hashMode, hashAll);
	}

	public void reset(long fileSize)
	{
		smallBlockHasher.reset(fileSize);
		mediumBlockHasher.reset(fileSize);
		fullHasher.reset(fileSize);
	}

	public void update(long position, MappedByteBuffer buffer)
	{
		smallBlockHasher.update(position, buffer);
		mediumBlockHasher.update(position, buffer);
		fullHasher.update(position, buffer);
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
		return smallBlockHasher.getBytesHashed() == SMALL_BLOCK_SIZE;
	}

	public boolean isMediumBlockHashed()
	{
		return mediumBlockHasher.getBytesHashed() == MEDIUM_BLOCK_SIZE;
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
