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

import org.fim.command.exception.BadFimUsageException;
import org.fim.model.Context;
import org.fim.model.OutputType;
import org.fim.tooling.RepositoryTool;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;

public class FindDuplicatesCommandTest {
    private RepositoryTool tool;
    private Path rootDir;
    private Context context;

    private FindDuplicatesCommand findDuplicatesCommand;

    @Before
    public void setUp() throws IOException {
        tool = new RepositoryTool(this.getClass());
        rootDir = tool.getRootDir();
        context = tool.getContext();

        findDuplicatesCommand = new FindDuplicatesCommand();
    }

    @Test(expected = BadFimUsageException.class)
    public void cannotRemoveAndDisplayInCsv() throws Exception {
        context.setRemoveDuplicates(true);
        context.setOutputType(OutputType.csv);

        findDuplicatesCommand.execute(context);
    }
}
