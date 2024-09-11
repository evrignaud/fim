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

package org.fim;

import org.fim.command.exception.BadFimUsageException;
import org.fim.command.exception.RepositoryException;
import org.fim.model.Context;
import org.fim.tooling.RepositoryTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.nio.file.Path;

import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FimWithoutReposTest {
    private Fim cut;
    private RepositoryTool tool;
    private Context context;
    private Path rootDir;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        tool = new RepositoryTool(testInfo);
        rootDir = tool.getRootDir();
        context = tool.getContext();

        cut = new Fim();
    }

    @Test
    public void canFilterCorrectlyEmptyArgs() {
        String[] filteredArgs = cut.filterEmptyArgs(new String[] { "", "init", "", "-y", "" });
        assertThat(filteredArgs).isEqualTo(new String[] { "init", "-y" });
    }

    @Test
    public void fimRepositoryNotWritable() throws Exception {
        if (IS_OS_WINDOWS) {
            // Ignore this test for Windows
            return;
        }

        tool.setReadOnly(rootDir);
        try {
            assertThrows(RepositoryException.class, () -> {
                cut.run(new String[] { "init", "-y" }, context);
            });
        } finally {
            tool.setReadWrite(rootDir);
        }
    }

    @Test
    public void fimDoesNotExist() throws Exception {
        assertThrows(BadFimUsageException.class, () -> {
            cut.run(new String[] { "status" }, context);
        });
    }

    @Test
    public void canPrintUsage() throws Exception {
        Fim.setCalledFromTest(true);
        Fim.main(new String[] { "help" });
        assertThat(Fim.getExitStatus()).isEqualTo(0);
    }

    @Test
    public void invalidOptionIsDetected() throws Exception {
        Fim.setCalledFromTest(true);
        Fim.main(new String[] { "-9" });
        assertThat(Fim.getExitStatus()).isEqualTo(-1);
    }

    @Test
    public void invalidThreadCountIsDetected() throws Exception {
        assertThrows(BadFimUsageException.class, () -> {
            cut.run(new String[] { "ci", "-y", "-t", "dummy" }, context);
        });
    }

    @Test
    public void masterFimRepositoryDirectoryMustExist() throws Exception {
        assertThrows(BadFimUsageException.class, () -> {
            cut.run(new String[] { "rdup", "-M", "dummy_directory" }, context);
        });
    }

    @Test
    public void canRunVersionCommand() throws Exception {
        cut.run(new String[] { "-v" }, context);
    }

    @Test
    public void canRunHelpCommand() throws Exception {
        cut.run(new String[] { "-h" }, context);
    }

    @Test
    public void noArgumentSpecified() throws Exception {
        assertThrows(BadFimUsageException.class, () -> {
            cut.run(new String[] { "" }, context);
        });
    }

    @Test
    public void noCommandSpecified() throws Exception {
        assertThrows(BadFimUsageException.class, () -> {
            cut.run(new String[] { "-s" }, context);
        });
    }
}
