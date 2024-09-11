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

import org.fim.command.CommitCommand;
import org.fim.command.InitCommand;
import org.fim.command.RollbackCommand;
import org.fim.command.StatusCommand;
import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.model.HashMode;
import org.fim.model.ModificationCounts;
import org.fim.model.State;
import org.fim.tooling.RepositoryTool;
import org.fim.util.TestAllHashModes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.HashMode.hashAll;
import static org.fim.model.HashMode.hashMediumBlock;
import static org.fim.model.HashMode.hashSmallBlock;

public class FileGrowingTest {
    private InitCommand initCommand;
    private StatusCommand statusCommand;
    private CommitCommand commitCommand;
    private RollbackCommand rollbackCommand;

    private RepositoryTool tool;
    private Path rootDir;

    private TestInfo testInfo;

    @BeforeEach
    void init(TestInfo testInfo) {
        this.testInfo = testInfo;
    }

    public void setUp(HashMode hashMode) throws IOException {
        tool = new RepositoryTool(testInfo, hashMode);
        tool.useFixedThreadCount();
        rootDir = tool.getRootDir();

        initCommand = new InitCommand();
        statusCommand = new StatusCommand();
        commitCommand = new CommitCommand();
        rollbackCommand = new RollbackCommand();
    }

    @TestAllHashModes
    public void canDetectAGrowingFile(HashMode hashMode) throws Exception {
        setUp(hashMode);

        Context context = tool.getContext();

        Path file01 = rootDir.resolve("file01");
        tool.createFile(file01, 42_682_889);

        State state = (State) initCommand.execute(context);

        assertThat(state.getModificationCounts().getAdded()).isEqualTo(1);
        assertThat(state.getFileCount()).isEqualTo(1);

        tool.appendFileContent(file01, "toto");

        CompareResult compareResult = (CompareResult) statusCommand.execute(context);
        ModificationCounts modificationCounts = compareResult.getModificationCounts();
        assertThat(compareResult.modifiedCount()).isEqualTo(1);
        assertThat(modificationCounts.getContentModified()).isEqualTo(1);

        compareResult = (CompareResult) commitCommand.execute(context);
        assertThat(compareResult.modifiedCount()).isEqualTo(1);
        modificationCounts = compareResult.getModificationCounts();
        assertThat(modificationCounts.getContentModified()).isEqualTo(1);

        if (hashMode == hashAll || hashMode == hashMediumBlock) {
            // Rollback the last commit and commit again in super-fast mode
            rollbackCommand.execute(context);

            Context superFastModeContext = tool.createContext(hashSmallBlock, true);
            superFastModeContext.setComment("Using hash mode " + hashSmallBlock);

            compareResult = (CompareResult) statusCommand.execute(superFastModeContext);
            modificationCounts = compareResult.getModificationCounts();
            assertThat(compareResult.modifiedCount()).isEqualTo(1);
            assertThat(modificationCounts.getContentModified()).isEqualTo(1);

            compareResult = (CompareResult) commitCommand.execute(superFastModeContext);
            assertThat(compareResult.modifiedCount()).isEqualTo(1);
            modificationCounts = compareResult.getModificationCounts();
            assertThat(modificationCounts.getContentModified()).isEqualTo(1);
        }
    }
}
