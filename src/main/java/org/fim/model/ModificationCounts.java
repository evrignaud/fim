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
package org.fim.model;

public class ModificationCounts
{
	private int added;
	private int copied;
	private int duplicated;
	private int dateModified;
	private int contentModified;
	private int renamed;
	private int deleted;

	public int getAdded()
	{
		return added;
	}

	public void setAdded(int added)
	{
		this.added = added;
	}

	public int getCopied()
	{
		return copied;
	}

	public void setCopied(int copied)
	{
		this.copied = copied;
	}

	public int getDuplicated()
	{
		return duplicated;
	}

	public void setDuplicated(int duplicated)
	{
		this.duplicated = duplicated;
	}

	public int getDateModified()
	{
		return dateModified;
	}

	public void setDateModified(int dateModified)
	{
		this.dateModified = dateModified;
	}

	public int getContentModified()
	{
		return contentModified;
	}

	public void setContentModified(int contentModified)
	{
		this.contentModified = contentModified;
	}

	public int getRenamed()
	{
		return renamed;
	}

	public void setRenamed(int renamed)
	{
		this.renamed = renamed;
	}

	public int getDeleted()
	{
		return deleted;
	}

	public void setDeleted(int deleted)
	{
		this.deleted = deleted;
	}

	public void add(ModificationCounts modificationCounts)
	{
		added += modificationCounts.getAdded();
		copied += modificationCounts.getCopied();
		duplicated += modificationCounts.getDuplicated();
		dateModified += modificationCounts.getDateModified();
		contentModified += modificationCounts.getContentModified();
		renamed += modificationCounts.getRenamed();
		deleted += modificationCounts.getDeleted();
	}
}
