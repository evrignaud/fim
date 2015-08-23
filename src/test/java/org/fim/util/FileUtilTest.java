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
package org.fim.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class FileUtilTest
{
	@Test
	public void weCanGetTheRelativeFileName()
	{
		String relativeFileName = FileUtil.getRelativeFileName("/dir1/dir2/dir3", "/dir1/dir2/dir3/dir4/file1");
		assertThat(relativeFileName).isEqualTo("dir4/file1");

		relativeFileName = FileUtil.getRelativeFileName("/dir5/dir6/dir7", "/dir1/dir2/dir3/dir4/file1");
		assertThat(relativeFileName).isEqualTo("dir1/dir2/dir3/dir4/file1");

		relativeFileName = FileUtil.getRelativeFileName("/dir1/dir2/dir3", "dir4/file1");
		assertThat(relativeFileName).isEqualTo("dir4/file1");
	}
}
