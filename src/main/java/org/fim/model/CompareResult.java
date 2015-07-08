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

import static org.fim.util.FormatUtil.formatDate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CompareResult
{
	private static Comparator<Difference> fileNameComparator = new Difference.FileNameComparator();

	private List<Difference> added;
	private List<Difference> copied;
	private List<Difference> duplicated;
	private List<Difference> dateModified;
	private List<Difference> contentModified;
	private List<Difference> renamed;
	private List<Difference> deleted;

	private Parameters parameters;
	private State lastState;

	public CompareResult(Parameters parameters, State lastState)
	{
		this.parameters = parameters;
		this.lastState = lastState;

		added = new ArrayList<>();
		copied = new ArrayList<>();
		duplicated = new ArrayList<>();
		dateModified = new ArrayList<>();
		contentModified = new ArrayList<>();
		renamed = new ArrayList<>();
		deleted = new ArrayList<>();
	}

	public void sortResults()
	{
		sortDifferences(added);
		sortDifferences(copied);
		sortDifferences(duplicated);
		sortDifferences(dateModified);
		sortDifferences(contentModified);
		sortDifferences(renamed);
		sortDifferences(deleted);
	}

	private void sortDifferences(List<Difference> differences)
	{
		Collections.sort(differences, fileNameComparator);
	}

	public CompareResult displayChanges()
	{
		if (lastState != null)
		{
			System.out.println("Comparing with the last committed state from " + formatDate(lastState.getTimestamp()));
			if (lastState.getMessage().length() > 0)
			{
				System.out.println("Message: " + lastState.getMessage());
			}
			System.out.println("");
		}

		if (!parameters.isVerbose())
		{
			displayCounts();
			return this;
		}

		String stateFormat = "%-17s ";

		for (Difference diff : added)
		{
			System.out.format(String.format(stateFormat + "%s%n", "Added:", diff.getFileState().getFileName()));
		}

		for (Difference diff : copied)
		{
			System.out.format(String.format(stateFormat + "%s \t(was %s)%n", "Copied:", diff.getFileState().getFileName(), diff.getPreviousFileState().getFileName()));
		}

		for (Difference diff : duplicated)
		{
			System.out.format(String.format(stateFormat + "%s = %s%n", "Duplicated:", diff.getFileState().getFileName(), diff.getPreviousFileState().getFileName()));
		}

		for (Difference diff : dateModified)
		{
			System.out.format(stateFormat + "%s \t%s -> %s%n", "Date modified:", diff.getFileState().getFileName(), formatDate(diff.getPreviousFileState()), formatDate(diff.getFileState()));
		}

		for (Difference diff : contentModified)
		{
			System.out.format(stateFormat + "%s \t%s -> %s%n", "Content modified:", diff.getFileState().getFileName(), formatDate(diff.getPreviousFileState()), formatDate(diff.getFileState()));
		}

		for (Difference diff : renamed)
		{
			System.out.format(String.format(stateFormat + "%s -> %s%n", "Renamed:", diff.getPreviousFileState().getFileName(), diff.getFileState().getFileName()));
		}

		for (Difference diff : deleted)
		{
			System.out.format(String.format(stateFormat + "%s%n", "Deleted:", diff.getFileState().getFileName()));
		}

		if (somethingModified())
		{
			System.out.println("");
		}

		displayCounts();

		return this;
	}

	public CompareResult displayCounts()
	{
		if (somethingModified())
		{
			String message = "";
			if (!added.isEmpty())
			{
				message += "" + added.size() + " added, ";
			}

			if (!copied.isEmpty())
			{
				message += "" + copied.size() + " copied, ";
			}

			if (!duplicated.isEmpty())
			{
				message += "" + duplicated.size() + " duplicated, ";
			}

			if (!dateModified.isEmpty())
			{
				message += "" + dateModified.size() + " date modified, ";
			}

			if (!contentModified.isEmpty())
			{
				message += "" + contentModified.size() + " content modified, ";
			}

			if (!renamed.isEmpty())
			{
				message += "" + renamed.size() + " renamed, ";
			}

			if (!deleted.isEmpty())
			{
				message += "" + deleted.size() + " deleted, ";
			}

			message = message.replaceAll(", $", "");
			System.out.println(message);
		}
		else
		{
			System.out.println("Nothing modified");
		}

		return this;
	}

	public boolean somethingModified()
	{
		return (added.size() + copied.size() + duplicated.size() + dateModified.size() + contentModified.size() + renamed.size() + deleted.size()) > 0;
	}

	public List<Difference> getAdded()
	{
		return added;
	}

	public List<Difference> getCopied()
	{
		return copied;
	}

	public List<Difference> getDuplicated()
	{
		return duplicated;
	}

	public List<Difference> getDateModified()
	{
		return dateModified;
	}

	public List<Difference> getContentModified()
	{
		return contentModified;
	}

	public List<Difference> getRenamed()
	{
		return renamed;
	}

	public List<Difference> getDeleted()
	{
		return deleted;
	}
}

