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
package org.fim.model;

import static java.lang.System.lineSeparator;
import static org.assertj.core.api.Assertions.*;
import static org.fim.model.CompareResult.addSeparator;
import static org.fim.model.CompareResult.displayDifferences;
import static org.fim.model.CompareResult.formatModifiedAttributes;
import static org.fim.model.CompareResult.getValue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.fim.tooling.BuildableContext;
import org.fim.tooling.BuildableState;
import org.fim.tooling.StateAssert;
import org.junit.Before;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;

public class CompareResultTest extends StateAssert
{
	private GregorianCalendar calendar;
	private long time;

	private BuildableContext context;

	private FileState fileState1;
	private FileState fileState2;

	private Difference difference;

	@Before
	public void setup()
	{
		context = defaultContext();

		int year = 115;
		int month = 11;
		int day = 14;
		int hrs = 13;
		int min = 12;
		int seconds = 10;
		calendar = new GregorianCalendar(year + 1900, month - 1, day, hrs, min, seconds);
		time = calendar.getTimeInMillis();

		BuildableState s1 = new BuildableState(context).addFiles("file_01");
		BuildableState s2 = s1.clone();

		fileState1 = s1.getFileStates().get(0);
		fileState1.getFileTime().setCreationTime(time + 1);
		fileState1.getFileTime().setLastModified(time + 2);

		fileState2 = s2.getFileStates().get(0);
		fileState2.getFileTime().setCreationTime(time + 1);
		fileState2.getFileTime().setLastModified(time + 3);

		difference = new Difference(fileState1, fileState2);
	}

	@Test
	public void getValueTest()
	{
		assertThat(getValue(null, "key1")).isEqualTo("[nothing]");

		HashMap<String, String> map = new HashMap<>();
		map.put("key1", "val1");
		map.put("key2", "val2");

		assertThat(getValue(map, "key1")).isEqualTo("val1");

		assertThat(getValue(map, "_dummy_key_")).isEqualTo("[nothing]");
	}

	@Test
	public void addSeparatorTest()
	{
		StringBuilder modification;

		addSeparator(difference, (modification = new StringBuilder("")));
		assertThat(modification.toString()).isEqualTo("");

		addSeparator(difference, (modification = new StringBuilder("content")));
		assertThat(modification.toString()).isEqualTo("content\n                          \t");
	}

	@Test
	public void formatModifiedAttributesTest()
	{
		String modificationStr = "lastModified: 2015/11/14 13:12:10 -> 2015/11/14 13:12:10";
		assertThat(formatModifiedAttributes(difference, true)).isEqualTo(" \n                          \t" + modificationStr);

		assertThat(formatModifiedAttributes(difference, false)).isEqualTo(modificationStr);
	}

	@Test
	public void displayDifferencesTest()
	{
		final AtomicBoolean called = new AtomicBoolean(false);

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		PrintStream printStream = new PrintStream(outputStream);

		ArrayList<Difference> differences = new ArrayList<>();
		String actionStr = "action: ";
		Consumer<Difference> differenceConsumer = diff -> called.set(true);

		// No differences
		context.setTruncateOutput(0);
		displayDifferences(printStream, context, actionStr, differences, differenceConsumer);
		assertThat(called.get()).isEqualTo(false);
		assertThat(outputStream.toString()).isEqualTo("");

		// Only one with output truncated
		called.set(false);
		differences.add(difference);
		context.setTruncateOutput(0);
		displayDifferences(printStream, context, actionStr, differences, differenceConsumer);
		assertThat(called.get()).isEqualTo(false);
		assertThat(outputStream.toString()).isEqualTo("");

		// Only one with output set to one line
		called.set(false);
		context.setTruncateOutput(1);
		displayDifferences(printStream, context, actionStr, differences, differenceConsumer);
		assertThat(called.get()).isEqualTo(true);
		assertThat(outputStream.toString()).isEqualTo("");

		// Three differences with output set to 2 lines
		differences.add(difference);
		differences.add(difference);
		called.set(false);
		context.setTruncateOutput(2);
		displayDifferences(printStream, context, actionStr, differences, differenceConsumer);
		assertThat(called.get()).isEqualTo(true);
		assertThat(outputStream.toString()).isEqualTo("  [Too many lines. Truncating the output] ..." + lineSeparator() + "action: 1 file more" + lineSeparator());
	}
}
