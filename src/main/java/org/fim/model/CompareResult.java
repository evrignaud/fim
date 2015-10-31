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

import static org.fim.util.FormatUtil.formatCreationTime;
import static org.fim.util.FormatUtil.formatDate;
import static org.fim.util.FormatUtil.formatLastModified;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.fim.util.Console;

public class CompareResult
{
	public static final String NOTHING = "[nothing]";

	private static final Comparator<Difference> fileNameComparator = new Difference.FileNameComparator();

	private List<Difference> added;
	private List<Difference> copied;
	private List<Difference> duplicated;
	private List<Difference> dateModified;
	private List<Difference> contentModified;
	private List<Difference> attributesModified;
	private List<Difference> renamed;
	private List<Difference> deleted;
	private List<Difference> corrupted;

	private Context context;
	private State lastState;
	private boolean searchForHardwareCorruption;

	public CompareResult(Context context, State lastState)
	{
		this.context = context;
		this.lastState = lastState;
		this.searchForHardwareCorruption = false;

		added = new ArrayList<>();
		copied = new ArrayList<>();
		duplicated = new ArrayList<>();
		dateModified = new ArrayList<>();
		contentModified = new ArrayList<>();
		attributesModified = new ArrayList<>();
		renamed = new ArrayList<>();
		deleted = new ArrayList<>();
		corrupted = new ArrayList<>();
	}

	public boolean isSearchForHardwareCorruption()
	{
		return searchForHardwareCorruption;
	}

	public void setSearchForHardwareCorruption(boolean searchForHardwareCorruption)
	{
		this.searchForHardwareCorruption = searchForHardwareCorruption;
	}

	public void sortResults()
	{
		sortDifferences(added);
		sortDifferences(copied);
		sortDifferences(duplicated);
		sortDifferences(dateModified);
		sortDifferences(contentModified);
		sortDifferences(attributesModified);
		sortDifferences(renamed);
		sortDifferences(deleted);
		sortDifferences(corrupted);
	}

	private void sortDifferences(List<Difference> differences)
	{
		Collections.sort(differences, fileNameComparator);
	}

	public CompareResult displayChanges()
	{
		if (lastState != null)
		{
			System.out.printf("Comparing with the last committed state from %s%n", formatDate(lastState.getTimestamp()));
			if (lastState.getComment().length() > 0)
			{
				System.out.println("Comment: " + lastState.getComment());
			}
			Console.newLine();
		}

		if (!context.isVerbose())
		{
			displayCounts();
			return this;
		}

		String stateFormat = "%-17s ";

		for (Difference diff : added)
		{
			System.out.printf(stateFormat + "%s%n", "Added:", diff.getFileState().getFileName());
		}

		for (Difference diff : copied)
		{
			System.out.printf(stateFormat + "%s \t(was %s)%n", "Copied:", diff.getFileState().getFileName(), diff.getPreviousFileState().getFileName());
		}

		for (Difference diff : duplicated)
		{
			System.out.printf(stateFormat + "%s = %s%s%n", "Duplicated:", diff.getFileState().getFileName(), diff.getPreviousFileState().getFileName(), formatModifiedAttributes(diff, true));
		}

		for (Difference diff : dateModified)
		{
			System.out.printf(stateFormat + "%s \t%s%n", "Date modified:", diff.getFileState().getFileName(), formatModifiedAttributes(diff, false));
		}

		for (Difference diff : contentModified)
		{
			System.out.printf(stateFormat + "%s \t%s%n", "Content modified:", diff.getFileState().getFileName(), formatModifiedAttributes(diff, false));
		}

		for (Difference diff : attributesModified)
		{
			System.out.printf(stateFormat + "%s \t%s%n", "Attrs. modified:", diff.getFileState().getFileName(), formatModifiedAttributes(diff, false));
		}

		for (Difference diff : renamed)
		{
			System.out.printf(stateFormat + "%s -> %s%s%n", "Renamed:", diff.getPreviousFileState().getFileName(), diff.getFileState().getFileName(), formatModifiedAttributes(diff, true));
		}

		for (Difference diff : deleted)
		{
			System.out.printf(stateFormat + "%s%n", "Deleted:", diff.getFileState().getFileName());
		}

		for (Difference diff : corrupted)
		{
			System.out.printf(stateFormat + "%s \t%s%n", "Corrupted?:", diff.getFileState().getFileName(), formatModifiedAttributes(diff, false));
		}

		if (somethingModified())
		{
			Console.newLine();
		}

		displayCounts();

		return this;
	}

	private String formatModifiedAttributes(Difference diff, boolean nextLine)
	{
		int modifCount = 0;
		StringBuilder modification = new StringBuilder(nextLine ? " " : ""); // Put a white space to force to add a separator

		Map<String, String> previousFileAttributes = diff.getPreviousFileState().getFileAttributes();
		Map<String, String> currentFileAttributes = diff.getFileState().getFileAttributes();
		for (FileAttribute attribute : FileAttribute.values())
		{
			String key = attribute.name();
			String previousValue = getValue(previousFileAttributes, key);
			String currentValue = getValue(currentFileAttributes, key);

			if (false == Objects.equals(previousValue, currentValue))
			{
				modifCount++;
				addSeparator(diff, modification);
				modification.append(key).append(": ").append(previousValue).append(" -> ").append(currentValue);
			}
		}

		if (diff.isCreationTimeChanged())
		{
			modifCount++;
			addSeparator(diff, modification);
			modification.append("creationTime: ").append(formatCreationTime(diff.getPreviousFileState())).append(" -> ").append(formatCreationTime(diff.getFileState()));
		}

		if (diff.isLastModifiedChanged())
		{
			modifCount++;
			addSeparator(diff, modification);
			modification.append("lastModified: ").append(formatLastModified(diff.getPreviousFileState())).append(" -> ").append(formatLastModified(diff.getFileState()));
		}

		if (modifCount > 1)
		{
			modification.append('\n');
		}

		return modification.toString();
	}

	private void addSeparator(Difference diff, StringBuilder modification)
	{
		if (modification.length() == 0)
		{
			return;
		}

		modification.append("\n");
		int len = 17 + 1 + diff.getFileState().getFileName().length() + 1;
		for (int index = 0; index < len; index++)
		{
			modification.append(' ');
		}
		modification.append('\t');
	}

	private String getValue(Map<String, String> attributes, String key)
	{
		String value = attributes != null ? attributes.get(key) : null;
		if (value == null || value.length() == 0)
		{
			value = NOTHING;
		}
		return value;
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

			if (!attributesModified.isEmpty())
			{
				message += "" + attributesModified.size() + " attrs. modified, ";
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

			if (!corrupted.isEmpty())
			{
				message += "" + corrupted.size() + " corrupted, ";
			}

			message = message.replaceAll(", $", "");
			System.out.println(message);
		}
		else
		{
			if (isSearchForHardwareCorruption())
			{
				System.out.println("Nothing corrupted");
			}
			else
			{
				System.out.println("Nothing modified");
			}
		}

		return this;
	}

	public boolean somethingModified()
	{
		return modifiedCount() > 0;
	}

	public int modifiedCount()
	{
		return added.size() + copied.size() + duplicated.size() + dateModified.size() + contentModified.size() +
				attributesModified.size() + renamed.size() + deleted.size() + corrupted.size();
	}

	public ModificationCounts getModificationCounts()
	{
		ModificationCounts modificationCounts = new ModificationCounts();
		modificationCounts.setAdded(added.size());
		modificationCounts.setCopied(copied.size());
		modificationCounts.setDuplicated(duplicated.size());
		modificationCounts.setDateModified(dateModified.size());
		modificationCounts.setContentModified(contentModified.size());
		modificationCounts.setAttributesModified(attributesModified.size());
		modificationCounts.setRenamed(renamed.size());
		modificationCounts.setDeleted(deleted.size());

		return modificationCounts;
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

	public List<Difference> getAttributesModified()
	{
		return attributesModified;
	}

	public List<Difference> getRenamed()
	{
		return renamed;
	}

	public List<Difference> getDeleted()
	{
		return deleted;
	}

	public List<Difference> getCorrupted()
	{
		return corrupted;
	}
}

