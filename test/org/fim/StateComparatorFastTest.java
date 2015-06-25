/*
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.fim;

import org.junit.Test;

/**
 * @author evrignaud
 */
public class StateComparatorFastTest extends StateAssert
{
	private StateComparator cut = new StateComparator(CompareMode.FAST);
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

		s2 = s1.rename("file_1", "file_1_1");
		cut.compare(s1, s2);
		assertGotOnlyModifications(cut, Modification.ADDED, Modification.DELETED);
		assertFilesModified(cut, Modification.DELETED, "file_1");
		assertFilesModified(cut, Modification.ADDED, "file_1_1");

		s2 = s1.copy("file_1", "file_1_1");
		cut.compare(s1, s2);
		assertOnlyFilesAdded(cut, "file_1_1");

		s2 = s1.delete("file_1");
		cut.compare(s1, s2);
		assertOnlyFileDeleted(cut, "file_1");
	}
}
