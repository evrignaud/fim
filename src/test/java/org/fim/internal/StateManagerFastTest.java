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
package org.fim.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.fim.model.FileState;
import org.fim.model.State;
import org.fim.tooling.BuildableState;
import org.fim.tooling.StateAssert;
import org.junit.Before;
import org.junit.Test;

public class StateManagerFastTest extends StateAssert
{
	private BuildableState s = new BuildableState();

	private File stateDir;
	private StateManager cut;

	@Before
	public void setup() throws IOException
	{
		stateDir = new File("target", this.getClass().getSimpleName());

		FileUtils.deleteDirectory(stateDir);
		stateDir.mkdirs();

		cut = new StateManager(defaultParameters().compareModeFast(), stateDir);
	}

	@Test
	public void weCanCreateNewState() throws IOException
	{
		int count = 10;
		for (int index = 0; index < count; index++)
		{
			String dirName = "dir_" + index;
			s = s.addFiles(dirName + "/file_1", dirName + "/file_2", dirName + "/file_3");
			cut.createNewState(s);

			assertThat(cut.lastStateNumber).isEqualTo(index + 1);
		}

		cut.findLastStateNumber();
		assertThat(cut.lastStateNumber).isEqualTo(count);

		State result = cut.loadLastState();
		assertAllFileStatesHaveNoHash(result, 30);

		result = cut.loadState(10);
		assertAllFileStatesHaveNoHash(result, 30);

		File nextStateFile = cut.getNextStateFile();
		assertThat(nextStateFile.getName()).isEqualTo("state_11.json.gz");
	}

	private void assertAllFileStatesHaveNoHash(State result, int fileCount)
	{
		assertThat(result.getFileStates().size()).isEqualTo(fileCount);
		for (FileState fileState : result.getFileStates())
		{
			assertThat(fileState.getHash()).isEqualTo(StateGenerator.NO_HASH);
		}
	}
}
