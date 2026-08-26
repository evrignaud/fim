/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2026 Etienne Vrignaud
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

package org.fim.command;

import org.fim.command.exception.BadFimUsageException;
import org.fim.model.Context;
import org.fim.model.DuplicateResult;
import org.fim.model.HashMode;
import org.fim.model.OutputType;
import org.fim.tooling.RepositoryTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FindDuplicatesCommandTest {
    private Context context;

    private FindDuplicatesCommand findDuplicatesCommand;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        RepositoryTool tool = new RepositoryTool(testInfo);
        context = tool.getContext();

        findDuplicatesCommand = new FindDuplicatesCommand();
    }

    @Test
    public void cannotRemoveAndDisplayInCsv() throws Exception {
        context.setRemoveDuplicates(true);
        context.setOutputType(OutputType.csv);

        assertThrows(BadFimUsageException.class, () -> {
            findDuplicatesCommand.execute(context);
        });
    }

    @Test
    public void cannotRemoveAndDisplayInJson() throws Exception {
        // Req 8.2: non-human output + removeDuplicates throws
        context.setRemoveDuplicates(true);
        context.setOutputType(OutputType.json);

        assertThrows(BadFimUsageException.class, () -> {
            findDuplicatesCommand.execute(context);
        });
    }

    @Test
    public void dontHashModeThrows() throws Exception {
        // Req 1.1: dontHash mode rejected
        context.setHashMode(HashMode.dontHash);

        assertThrows(BadFimUsageException.class, () -> {
            findDuplicatesCommand.execute(context);
        });
    }

    @Test
    public void canFindDuplicatesWithDefaultSettings() throws Exception {
        // Req 1.3, 1.4: generates new state and displays result
        DuplicateResult result = (DuplicateResult) findDuplicatesCommand.execute(context);
        assertThat(result).isNotNull();
        assertThat(result.getDuplicatedFilesCount()).isEqualTo(0);
    }

    @Test
    public void calledFromTestSkipsSafetyConfirmation() throws Exception {
        // Req 9.3: calledFromTest skips safety prompt
        context.setRemoveDuplicates(true);
        context.setAlwaysYes(true);
        context.setCalledFromTest(true);

        // Should not throw — safety prompt is skipped
        DuplicateResult result = (DuplicateResult) findDuplicatesCommand.execute(context);
        assertThat(result).isNotNull();
    }

    @Test
    public void safetyConfirmationTriggeredWhenNotCalledFromTest() throws Exception {
        // Req 9.1, 9.2: safety prompt shown when removeDuplicates + alwaysYes + !calledFromTest
        // Since System.in is empty, Scanner throws NoSuchElementException
        context.setRemoveDuplicates(true);
        context.setAlwaysYes(true);
        context.setCalledFromTest(false);

        assertThrows(java.util.NoSuchElementException.class, () -> {
            findDuplicatesCommand.execute(context);
        });
    }
}
