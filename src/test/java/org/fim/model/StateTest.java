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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.fim.tooling.BuildableState;
import org.fim.tooling.StateAssert;
import org.junit.Before;
import org.junit.Test;

public class StateTest extends StateAssert
{
	public static final String MODIFICATION_TIME = "2015/07/23 23:24:10";

	private BuildableState a1;
	private BuildableState a2;
	private BuildableState b;

	@Before
	public void setup()
	{
		a1 = new BuildableState(defaultParameters()).addFiles("file_1", "file_2");
		a2 = a1.cloneState();
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
	public void weCanHashAState() throws ParseException
	{
		fixTimeStamps(a1);

		String a1_hash = a1.hashState();
		assertThat(a1_hash).isEqualTo("9dedf87af7a3b61881dd1c94a58539725b01f2e6e702cfa151d6552156ece9821532f107023410169f093ff55094d15a408827ac4618be10860cb4a3be5b02bf");
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

	private void fixTimeStamps(BuildableState a1) throws ParseException
	{
		// Fix the timeStamps in order that state hash can be verified
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
		Date date = sdf.parse(MODIFICATION_TIME);
		long timestamp = date.getTime();

		a1.setTimestamp(timestamp);
		for (FileState fileState : a1.getFileStates())
		{
			fileState.setLastModified(timestamp);
		}
	}
}
