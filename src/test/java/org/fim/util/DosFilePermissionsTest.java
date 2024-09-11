/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2017  Etienne Vrignaud
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
 * along with Fim.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.fim.util;

import org.fim.model.Context;
import org.fim.tooling.RepositoryTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributes;

import static java.nio.file.StandardOpenOption.CREATE;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class DosFilePermissionsTest {
    private Context context;
    private RepositoryTool tool;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        tool = new RepositoryTool(testInfo);

        context = mock(Context.class);
    }

    @Test
    public void canRetrieveTheStringVersion() {
        DosFileAttributes dosFileAttributes = mock(DosFileAttributes.class);

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

    @Test
    public void canSetPermissions() throws IOException {
        if (IS_OS_WINDOWS) {
            Path file = tool.getRootDir().resolve("file");
            Files.write(file, "file content".getBytes(), CREATE);

            assertCanSetPermissions(file, "A");
            assertCanSetPermissions(file, "HR");
            assertCanSetPermissions(file, "S");
        }
    }

    private void assertCanSetPermissions(Path file, String permissions) throws IOException {
        DosFilePermissions.setPermissions(context, file, permissions);

        DosFileAttributes dosFileAttributes = Files.readAttributes(file, DosFileAttributes.class);
        assertThat(DosFilePermissions.toString(dosFileAttributes)).isEqualTo(permissions);
    }
}
