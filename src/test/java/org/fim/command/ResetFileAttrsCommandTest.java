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
package org.fim.command;

import org.apache.commons.io.FileUtils;
import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.model.State;
import org.fim.tooling.RepositoryTool;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.HashMode.hashAll;

public class ResetFileAttrsCommandTest {
    private static Path rootDir = Paths.get("target/" + ResetFileAttrsCommandTest.class.getSimpleName());

    private InitCommand initCommand;
    private StatusCommand statusCommand;
    private ResetFileAttributesCommand resetFileAttributesCommand;

    private RepositoryTool tool;

    @Before
    public void setup() throws IOException {
        FileUtils.deleteDirectory(rootDir.toFile());
        Files.createDirectories(rootDir);

        initCommand = new InitCommand();
        statusCommand = new StatusCommand();
        resetFileAttributesCommand = new ResetFileAttributesCommand();

        tool = new RepositoryTool(rootDir);
    }

    @Test
    public void weCanResetFileAttributes() throws Exception {
        Context context = tool.createContext(hashAll, true);

        tool.createASetOfFiles(5);

        State state = (State) initCommand.execute(context);
        assertThat(state.getModificationCounts().getAdded()).isEqualTo(5);

        int fileResetCount = (int) resetFileAttributesCommand.execute(context);
        assertThat(fileResetCount).isEqualTo(0);

        doSomeModifications();

        CompareResult compareResult = (CompareResult) statusCommand.execute(context);
        assertThat(compareResult.modifiedCount()).isEqualTo(3);
        assertThat(compareResult.getDateModified().size()).isEqualTo(IS_OS_WINDOWS ? 3 : 2);
        assertThat(compareResult.getAttributesModified().size()).isEqualTo(IS_OS_WINDOWS ? 0 : 1);

        fileResetCount = (int) resetFileAttributesCommand.execute(context);
        assertThat(fileResetCount).isEqualTo(3);

        compareResult = (CompareResult) statusCommand.execute(context);
        assertThat(compareResult.modifiedCount()).isEqualTo(0);
    }

    private void doSomeModifications() throws IOException {
        tool.sleepSafely(1_000); // Ensure to increase lastModified at least of 1 second

        tool.touchCreationTime("file01");
        tool.setPermissions("file01", "rwx------", "AHRS");

        tool.touchLastModified("file02");
        tool.setPermissions("file02", "r-x------", "H");

        tool.touchLastModified("file03");
        tool.setPermissions("file03", "r-xr-x---", "R");
    }
}
