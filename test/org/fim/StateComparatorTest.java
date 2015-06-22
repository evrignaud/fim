package org.fim;

import java.text.ParseException;
import java.util.List;

import org.assertj.core.api.AbstractBooleanAssert;
import org.assertj.core.api.Assertions;
import org.fim.model.State;
import org.junit.Test;

/**
 * Created by evrignaud on 21/06/15.
 */
public class StateComparatorTest
{
	@Test
	public void weCanCompareFull() throws ParseException
	{
		StateComparator cut = new StateComparator(CompareMode.FULL);

		State state1 = new StateBuilder()
				.addFile("dir1/file1", "2015-01-01", "File1 Content")
				.addFile("dir2/file2", "2015-01-02", "File2 Content")
				.build();

		State state2 = new StateBuilder()
				.addFile("dir1/file1", "2015-01-01", "File1 Content")
				.addFile("dir2/file2", "2015-01-02", "File2 Content")
				.build();

		cut.compare(state1, state2);
		AssertNothingModified(cut);

		State state3 = new StateBuilder()
				.addFile("dir1/file1", "2015-01-02", "File1 Content")
				.addFile("dir2/file2", "2015-01-02", "File2 Content")
				.build();

		cut.compare(state1, state2);
		AssertOnlyFileModified(cut, "dir1/file1");
	}

	private void AssertNothingModified(StateComparator comparator)
	{
		Assertions.assertThat(comparator.somethingModified()).isFalse();
	}

	private void AssertOnlyFileModified(StateComparator comparator, String... fileName)
	{
		assertListEmpty(comparator.getAdded());
		assertListEmpty(comparator.getDeleted());
		assertListEmpty(comparator.getContentModified());
		assertListEmpty(comparator.getDuplicated());
		assertListEmpty(comparator.getMoved());


	}

	private AbstractBooleanAssert<?> assertListEmpty(List<?> list)
	{
		return Assertions.assertThat(list.isEmpty()).isTrue();
	}

	@Test
	public void weCanCompareFast()
	{
		StateComparator cut = new StateComparator(CompareMode.FAST);

	}
}
