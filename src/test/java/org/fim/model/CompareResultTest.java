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

package org.fim.model;

import org.fim.tooling.BuildableContext;
import org.fim.tooling.BuildableState;
import org.fim.tooling.StateAssert;
import org.fim.util.FormatUtil;
import org.fim.util.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
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

        FileState fileState1 = s1.getFileStates().get(0);
        fileState1.getFileTime().setCreationTime(time + 1_000);
        fileState1.getFileTime().setLastModified(time + 2_000);

        FileState fileState2 = s2.getFileStates().get(0);
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

    // --- Req 16.1: Timestamp/comment header display ---
    @Test
    public void displayChanges_showsTimestampAndCommentHeader() {
        BuildableState lastState = new BuildableState(context).addFiles("file_a");
        long timestamp = new GregorianCalendar(2023, 5, 15, 10, 30, 0).getTimeInMillis();
        lastState.setTimestamp(timestamp);
        lastState.setComment("Test commit");

        CompareResult result = new CompareResult(context, lastState);
        // Add one difference so somethingModified is true
        result.getAdded().add(difference);

        String output = captureOutput(() -> result.displayChanges());

        assertThat(output).contains("Comparing with the last committed state from " + FormatUtil.formatDate(timestamp));
        assertThat(output).contains("Comment: Test commit");
    }

    @Test
    public void displayChanges_showsTimestampWithoutCommentWhenEmpty() {
        BuildableState lastState = new BuildableState(context).addFiles("file_a");
        long timestamp = new GregorianCalendar(2023, 5, 15, 10, 30, 0).getTimeInMillis();
        lastState.setTimestamp(timestamp);
        lastState.setComment("");

        CompareResult result = new CompareResult(context, lastState);

        String output = captureOutput(() -> result.displayChanges());

        assertThat(output).contains("Comparing with the last committed state from " + FormatUtil.formatDate(timestamp));
        assertThat(output).doesNotContain("Comment:");
    }

    // --- Req 16.2: Category labels ---
    @Test
    public void displayChanges_showsCategoryLabels() {
        context.setVerbose(true);
        BuildableState lastState = new BuildableState(context).addFiles("file_a");
        CompareResult result = new CompareResult(context, lastState);

        // Add entries to various categories
        FileState addedFile = createFileState("added_file");
        result.getAdded().add(new Difference(null, addedFile));

        FileState deletedFile = createFileState("deleted_file");
        result.getDeleted().add(new Difference(null, deletedFile));

        String output = captureOutput(() -> result.displayChanges());

        assertThat(output).contains("Added:");
        assertThat(output).contains("Deleted:");
    }

    // --- Req 16.3: Rename arrow formatting ---
    @Test
    public void displayChanges_showsRenameArrowFormatting() {
        context.setVerbose(true);
        BuildableState lastState = new BuildableState(context).addFiles("file_a");
        CompareResult result = new CompareResult(context, lastState);

        FileState prevFile = createFileState("old_name.txt");
        FileState currFile = createFileState("new_name.txt");
        Difference renameDiff = new Difference(prevFile, currFile);
        result.getRenamed().add(renameDiff);

        String output = captureOutput(() -> result.displayChanges());

        // Req 16.3: renamed shows "previous -> new"
        assertThat(output).contains("old_name.txt -> new_name.txt");
    }

    // --- Req 16.4: Copy/duplicate original name display ---
    @Test
    public void displayChanges_showsCopiedOriginalName() {
        context.setVerbose(true);
        BuildableState lastState = new BuildableState(context).addFiles("file_a");
        CompareResult result = new CompareResult(context, lastState);

        FileState origFile = createFileState("original.txt");
        FileState copyFile = createFileState("copy.txt");
        Difference copyDiff = new Difference(origFile, copyFile);
        result.getCopied().add(copyDiff);

        String output = captureOutput(() -> result.displayChanges());

        // Req 16.4: copied shows "current (was original)"
        assertThat(output).contains("copy.txt");
        assertThat(output).contains("(was original.txt)");
    }

    @Test
    public void displayChanges_showsDuplicatedOriginalName() {
        context.setVerbose(true);
        BuildableState lastState = new BuildableState(context).addFiles("file_a");
        CompareResult result = new CompareResult(context, lastState);

        FileState origFile = createFileState("original.txt");
        FileState dupFile = createFileState("duplicate.txt");
        Difference dupDiff = new Difference(origFile, dupFile);
        result.getDuplicated().add(dupDiff);

        String output = captureOutput(() -> result.displayChanges());

        // Req 16.4: duplicated shows "current = original"
        assertThat(output).contains("duplicate.txt = original.txt");
    }

    // --- Req 16.5: Attribute change formatting ---
    @Test
    public void displayChanges_showsAttributeChangeFormatting() {
        context.setVerbose(true);
        BuildableState lastState = new BuildableState(context).addFiles("file_a");
        CompareResult result = new CompareResult(context, lastState);

        FileState prevFile = createFileStateWithAttributes("attrs_file.txt",
                Map.of("PosixFilePermissions", "rwxr-xr-x"));
        FileState currFile = createFileStateWithAttributes("attrs_file.txt",
                Map.of("PosixFilePermissions", "rw-r--r--"));
        // Same time so no time change
        currFile.setFileTime(new FileTime(prevFile.getFileTime()));
        Difference attrDiff = new Difference(prevFile, currFile);
        result.getAttributesModified().add(attrDiff);

        String output = captureOutput(() -> result.displayChanges());

        // Req 16.5: shows "key: oldValue -> newValue"
        assertThat(output).contains("PosixFilePermissions: rwxr-xr-x -> rw-r--r--");
    }

    // --- Req 16.7: Summary line ---
    @Test
    public void displayChanges_showsSummaryLine() {
        BuildableState lastState = new BuildableState(context).addFiles("file_a");
        CompareResult result = new CompareResult(context, lastState);

        result.getAdded().add(new Difference(null, createFileState("f1")));
        result.getAdded().add(new Difference(null, createFileState("f2")));
        result.getDeleted().add(new Difference(null, createFileState("f3")));

        String output = captureOutput(() -> result.displayChanges());

        // Req 16.7: summary lists counts of each modification type
        assertThat(output).contains("2 added");
        assertThat(output).contains("1 deleted");
    }

    @Test
    public void displayChanges_summaryOmitsZeroCountCategories() {
        BuildableState lastState = new BuildableState(context).addFiles("file_a");
        CompareResult result = new CompareResult(context, lastState);

        result.getAdded().add(new Difference(null, createFileState("f1")));

        String output = captureOutput(() -> result.displayChanges());

        assertThat(output).contains("1 added");
        assertThat(output).doesNotContain("deleted");
        assertThat(output).doesNotContain("renamed");
    }

    // --- Req 17.2: truncateOutput=0 suppresses details ---
    @Test
    public void displayChanges_truncateOutputZeroSuppressesDetails() {
        context.setVerbose(true);
        context.setTruncateOutput(0);
        BuildableState lastState = new BuildableState(context).addFiles("file_a");
        CompareResult result = new CompareResult(context, lastState);

        FileState addedFile = createFileState("added_file.txt");
        result.getAdded().add(new Difference(null, addedFile));

        String output = captureOutput(() -> result.displayChanges());

        // Req 17.2: truncateOutput=0 suppresses per-file details but still shows summary
        assertThat(output).doesNotContain("added_file.txt");
        assertThat(output).contains("1 added");
    }

    // --- Req 16.6: sortResults sorts alphabetically ---
    @Test
    public void sortResults_sortsAllCategoriesAlphabetically() {
        CompareResult result = new CompareResult(context, null);

        result.getAdded().add(new Difference(null, createFileState("z_file")));
        result.getAdded().add(new Difference(null, createFileState("a_file")));
        result.getAdded().add(new Difference(null, createFileState("m_file")));

        result.getDeleted().add(new Difference(null, createFileState("beta")));
        result.getDeleted().add(new Difference(null, createFileState("alpha")));

        result.sortResults();

        assertThat(result.getAdded().get(0).getFileState().getFileName()).isEqualTo("a_file");
        assertThat(result.getAdded().get(1).getFileState().getFileName()).isEqualTo("m_file");
        assertThat(result.getAdded().get(2).getFileState().getFileName()).isEqualTo("z_file");

        assertThat(result.getDeleted().get(0).getFileState().getFileName()).isEqualTo("alpha");
        assertThat(result.getDeleted().get(1).getFileState().getFileName()).isEqualTo("beta");
    }

    // --- Req 17.4: getModificationCounts consistency ---
    @Test
    public void getModificationCounts_matchesListSizes() {
        CompareResult result = new CompareResult(context, null);

        result.getAdded().add(new Difference(null, createFileState("f1")));
        result.getAdded().add(new Difference(null, createFileState("f2")));
        result.getDeleted().add(new Difference(null, createFileState("f3")));
        result.getContentModified().add(difference);
        result.getRenamed().add(difference);

        ModificationCounts counts = result.getModificationCounts();

        assertThat(counts.getAdded()).isEqualTo(2);
        assertThat(counts.getDeleted()).isEqualTo(1);
        assertThat(counts.getContentModified()).isEqualTo(1);
        assertThat(counts.getRenamed()).isEqualTo(1);
        assertThat(counts.getCopied()).isEqualTo(0);
        assertThat(counts.getDuplicated()).isEqualTo(0);
        assertThat(counts.getDateModified()).isEqualTo(0);
        assertThat(counts.getAttributesModified()).isEqualTo(0);
    }

    @Test
    public void getModificationCounts_doesNotPopulateCorrupted() {
        // Req 17.4: current implementation does NOT populate corrupted field
        CompareResult result = new CompareResult(context, null);
        result.getCorrupted().add(difference);

        ModificationCounts counts = result.getModificationCounts();
        assertThat(counts.getCorrupted()).isEqualTo(0);
        // But modifiedCount includes corrupted
        assertThat(result.modifiedCount()).isEqualTo(1);
    }

    // --- Req 14.4: Ignore flag parenthetical note ---
    @Test
    public void displayChanges_showsIgnoredNote() {
        context.getIgnored().setAttributesIgnored(true);
        BuildableState lastState = new BuildableState(context).addFiles("file_a");
        CompareResult result = new CompareResult(context, lastState);

        String output = captureOutput(() -> result.displayChanges("Nothing modified"));

        assertThat(output).contains("(Not taking in account file attributes)");
    }

    @Test
    public void displayChanges_showsMultipleIgnoredNotes() {
        context.getIgnored().setAttributesIgnored(true);
        context.getIgnored().setDatesIgnored(true);
        BuildableState lastState = new BuildableState(context).addFiles("file_a");
        CompareResult result = new CompareResult(context, lastState);

        String output = captureOutput(() -> result.displayChanges("Nothing modified"));

        assertThat(output).contains("Not taking in account");
        assertThat(output).contains("file attributes");
        assertThat(output).contains("modification dates");
    }

    // --- Req 16.1: getPreviousFileName returns "?" for null previous ---
    @Test
    public void displayChanges_handlesNullPreviousFileState() {
        context.setVerbose(true);
        BuildableState lastState = new BuildableState(context).addFiles("file_a");
        CompareResult result = new CompareResult(context, lastState);

        // Create a difference with null previous (single-arg constructor)
        FileState currFile = createFileState("renamed_file.txt");
        Difference renameDiff = new Difference(currFile);
        result.getRenamed().add(renameDiff);

        String output = captureOutput(() -> result.displayChanges());

        // When previousFileState is null, "?" is shown
        assertThat(output).contains("? -> renamed_file.txt");
    }

    // --- Task 6.1: Verbose output displays "Corrupted?:" label (Req 5.1) ---

    @Test
    public void displayChanges_showsCorruptedLabel() {
        context.setVerbose(true);
        BuildableState lastState = new BuildableState(context).addFiles("file_a");
        CompareResult result = new CompareResult(context, lastState);

        FileState prevFile = createFileState("corrupted_file.txt");
        FileState currFile = new FileState("corrupted_file.txt", 100,
                new FileTime(prevFile.getFileTime()),
                new FileHash("diff_small", "diff_medium", "diff_full"), null);
        Difference corruptedDiff = new Difference(prevFile, currFile);
        result.getCorrupted().add(corruptedDiff);

        String output = captureOutput(() -> result.displayChanges());

        assertThat(output).contains("Corrupted?:");
        assertThat(output).contains("corrupted_file.txt");
    }

    // --- Task 6.1: Summary line includes "<count> corrupted" (Req 5.2) ---

    @Test
    public void displayChanges_showsCorruptedCountInSummary() {
        BuildableState lastState = new BuildableState(context).addFiles("file_a");
        CompareResult result = new CompareResult(context, lastState);

        result.getCorrupted().add(difference);
        result.getCorrupted().add(difference);

        String output = captureOutput(() -> result.displayChanges());

        assertThat(output).contains("2 corrupted");
    }

    // --- Task 6.1: "Nothing corrupted" displayed when no corruption and no modifications (Req 5.3) ---

    @Test
    public void displayChanges_showsNothingCorruptedMessage() {
        BuildableState lastState = new BuildableState(context).addFiles("file_a");
        CompareResult result = new CompareResult(context, lastState);

        String output = captureOutput(() -> result.displayChanges("Nothing corrupted"));

        assertThat(output).contains("Nothing corrupted");
    }

    // --- Task 6.1: modifiedCount includes corrupted list size (Req 5.4) ---

    @Test
    public void modifiedCount_includesCorruptedSize() {
        CompareResult result = new CompareResult(context, null);

        result.getCorrupted().add(difference);
        result.getCorrupted().add(difference);
        result.getAdded().add(new Difference(null, createFileState("f1")));

        assertThat(result.modifiedCount()).isEqualTo(3);
        assertThat(result.getCorrupted()).hasSize(2);
    }

    // --- Task 6.1: Corrupted list sorted by fileName before display (Req 5.5) ---

    @Test
    public void sortResults_sortsCorruptedByFileName() {
        CompareResult result = new CompareResult(context, null);

        FileState prev1 = createFileState("z_corrupted");
        FileState curr1 = new FileState("z_corrupted", 100, new FileTime(1000, 1000),
                new FileHash("x1", "x2", "x3"), null);
        result.getCorrupted().add(new Difference(prev1, curr1));

        FileState prev2 = createFileState("a_corrupted");
        FileState curr2 = new FileState("a_corrupted", 100, new FileTime(1000, 1000),
                new FileHash("y1", "y2", "y3"), null);
        result.getCorrupted().add(new Difference(prev2, curr2));

        result.sortResults();

        assertThat(result.getCorrupted().get(0).getFileState().getFileName()).isEqualTo("a_corrupted");
        assertThat(result.getCorrupted().get(1).getFileState().getFileName()).isEqualTo("z_corrupted");
    }

    // --- Helper methods ---

    private FileState createFileState(String fileName) {
        return new FileState(fileName, 100, new FileTime(1000, 1000),
                new FileHash("small_" + fileName, "medium_" + fileName, "full_" + fileName), null);
    }

    private FileState createFileStateWithAttributes(String fileName, Map<String, String> attributes) {
        FileState fs = new FileState(fileName, 100, new FileTime(1000, 1000),
                new FileHash("small_" + fileName, "medium_" + fileName, "full_" + fileName),
                new ArrayList<>());
        fs.setFileAttributes(new HashMap<>(attributes));
        return fs;
    }

    private String captureOutput(Runnable action) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream oldOut = Logger.out;
        try {
            Logger.out = new PrintStream(outputStream);
            action.run();
            return outputStream.toString();
        } finally {
            Logger.out = oldOut;
        }
    }
}
