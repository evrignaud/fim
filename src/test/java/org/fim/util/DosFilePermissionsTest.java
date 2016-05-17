/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2016  Etienne Vrignaud
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

import org.apache.commons.io.FileUtils;
import org.fim.model.Context;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.DosFileAttributes;

import static java.nio.file.StandardOpenOption.CREATE;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class DosFilePermissionsTest {
    private static Path rootDir = Paths.get("target/" + DosFilePermissionsTest.class.getSimpleName());
    private Context context;

    @BeforeClass
    public static void setupOnce() throws IOException {
        FileUtils.deleteDirectory(rootDir.toFile());
        Files.createDirectories(rootDir);
    }

    @Before
    public void setup() {
        context = mock(Context.class);
    }

    @Test
    public void weCanRetrieveTheStringVersion() {
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
    public void weCanSetPermissions() throws IOException {
        if (IS_OS_WINDOWS) {
            Path file = rootDir.resolve("file");
            Files.write(file, "file content".getBytes(), CREATE);

            assertWeCanSetPermissions(file, "A");
            assertWeCanSetPermissions(file, "HR");
            assertWeCanSetPermissions(file, "S");
        }
    }

    private void assertWeCanSetPermissions(Path file, String permissions) throws IOException {
        DosFilePermissions.setPermissions(context, file, permissions);

        DosFileAttributes dosFileAttributes = Files.readAttributes(file, DosFileAttributes.class);
        assertThat(DosFilePermissions.toString(dosFileAttributes)).isEqualTo(permissions);
    }
}
