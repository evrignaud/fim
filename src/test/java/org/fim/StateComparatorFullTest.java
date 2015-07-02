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
package org.fim;

import org.fim.tool.BuildableState;
import org.fim.tool.FileNameDiff;
import org.fim.tool.Modification;
import org.fim.tool.StateAssert;
import org.junit.Test;

public class StateComparatorFullTest extends StateAssert
{
	private StateComparator cut = new StateComparator(CompareMode.FULL);
	private BuildableState s1 = new BuildableState().addFiles("file_1", "file_2", "file_3", "file_4");
	private BuildableState s2;

	@Test
	public void weCanDoCompareOnSimpleOperations()
	{
		// Set the same file content
		s2 = s1.setContent("file_1", "file_1");
		cut.compare(s1, s2);
		assertNothingModified(cut);

		s2 = s1.addFiles("file_5");
		cut.compare(s1, s2);
		assertOnlyFilesAdded(cut, "file_5");

		s2 = s1.touch("file_1");
		cut.compare(s1, s2);
		assertOnlyDatesModified(cut, "file_1");

		s2 = s1.appendContent("file_1", "append 1");
		cut.compare(s1, s2);
		assertOnlyContentModified(cut, "file_1");

		s2 = s1.rename("file_1", "file_6");
		cut.compare(s1, s2);
		assertOnlyFileRenamed(cut, new FileNameDiff("file_1", "file_6"));

		s2 = s1.copy("file_1", "file_6");
		cut.compare(s1, s2);
		assertOnlyFileDuplicated(cut, new FileNameDiff("file_1", "file_6"));

		s2 = s1.delete("file_1");
		cut.compare(s1, s2);
		assertOnlyFileDeleted(cut, "file_1");
	}

	@Test
	public void weCanCopyAFileAndChangeDate()
	{
		s2 = s1.copy("file_1", "file_0")
				.copy("file_1", "file_6")
				.touch("file_1");
		cut.compare(s1, s2);
		assertGotOnlyModifications(cut, Modification.DUPLICATED, Modification.DATE_MODIFIED);
		assertFilesModified(cut, Modification.DUPLICATED, new FileNameDiff("file_1", "file_0"), new FileNameDiff("file_1", "file_6"));
		assertFilesModified(cut, Modification.DATE_MODIFIED, "file_1");
	}

	@Test
	public void weCanCopyAFileAndChangeContent()
	{
		s2 = s1.copy("file_1", "file_0")
				.copy("file_1", "file_6")
				.appendContent("file_1", "append 1");
		cut.compare(s1, s2);
		assertGotOnlyModifications(cut, Modification.COPIED, Modification.CONTENT_MODIFIED);
		assertFilesModified(cut, Modification.COPIED, new FileNameDiff("file_1", "file_0"), new FileNameDiff("file_1", "file_6"));
		assertFilesModified(cut, Modification.CONTENT_MODIFIED, "file_1");
	}
}
