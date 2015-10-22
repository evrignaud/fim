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
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.fim.util.Console;

public class LogResult
{
	private List<LogEntry> logEntries = new ArrayList<>();

	public void add(LogEntry logEntry)
	{
		logEntries.add(logEntry);
	}

	public List<LogEntry> getLogEntries()
	{
		return logEntries;
	}

	public void displayEntries()
	{
		for (LogEntry logEntry : logEntries)
		{
			System.out.printf("State #%d: %s (%d files - %s)%n", logEntry.getStateNumber(), formatDate(logEntry.getTimestamp()),
					logEntry.getFileCount(), FileUtils.byteCountToDisplaySize(logEntry.getFilesContentLength()));
			if (logEntry.getComment().length() > 0)
			{
				System.out.printf("\tComment: %s%n", logEntry.getComment());
			}
			displayCounts(logEntry.getModificationCounts());
			Console.newLine();
		}
	}

	private void displayCounts(ModificationCounts modificationCounts)
	{
		if (modificationCounts == null)
		{
			return;
		}

		String message = "";
		if (modificationCounts.getAdded() > 0)
		{
			message += "" + modificationCounts.getAdded() + " added, ";
		}

		if (modificationCounts.getCopied() > 0)
		{
			message += "" + modificationCounts.getCopied() + " copied, ";
		}

		if (modificationCounts.getDuplicated() > 0)
		{
			message += "" + modificationCounts.getDuplicated() + " duplicated, ";
		}

		if (modificationCounts.getDateModified() > 0)
		{
			message += "" + modificationCounts.getDateModified() + " date modified, ";
		}

		if (modificationCounts.getContentModified() > 0)
		{
			message += "" + modificationCounts.getContentModified() + " content modified, ";
		}

		if (modificationCounts.getRenamed() > 0)
		{
			message += "" + modificationCounts.getRenamed() + " renamed, ";
		}

		if (modificationCounts.getDeleted() > 0)
		{
			message += "" + modificationCounts.getDeleted() + " deleted, ";
		}

		message = message.replaceAll(", $", "");
		System.out.printf("\t%s%n", message);
	}
}
