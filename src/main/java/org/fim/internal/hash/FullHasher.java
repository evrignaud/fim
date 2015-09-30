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

public class FullHasher extends Hasher
{
	public FullHasher(HashMode hashMode, HashMode blockHashMode) throws NoSuchAlgorithmException
	{
		super(hashMode, blockHashMode);
	}

	@Override
	protected long computeSizeToHash(HashMode blockHashMode)
	{
		return FileState.SIZE_UNLIMITED;
	}

	@Override
	protected long computeStartPosition(long fileSize)
	{
		return 0;
	}

	@Override
	protected ByteBuffer getBlockToHash(long position, ByteBuffer buffer)
	{
		return buffer;
	}
}
