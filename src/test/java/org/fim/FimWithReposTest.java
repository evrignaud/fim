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
 * along with Fim.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.fim;

import org.fim.command.exception.BadFimUsageException;
import org.fim.model.Context;
import org.fim.tooling.RepositoryTool;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class FimWithReposTest {
    private Fim cut;
    private RepositoryTool tool;
    private Context context;
    private Path rootDir;

    @Before
    public void setUp() throws Exception {
        tool = new RepositoryTool(this.getClass());
        rootDir = tool.getRootDir();
        context = tool.getContext();

        cut = new Fim();
        initRepoAndCreateOneFile();
    }

    @Test(expected = BadFimUsageException.class)
    public void fimRepositoryAlreadyExist() throws Exception {
        cut.run(new String[]{"init", "-y"}, context);
    }

    @Test
    public void manyValidOptions() throws Exception {
        cut.run(new String[]{"st", "-s", "-o", "1", "-e", "-t", "1"}, context);
    }

    @Test
    public void canCommitUsingMultipleThreads() throws Exception {
        cut.run(new String[]{"ci", "-y", "-t", "4"}, context);
        assertThat(context.getThreadCount()).isEqualTo(4);
    }

    @Test
    public void canCommitUsingFim() throws Exception {
        cut.run(new String[]{"ci", "-y"}, context);
    }

    @Test
    public void canCommitFromASubDirectory() throws Exception {
        String subdir = "sub-dir";
        Path subdirPath = rootDir.resolve(subdir);
        Files.createDirectories(subdirPath);
        cut.run(new String[]{"ci", "-y", "-d", subdirPath.toString()}, context);
        assertThat(context.getCurrentDirectory()).isEqualTo(subdirPath);
    }

    @Test
    public void negativeOutputTruncatingIsSetToZero() throws Exception {
        cut.run(new String[]{"ci", "-y", "-o", "-1"}, context);
        assertThat(context.getTruncateOutput()).isEqualTo(0);
    }

    @Test
    public void doNotHashOption() throws Exception {
        cut.run(new String[]{"status", "-n"}, context);
    }

    @Test
    public void fastModeOption() throws Exception {
        cut.run(new String[]{"status", "-f"}, context);
    }

    @Test
    public void superFastModeOption() throws Exception {
        cut.run(new String[]{"status", "-s"}, context);
    }

    @Test
    public void diffAliasIsAllowed() throws Exception {
        cut.run(new String[]{"diff", "-s"}, context);
    }

    @Test
    public void attributesIgnoredIsAllowed() throws Exception {
        cut.run(new String[]{"status", "-s", "-i", "attrs"}, context);
    }

    @Test
    public void datesIgnoredIsAllowed() throws Exception {
        cut.run(new String[]{"status", "-s", "-i", "dates"}, context);
    }

    @Test
    public void renamedIgnoredIsAllowed() throws Exception {
        cut.run(new String[]{"status", "-s", "-i", "renamed"}, context);
    }

    @Test
    public void allIgnoredIsAllowed() throws Exception {
        cut.run(new String[]{"status", "-s", "-i", "all"}, context);
    }

    @Test(expected = BadFimUsageException.class)
    public void badIgnoredIsNotAllowed() throws Exception {
        cut.run(new String[]{"status", "-s", "-i", "bad"}, context);
    }

    private void initRepoAndCreateOneFile() throws Exception {
        cut.run(new String[]{"init", "-y"}, context);
        tool.createOneFile();
    }
}
