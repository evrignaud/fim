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
package org.fim.tool;

import java.util.Objects;

import org.fim.model.Difference;

public class FileNameDiff
{
	private String before;
	private String after;

	public FileNameDiff(String before, String after)
	{
		this.before = before;
		this.after = after;
	}

	public FileNameDiff(Difference difference)
	{
		before = difference.getPreviousFileState().getFileName();
		after = difference.getFileState().getFileName();
	}

	public String getBefore()
	{
		return before;
	}

	public String getAfter()
	{
		return after;
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (other == null || getClass() != other.getClass())
		{
			return false;
		}
		FileNameDiff fileNameDiff = (FileNameDiff) other;
		return Objects.equals(before, fileNameDiff.before) &&
				Objects.equals(after, fileNameDiff.after);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(before, after);
	}
}
