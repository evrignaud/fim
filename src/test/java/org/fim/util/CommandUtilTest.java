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

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;

public class CommandUtilTest {
    private List<String> cmdArray;
    private List<String> badArgumentCmdArray;

    @Before
    public void setUp() {
        if (IS_OS_WINDOWS) {
            cmdArray = Arrays.asList("cmd", "/c", "dir /AH");
            badArgumentCmdArray = Arrays.asList("cmd", "/c", "dir /+++");
        } else {
            cmdArray = Arrays.asList("ls", "-la");
            badArgumentCmdArray = Arrays.asList("ls", "-+++");
        }
    }

    @Test
    public void canExecuteACommand() throws Exception {
        String output = CommandUtil.executeCommand(cmdArray);
        Assertions.assertThat(output.length()).isGreaterThan(10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void executingACommandWithWrongArgumentsThrowAnException() throws Exception {
        CommandUtil.executeCommand(badArgumentCmdArray);
    }

    @Test
    public void canExecuteACommandAndGetLines() throws Exception {
        List<String> lines = CommandUtil.executeCommandAndGetLines(cmdArray);
        Assertions.assertThat(lines.size()).isGreaterThan(5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void executingACommandAndGetLinesWithWrongArgumentsThrowAnException() throws Exception {
        CommandUtil.executeCommandAndGetLines(badArgumentCmdArray);
    }
}
