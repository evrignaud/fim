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

package org.fim.model;

import org.fim.tooling.BuildableContext;
import org.fim.tooling.BuildableState;
import org.fim.tooling.StateAssert;
import org.fim.util.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.lang.System.lineSeparator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.CompareResult.addSeparator;
import static org.fim.model.CompareResult.displayDifferences;
import static org.fim.model.CompareResult.formatModifiedAttributes;
import static org.fim.model.CompareResult.getValue;

public class CompareResultTest extends StateAssert {

    private BuildableContext context;

    private Difference difference;

    @BeforeEach
    public void setUp() {
        context = defaultContext();

        int year = 115;
        int month = 11;
        int day = 14;
        int hrs = 13;
        int min = 12;
        int seconds = 10;
        GregorianCalendar calendar = new GregorianCalendar(year + 1900, month - 1, day, hrs, min, seconds);
        long time = calendar.getTimeInMillis();

        BuildableState s1 = new BuildableState(context).addFiles("file_01");
        BuildableState s2 = s1.clone();

        FileState fileState1 = s1.getFileStates().getFirst();
        fileState1.getFileTime().setCreationTime(time + 1_000);
        fileState1.getFileTime().setLastModified(time + 2_000);

        FileState fileState2 = s2.getFileStates().getFirst();
        fileState2.getFileTime().setCreationTime(time + 1_000);
        fileState2.getFileTime().setLastModified(time + 3_000);

        difference = new Difference(fileState1, fileState2);
    }

    @Test
    public void getValueTest() {
        assertThat(getValue(null, "key1")).isEqualTo("[nothing]");

        HashMap<String, String> map = new HashMap<>();
        map.put("key1", "val1");
        map.put("key2", "val2");

        assertThat(getValue(map, "key1")).isEqualTo("val1");

        assertThat(getValue(map, "_dummy_key_")).isEqualTo("[nothing]");
    }

    @Test
    public void addSeparatorTest() {
        StringBuilder modification;

        addSeparator(difference, (modification = new StringBuilder()));
        assertThat(modification.toString()).isEqualTo("");

        addSeparator(difference, (modification = new StringBuilder("content")));
        assertThat(modification.toString()).isEqualTo("""
                content
                                          \t""");
    }

    @Test
    public void formatModifiedAttributesTest() {
        String modificationStr = "last modified: 2015/11/14 13:12:12 -> 2015/11/14 13:12:13";
        assertThat(formatModifiedAttributes(difference, true)).isEqualTo(" \n                          \t" + modificationStr);

        assertThat(formatModifiedAttributes(difference, false)).isEqualTo(modificationStr);
    }

    @Test
    public void displayDifferencesTest() {
        final AtomicBoolean called = new AtomicBoolean(false);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream oldOut = Logger.out;
        try {
            Logger.out = new PrintStream(outputStream);

            ArrayList<Difference> differences = new ArrayList<>();
            String actionStr = "action: ";
            Consumer<Difference> differenceConsumer = diff -> called.set(true);

            // No differences
            context.setTruncateOutput(0);
            displayDifferences(context, actionStr, differences, differenceConsumer);
            assertThat(called.get()).isEqualTo(false);
            assertThat(outputStream.toString()).isEqualTo("");

            // Only one with output truncated
            called.set(false);
            differences.add(difference);
            context.setTruncateOutput(0);
            displayDifferences(context, actionStr, differences, differenceConsumer);
            assertThat(called.get()).isEqualTo(false);
            assertThat(outputStream.toString()).isEqualTo("");

            // Only one with output set to one line
            called.set(false);
            context.setTruncateOutput(1);
            displayDifferences(context, actionStr, differences, differenceConsumer);
            assertThat(called.get()).isEqualTo(true);
            assertThat(outputStream.toString()).isEqualTo("");

            // Three differences with output set to 2 lines
            differences.add(difference);
            differences.add(difference);
            called.set(false);
            context.setTruncateOutput(2);
            displayDifferences(context, actionStr, differences, differenceConsumer);
            assertThat(called.get()).isEqualTo(true);
            assertThat(outputStream.toString()).isEqualTo(
                    "  [Too many lines. Truncating the output] ..." + lineSeparator() + "action: 1 file more" + lineSeparator());
        } finally {
            Logger.out = oldOut;
        }
    }
}
