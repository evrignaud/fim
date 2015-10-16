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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Paths;
import java.text.ParseException;
import java.util.Arrays;

import org.fim.tooling.BuildableState;
import org.fim.tooling.StateAssert;
import org.junit.Before;
import org.junit.Test;

public class StateTest extends StateAssert
{
	// public static final String MODIFICATION_TIME = "2015/07/23 23:24:10";
	public static final long MODIFICATION_TIMESTAMP = 1437686650000L; // MODIFICATION_TIME translated in milliseconds

	private BuildableState a1;
	private BuildableState a2;
	private BuildableState b;

	@Before
	public void setup()
	{
		a1 = new BuildableState(defaultContext()).addFiles("file_1", "file_2");
		a2 = a1.clone();
		a2.setTimestamp(a1.getTimestamp());

		b = a1.delete("file_2").addFiles("file_3");
		b.setTimestamp(a1.getTimestamp());
	}

	@Test
	public void equalsIsWorking()
	{
		assertThat(a1).isNotEqualTo(null);

		assertThat(a1).isNotEqualTo("dummy_string");

		assertThat(a1).isEqualTo(a2);
		assertThat(a2).isEqualTo(a1);

		assertThat(a1).isNotEqualTo(b);
		assertThat(b).isNotEqualTo(a1);
	}

	@Test
	public void hashcodeIsWorking()
	{
		assertThat(a1.hashCode()).isEqualTo(a2.hashCode());

		assertThat(a1.hashCode()).isNotEqualTo(b.hashCode());
	}

	@Test
	public void toStringIsWorking()
	{
		assertThat(a1.toString().contains("modelVersion")).isTrue();
	}

	@Test
	public void weCanHashAState() throws ParseException
	{
		fixTimeStamps(a1);

		String a1_hash = a1.hashState();
		assertThat(a1_hash.length()).isEqualTo(128);
		assertThat(a1_hash).isEqualTo("c6685a6deca5a2aa013cc3522840ef2e47685dba9d3ca57f8c6c9def0a81f5bfbf34c65d27240b8fced011beebbddd8aadc07adc5936bcb313579cce37d4491f");
	}

	@Test
	public void hashDependsOnContent() throws ParseException
	{
		fixTimeStamps(a1);
		fixTimeStamps(a2);
		fixTimeStamps(b);

		String a1_hash = a1.hashState();
		String a2_hash = a2.hashState();
		String b_hash = b.hashState();

		assertThat(a1_hash).isEqualTo(a2_hash);

		assertThat(a1_hash).isNotEqualTo(b_hash);
	}

	@Test
	public void aStateCanBeCloned()
	{
		State s = a1.clone();
		assertThat(s.hashState()).isEqualTo(a1.hashState());

		s.getModificationCounts().setAdded(1);
		State clone = s.clone();
		assertThat(clone.getModificationCounts().getAdded()).isEqualTo(1);
	}

	@Test
	public void weCanFilterFilesInside()
	{
		State s = a1.addFiles("dir_1/file_1", "dir_1/file_2", "dir_2/file_1", "dir_2/file_2");

		State filteredState = s.filterDirectory(Paths.get("."), Paths.get("dir_1"), true);

		assertThat(toFileNames(filteredState.getFileStates())).isEqualTo(Arrays.asList("dir_1/file_1", "dir_1/file_2"));
	}

	@Test
	public void weCanFilterFilesOutside()
	{
		State s = a1.addFiles("dir_1/file_1", "dir_1/file_2", "dir_2/file_1", "dir_2/file_2");

		State filteredState = s.filterDirectory(Paths.get("."), Paths.get("dir_1"), false);

		assertThat(toFileNames(filteredState.getFileStates())).isEqualTo(Arrays.asList("dir_2/file_1", "dir_2/file_2", "file_1", "file_2"));
	}

	private void fixTimeStamps(BuildableState s) throws ParseException
	{
		// Fix the timeStamps in order that state hash can be verified

		// SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
		// Date date = sdf.parse(MODIFICATION_TIME);
		// long timestamp = date.getTime();
		long timestamp = MODIFICATION_TIMESTAMP;

		s.setTimestamp(timestamp);
		for (FileState fileState : s.getFileStates())
		{
			fileState.getFileTime().reset(timestamp);
		}
	}
}
