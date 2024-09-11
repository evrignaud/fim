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

package org.fim.command;

import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.model.LogResult;
import org.fim.model.State;
import org.fim.tooling.RepositoryTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class PurgeStatesCommandTest {
    private InitCommand initCommand;
    private CommitCommand commitCommand;
    private LogCommand logCommand;
    private PurgeStatesCommand purgeStatesCommand;

    private RepositoryTool tool;
    private Context context;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        tool = new RepositoryTool(testInfo);
        context = tool.getContext();

        initCommand = new InitCommand();
        commitCommand = new CommitCommand();
        purgeStatesCommand = new PurgeStatesCommand();
        logCommand = new LogCommand();
    }

    @Test
    public void canPurgePreviousStates() throws Exception {
        tool.createOneFile();
        State state = (State) initCommand.execute(context);
        assertThat(state.getModificationCounts().getAdded()).isEqualTo(1);

        assertLogSizeIsEqualTo(context, 1);

        tool.createOneFile();
        commitAndAssertModificationCountIsEqualTo(context, 1);

        assertLogSizeIsEqualTo(context, 2);

        tool.createOneFile();
        commitAndAssertModificationCountIsEqualTo(context, 1);

        assertLogSizeIsEqualTo(context, 3);

        int statesPurgedCount = (int) purgeStatesCommand.execute(context);
        assertThat(statesPurgedCount).isEqualTo(2);

        assertLogSizeIsEqualTo(context, 1);

        // We cannot purge more
        statesPurgedCount = (int) purgeStatesCommand.execute(context);
        assertThat(statesPurgedCount).isEqualTo(0);

        assertLogSizeIsEqualTo(context, 1);
    }

    private void commitAndAssertModificationCountIsEqualTo(Context context, int expectedModificationCount) throws Exception {
        CompareResult compareResult = (CompareResult) commitCommand.execute(context);
        assertThat(compareResult.getModificationCounts().getAdded()).isEqualTo(expectedModificationCount);
    }

    private void assertLogSizeIsEqualTo(Context context, int expectedLogSize) throws Exception {
        LogResult logResult = (LogResult) logCommand.execute(context);
        assertThat(logResult.getLogEntries().size()).isEqualTo(expectedLogSize);
    }
}
