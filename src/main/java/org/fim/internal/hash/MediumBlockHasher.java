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
import java.security.NoSuchAlgorithmException;

import org.fim.internal.Hasher;
import org.fim.model.FileState;
import org.fim.model.HashMode;

public class MediumBlockHasher extends Hasher
{
	public MediumBlockHasher(HashMode hashMode, HashMode blockHashMode) throws NoSuchAlgorithmException
	{
		super(hashMode, blockHashMode);
	}

	@Override
	protected long computeSizeToHash(HashMode blockHashMode)
	{
		return FileState.SIZE_1_MB;
	}

	@Override
	protected long computeStartPosition(long fileSize)
	{
		if (fileSize >= (getSizeToHash() * 2))
		{
			// File size is at least twice the size we want to hash.
			// So skip the first block to ensure that the headers don't increase the collision probability when doing a rapid check.
			return getSizeToHash();
		}
		else
		{
			return 0;
		}
	}

	@Override
	protected ByteBuffer getBlockToHash(long position, ByteBuffer buffer)
	{
		if ((position >= getStartPosition()) && (position < (getStartPosition() + getSizeToHash())))
		{
			return buffer;
		}
		return null;
	}
}
