package org.fim;

import org.junit.Test;

/**
 * Created by evrignaud on 21/06/15.
 */
public class StateComparatorTest extends StateAssert
{
	@Test
	public void weCanDo_FULL_CompareOnSimpleOperations()
	{
		StateComparator cut = new StateComparator(CompareMode.FULL);

		BuildableState s1;
		BuildableState s2;

		s1 = new BuildableState().addFiles("file_1", "file_2", "file_3", "file_4");

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
		assertOnlyFileRenamed(cut, "file_1_1");

		s2 = s1.copy("file_1", "file_1_1");
		cut.compare(s1, s2);
		assertOnlyFileDuplicated(cut, "file_1_1");

		s2 = s1.delete("file_1");
		cut.compare(s1, s2);
		assertOnlyFileDeleted(cut, "file_1");
	}

	@Test
	public void weCanDo_FAST_CompareOnSimpleOperations()
	{
		StateComparator cut = new StateComparator(CompareMode.FAST);

		BuildableState s1;
		BuildableState s2;

		s1 = new BuildableState().addFiles("file_1", "file_2", "file_3", "file_4");

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
