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
package org.fim;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

public class FimTest
{
	@Rule
	public final ExpectedSystemExit exit = ExpectedSystemExit.none();

	private Fim cut;

	@Before
	public void setUp()
	{
		cut = new Fim();
	}

	@Test
	public void weCanPrintUsage()
	{
		Fim.printUsage();
	}

	@Test
	public void weCanRunVersionCommand() throws Exception
	{
		exit.expectSystemExitWithStatus(0);
		cut.main(new String[]{"-v"});
	}

	@Test
	public void weCanRunHelpCommand() throws Exception
	{
		exit.expectSystemExitWithStatus(0);
		cut.main(new String[]{"-h"});
	}

	@Test
	public void noArgumentSpecified() throws Exception
	{
		exit.expectSystemExitWithStatus(-1);
		cut.main(new String[]{""});
	}

	@Test
	public void doNotHashOptionWithoutCommand() throws Exception
	{
		exit.expectSystemExitWithStatus(-1);
		cut.main(new String[]{"-n"});
	}

	@Test
	public void fastModeOptionWithoutCommand() throws Exception
	{
		exit.expectSystemExitWithStatus(-1);
		cut.main(new String[]{"-f"});
	}

	@Test
	public void superFastModeOptionWithoutCommand() throws Exception
	{
		exit.expectSystemExitWithStatus(-1);
		cut.main(new String[]{"-s"});
	}

	@Test
	public void invalidOptionIsDetected() throws Exception
	{
		exit.expectSystemExitWithStatus(-1);
		cut.main(new String[]{"-0"});
	}
}
