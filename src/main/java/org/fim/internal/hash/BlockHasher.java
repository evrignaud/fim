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

import static java.lang.Math.min;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.fim.model.HashMode;

public abstract class BlockHasher extends Hasher
{
	private Range[] ranges;
	private long fileSize;
	private long sizeToHash;

	public BlockHasher(HashMode hashMode) throws NoSuchAlgorithmException
	{
		super(hashMode);
	}

	@Override
	protected void resetHasher(long fileSize)
	{
		this.fileSize = fileSize;
		List<Long> blockIndexes = getBlockIndexes();

		ranges = new Range[blockIndexes.size()];
		int rangeIndex = 0;
		sizeToHash = 0;
		for (Long blockIndex : blockIndexes)
		{
			Range range = getRange(blockIndex);
			sizeToHash += range.getTo() - range.getFrom();

			ranges[rangeIndex] = range;
			rangeIndex++;
		}
	}

	protected long getSizeToHash()
	{
		return sizeToHash;
	}

	protected Range getRange(long blockIndex)
	{
		int blockSize = getBlockSize();
		long from = min(fileSize, blockIndex * blockSize);
		long to = min(fileSize, from + blockSize);
		return new Range(from, to);
	}

	protected List<Long> getBlockIndexes()
	{
		// When it's possible ignore the first block to ensure that the headers don't increase the collision probability when doing a rapid check

		int blockSize = getBlockSize();
		List<Long> blockIndexes = new ArrayList<>();
		long middleBlockIndex = getMiddleBlockIndex();
		long endBlockIndex = getEndBlockIndex();
		if (fileSize > (blockSize * 4))
		{
			blockIndexes.add(1L);
			blockIndexes.add(middleBlockIndex);
			blockIndexes.add(endBlockIndex);
		}
		else if (fileSize > (blockSize * 3))
		{
			blockIndexes.add(1L);
			blockIndexes.add(endBlockIndex);
		}
		else if (fileSize > (blockSize * 2))
		{
			blockIndexes.add(1L);
		}
		else
		{
			blockIndexes.add(0L);
		}

		return blockIndexes;
	}

	protected long getMiddleBlockIndex()
	{
		return (fileSize / getBlockSize()) / 2;
	}

	protected long getEndBlockIndex()
	{
		return (fileSize / getBlockSize()) - 1;
	}

	protected Range[] getRanges()
	{
		return ranges;
	}

	protected Range getNextRange(long filePosition)
	{
		for (Range range : ranges)
		{
			if (range.getFrom() >= filePosition)
			{
				return range;
			}
		}
		return null;
	}

	@Override
	protected ByteBuffer getNextBlockToHash(long filePosition, long currentPosition, ByteBuffer buffer)
	{
		Range range = getNextRange(currentPosition);
		if (range == null)
		{
			return null;
		}

		int position = (int) (range.getFrom() - filePosition);
		int limit = (int) (range.getTo() - filePosition);
		if (position > buffer.capacity() || limit > buffer.capacity())
		{
			// We are too far. This range will be in a next buffer
			return null;
		}

		buffer.limit(limit);
		buffer.position(position);
		return buffer.duplicate();
	}

	@Override
	public boolean hashComplete()
	{
		return getBytesHashed() == sizeToHash;
	}
}
