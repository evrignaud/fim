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

import java.nio.file.attribute.DosFileAttributes;

import org.junit.Test;
import org.mockito.Mockito;

public class DosFilePermissionsTest
{
	@Test
	public void weCanRetrieveTheStringVersion()
	{
		DosFileAttributes dosFileAttributes = Mockito.mock(DosFileAttributes.class);

		assertThat(DosFilePermissions.toString(dosFileAttributes)).isEqualTo("");

		Mockito.when(dosFileAttributes.isArchive()).thenReturn(true);
		assertThat(DosFilePermissions.toString(dosFileAttributes)).isEqualTo("A");

		Mockito.when(dosFileAttributes.isHidden()).thenReturn(true);
		assertThat(DosFilePermissions.toString(dosFileAttributes)).isEqualTo("AH");

		Mockito.when(dosFileAttributes.isReadOnly()).thenReturn(true);
		assertThat(DosFilePermissions.toString(dosFileAttributes)).isEqualTo("AHR");

		Mockito.when(dosFileAttributes.isSystem()).thenReturn(true);
		assertThat(DosFilePermissions.toString(dosFileAttributes)).isEqualTo("AHRS");
	}
}
