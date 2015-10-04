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
import static java.lang.Math.min;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;

import org.fim.model.FileHash;
import org.fim.model.HashMode;

public class Hashers
{
	private final BlockHasher smallBlockHasher;
	private final BlockHasher mediumBlockHasher;
	private final Hasher fullHasher;
	private final int blockSize;
	private long fileSize;

	public Hashers(HashMode hashMode) throws NoSuchAlgorithmException
	{
		this.smallBlockHasher = new SmallBlockHasher(hashMode);
		this.mediumBlockHasher = new MediumBlockHasher(hashMode);
		this.fullHasher = new FullHasher(hashMode);

		checkBlockSizeCompatibility();

		blockSize = getOptimizedBlockSize(hashMode);
	}

	public void reset(long fileSize)
	{
		this.fileSize = fileSize;
		smallBlockHasher.reset(fileSize);
		mediumBlockHasher.reset(fileSize);
		fullHasher.reset(fileSize);
	}

	public Range getNextRange(long filePosition)
	{
		long from;
		long to;
		Range nextSmallRange;
		Range nextMediumRange;

		if (fullHasher.isActive())
		{
			nextSmallRange = smallBlockHasher.getNextRange(filePosition);
			nextMediumRange = mediumBlockHasher.getNextRange(filePosition);

			from = filePosition;
			to = min(fileSize, filePosition + blockSize);

			to = adjustToRange(to, nextSmallRange);
			to = adjustToRange(to, nextMediumRange);

			return new Range(from, to);
		}
		else if (smallBlockHasher.isActive() && mediumBlockHasher.isActive())
		{
			nextSmallRange = smallBlockHasher.getNextRange(filePosition);
			nextMediumRange = mediumBlockHasher.getNextRange(filePosition);

			if (nextSmallRange == null && nextMediumRange == null)
			{
				return null;
			}

			if (nextSmallRange == null)
			{
				return nextMediumRange;
			}

			if (nextMediumRange == null)
			{
				return nextSmallRange;
			}

			if (nextSmallRange.getTo() < nextMediumRange.getFrom())
			{
				// Next small block is before the next medium block
				return nextSmallRange;
			}

			if (nextMediumRange.getTo() < nextSmallRange.getFrom())
			{
				// Next medium block is before the next small block
				return nextMediumRange;
			}

			return union(nextSmallRange, nextMediumRange);
		}
		else if (smallBlockHasher.isActive())
		{
			nextSmallRange = smallBlockHasher.getNextRange(filePosition);
			return nextSmallRange;
		}
		else if (mediumBlockHasher.isActive())
		{
			nextMediumRange = mediumBlockHasher.getNextRange(filePosition);
			return nextMediumRange;
		}
		return null;
	}

	private long adjustToRange(long to, Range range)
	{
		if (range != null && range.getFrom() < to && range.getTo() > to)
		{
			return range.getTo();
		}
		return to;
	}

	private Range union(Range range1, Range range2)
	{
		long from = min(range1.getFrom(), range2.getFrom());
		long to = max(range1.getTo(), range2.getTo());
		return new Range(from, to);
	}

	public void update(long filePosition, ByteBuffer buffer)
	{
		update(smallBlockHasher, filePosition, buffer);
		update(mediumBlockHasher, filePosition, buffer);
		update(fullHasher, filePosition, buffer);
	}

	private void update(Hasher hasher, long filePosition, ByteBuffer buffer)
	{
		if (hasher.isActive())
		{
			int bufferPosition = buffer.position();
			int bufferLimit = buffer.limit();
			try
			{
				hasher.update(filePosition, buffer);
			}
			finally
			{
				buffer.limit(bufferLimit);
				buffer.position(bufferPosition);
			}
		}
	}

	public long getTotalBytesHashed()
	{
		long totalBytesHashed =
				max(smallBlockHasher.getTotalBytesHashed(),
						max(mediumBlockHasher.getTotalBytesHashed(), fullHasher.getTotalBytesHashed()));
		return totalBytesHashed;
	}

	public boolean hashComplete()
	{
		return smallBlockHasher.hashComplete() && mediumBlockHasher.hashComplete() && fullHasher.hashComplete();
	}

	public FileHash getFileHash()
	{
		return new FileHash(smallBlockHasher.getHash(), mediumBlockHasher.getHash(), fullHasher.getHash());
	}

	protected Hasher getSmallBlockHasher()
	{
		return smallBlockHasher;
	}

	protected Hasher getMediumBlockHasher()
	{
		return mediumBlockHasher;
	}

	protected Hasher getFullHasher()
	{
		return fullHasher;
	}

	private void checkBlockSizeCompatibility()
	{
		int smallBlockSize = smallBlockHasher.getBlockSize();
		int mediumBlockSize = mediumBlockHasher.getBlockSize();
		if ((mediumBlockSize % smallBlockSize) != 0)
		{
			throw new RuntimeException("Fim cannot work correctly. 'mediumBlockSize' is not a multiple of the 'smallBlockSize': " +
					"small=" + smallBlockSize + ", " +
					"medium=" + mediumBlockSize);
		}
	}

	private int getOptimizedBlockSize(HashMode hashMode)
	{
		switch (hashMode)
		{
			case hashAll:
				return mediumBlockHasher.getBlockSize() * 30;

			case hashMediumBlock:
				return mediumBlockHasher.getBlockSize();

			case hashSmallBlock:
				return smallBlockHasher.getBlockSize();

			case dontHash:
			default:
				return 0;
		}
	}
}
