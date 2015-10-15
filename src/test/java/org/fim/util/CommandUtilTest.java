/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2015  Etienne Vrignaud
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

import static org.junit.Assume.assumeTrue;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.SystemUtils;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

public class CommandUtilTest
{
	@Before
	public void setup()
	{
		assumeTrue(!SystemUtils.IS_OS_WINDOWS);
	}

	@Test
	public void weCanExecuteACommand() throws Exception
	{
		String output = CommandUtil.executeCommand(Arrays.asList("ls", "-la"));
		Assertions.assertThat(output.length()).isGreaterThan(10);
	}

	@Test(expected = IllegalArgumentException.class)
	public void executingACommandWithWrongArgumentsThrowAnException() throws Exception
	{
		CommandUtil.executeCommand(Arrays.asList("ls", "-LoL "));
	}

	@Test
	public void weCanExecuteACommandAndGetLines() throws Exception
	{
		List<String> lines = CommandUtil.executeCommandAndGetLines(Arrays.asList("ls", "-la"));
		Assertions.assertThat(lines.size()).isGreaterThan(5);
	}

	@Test(expected = IllegalArgumentException.class)
	public void executingACommandAndGetLinesWithWrongArgumentsThrowAnException() throws Exception
	{
		CommandUtil.executeCommandAndGetLines(Arrays.asList("ls", "-LoL "));
	}
}
