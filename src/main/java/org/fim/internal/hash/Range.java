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

import java.util.Objects;

import com.google.common.base.MoreObjects;

public class Range implements Comparable<Range>
{
	/** The initial index of the range to be copied, inclusive */
	private long from;

	/** The final index of the range to be copied, exclusive */
	private long to;

	public Range(long from, long to)
	{
		if (from > to)
		{
			throw new RuntimeException("'to' must be greater than 'from'");
		}

		this.from = from;
		this.to = to;
	}

	public long getFrom()
	{
		return from;
	}

	public long getTo()
	{
		return to;
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}

		if (other == null || !(other instanceof Range))
		{
			return false;
		}

		Range range = (Range) other;

		return Objects.equals(this.from, range.from)
				&& Objects.equals(this.to, range.to);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(from, to);
	}

	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
				.add("from", from)
				.add("to", to)
				.toString();
	}

	@Override
	public int compareTo(Range other)
	{
		int value = Long.compare(from, other.from);
		if (value != 0)
		{
			return value;
		}

		return Long.compare(to, other.to);
	}
}
