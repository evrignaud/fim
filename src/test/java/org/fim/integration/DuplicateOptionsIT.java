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

package org.fim.integration;

import org.fim.tooling.FimCli;
import org.fim.tooling.IntegrationRepository;
import org.fim.tooling.TestTiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The options that shape duplicate detection, driven from the command line.
 *
 * None of --sort, --order, --include, --exclude, --output-type or -l had any coverage through the command
 * line, and -l had none at all. Sorting was covered by setting the sort method on a Context and handing
 * synthetic results to a comparator, which leaves the parsing of the option value, its rejection when
 * invalid, and its effect on a repository that was actually scanned, all unverified.
 *
 * The three duplicate sets below are chosen so that the four sort methods each produce a different order.
 * Ordering by wasted space, by number of files, by file size and by path would be indistinguishable on a
 * fixture where the biggest set were also the most numerous one.
 */
@Tag(TestTiers.INTEGRATION)
public class DuplicateOptionsIT {
    /** Three copies of a middling file, in a directory that sorts first. */
    private static final int SET_A_SIZE = 6 * 1_024;
    /** Two copies of the largest file, in a directory that sorts last. */
    private static final int SET_B_SIZE = 10 * 1_024;
    /** Four copies of the smallest file, in a directory that sorts in the middle. */
    private static final int SET_C_SIZE = 2 * 1_024;

    private IntegrationRepository repository;
    private FimCli fim;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        repository = new IntegrationRepository(testInfo);
        fim = repository.fim();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // --sort and --order
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Wasted space is what a set costs: the size of the file times the number of extra copies. Set A wastes
     * 2 x 6 KB, B wastes 1 x 10 KB, C wastes 3 x 2 KB.
     */
    @Test
    public void sortingOnWastedSpacePutsTheCostliestSetFirst() throws Exception {
        givenThreeDuplicateSets();

        assertThat(setOrderOf("--sort", "wasted")).containsExactly("aaa", "zzz", "mmm");
    }

    @Test
    public void sortingOnTheNumberOfFilesPutsTheMostNumerousSetFirst() throws Exception {
        givenThreeDuplicateSets();

        assertThat(setOrderOf("--sort", "number")).containsExactly("mmm", "aaa", "zzz");
    }

    @Test
    public void sortingOnFileSizePutsTheLargestFileFirst() throws Exception {
        givenThreeDuplicateSets();

        assertThat(setOrderOf("--sort", "size")).containsExactly("zzz", "aaa", "mmm");
    }

    /**
     * Sorting on path is what groups the duplicates of one directory together, which is the point of the
     * option: it is the order to use when you intend to clean up by hand.
     */
    @Test
    public void sortingOnPathGroupsTheSetsByDirectory() throws Exception {
        givenThreeDuplicateSets();

        assertThat(setOrderOf("--sort", "path")).containsExactly("zzz", "mmm", "aaa");
        assertThat(setOrderOf("--sort", "path", "--order", "asc")).containsExactly("aaa", "mmm", "zzz");
    }

    @Test
    public void theDefaultIsWastedSpaceDescending() throws Exception {
        givenThreeDuplicateSets();

        assertThat(setOrderOf()).containsExactly("aaa", "zzz", "mmm");
    }

    @Test
    public void ascendingOrderReversesEverySortMethod() throws Exception {
        givenThreeDuplicateSets();

        assertThat(setOrderOf("--sort", "wasted", "--order", "asc")).containsExactly("mmm", "zzz", "aaa");
        assertThat(setOrderOf("--sort", "number", "--order", "asc")).containsExactly("zzz", "aaa", "mmm");
        assertThat(setOrderOf("--sort", "size", "--order", "asc")).containsExactly("mmm", "aaa", "zzz");
    }

    /** The value is lowercased before it is looked up, so the option is forgiving about case. */
    @Test
    public void theSortValueIsCaseInsensitive() throws Exception {
        givenThreeDuplicateSets();

        assertThat(setOrderOf("--sort", "NUMBER")).containsExactly("mmm", "aaa", "zzz");
        assertThat(setOrderOf("--sort", "size", "--order", "ASC")).containsExactly("mmm", "aaa", "zzz");
    }

    @Test
    public void anUnknownSortMethodIsRefused() throws Exception {
        givenThreeDuplicateSets();

        fim.failsAsBadUsage("fdup", "--sort", "sideways");
        fim.outputContains("Unsupported sort method 'sideways'");
    }

    @Test
    public void anUnknownSortOrderIsRefused() throws Exception {
        givenThreeDuplicateSets();

        fim.failsAsBadUsage("fdup", "--order", "sideways");
        fim.outputContains("Unsupported sort order 'sideways'");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // --include and --exclude
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void excludingAFileTypeLeavesItsSetOut() throws Exception {
        givenThreeDuplicateSets();

        assertThat(setOrderOf("--exclude", "*.tmp")).containsExactly("aaa", "zzz");
    }

    @Test
    public void includingAFileTypeKeepsOnlyThatSet() throws Exception {
        givenThreeDuplicateSets();

        assertThat(setOrderOf("--include", "*.jpg")).containsExactly("aaa");
    }

    @Test
    public void severalPatternsAreSeparatedByAColon() throws Exception {
        givenThreeDuplicateSets();

        assertThat(setOrderOf("--include", "*.jpg:*.png")).containsExactly("aaa", "zzz");
    }

    @Test
    public void excludeIsAppliedAfterInclude() throws Exception {
        givenThreeDuplicateSets();

        assertThat(setOrderOf("--include", "*.jpg:*.png", "--exclude", "*.png")).containsExactly("aaa");
    }

    /**
     * The patterns are matched against the name of the file and never against the directory holding it, even
     * though the option help talks about directories. Naming a directory therefore matches nothing.
     */
    @Test
    public void aPatternCannotSelectADirectory() throws Exception {
        givenThreeDuplicateSets();

        assertThat(setOrderOf("--include", "aaa")).isEmpty();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // --output-type
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void theHumanOutputDescribesEachSet() throws Exception {
        givenThreeDuplicateSets();

        fim.succeeds("fdup");
        fim.outputContains("Duplicate set #1", "Duplicate set #2", "Duplicate set #3", "of total wasted space");
    }

    @Test
    public void theCsvOutputCarriesAHeaderAndOneRowPerFile() throws Exception {
        givenThreeDuplicateSets();

        fim.succeeds("fdup", "--output-type", "csv");

        List<String> lines = nonEmptyLines(fim.output());
        assertThat(lines.get(0)).isEqualTo("SetIndex,FileIndex,WastedSpace,FilePath,FileName,FileLength,FileType");
        // 3 + 2 + 4 duplicated files, plus the header
        assertThat(lines).hasSize(1 + 9);
        assertThat(lines).anyMatch(line -> line.contains("aaa/a1.jpg") && line.contains("jpg"));
    }

    @Test
    public void theJsonOutputCarriesEverySet() throws Exception {
        givenThreeDuplicateSets();

        fim.succeeds("fdup", "--output-type", "json");

        String output = fim.output().trim();
        assertThat(output).startsWith("[").endsWith("]");
        fim.outputContains("wastedSpace", "fileList", "aaa/a1.jpg", "zzz/b1.png", "mmm/c1.tmp");
    }

    /** A machine-readable format silences the informational output, so that only the result is on stdout. */
    @Test
    public void aMachineReadableFormatSuppressesTheProgressOutput() throws Exception {
        givenThreeDuplicateSets();

        fim.succeeds("fdup", "--output-type", "csv");
        fim.outputDoesNotContain("Searching for duplicate files", "Duplicate set #", "wasted space");
    }

    @Test
    public void anUnknownOutputTypeIsRefused() throws Exception {
        givenThreeDuplicateSets();

        fim.failsAsBadUsage("fdup", "--output-type", "yaml");
        fim.outputContains("Unsupported output type 'yaml'");
    }

    /**
     * Removing duplicates while asking for csv or json is refused rather than served, because the output
     * would describe files as it deletes them and could not be trusted afterwards.
     */
    @ParameterizedTest
    @ValueSource(strings = { "csv", "json" })
    public void removingWhileAskingForAMachineReadableFormatIsRefused(String outputType) throws Exception {
        givenThreeDuplicateSets();

        fim.failsAsBadUsage("rdup", "-y", "--output-type", outputType);
        fim.outputContains("cannot display duplicates in a non human format and remove them");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // -l / --use-last-state
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Without -l the tree is scanned, so a duplicate created after the last commit is found. With -l the last
     * committed State is used instead, and that same duplicate is invisible because it is not in it.
     *
     * This is the whole behaviour of the option, and it had no test of any kind.
     */
    @Test
    public void theLastStateIsUsedInsteadOfScanningTheTree() throws Exception {
        givenThreeDuplicateSets();

        repository.copy("zzz/b1.png", "zzz/b3.png");

        // Scanning sees the new copy: set B now holds three files.
        fim.succeeds("fdup", "--output-type", "csv");
        assertThat(filesOfSetContaining("zzz")).isEqualTo(3);

        // The committed State does not know about it yet.
        fim.succeeds("fdup", "-l", "--output-type", "csv");
        assertThat(filesOfSetContaining("zzz")).isEqualTo(2);

        // Once committed, both agree again.
        fim.succeeds("ci", "-y", "-m", "one more copy");
        fim.succeeds("fdup", "-l", "--output-type", "csv");
        assertThat(filesOfSetContaining("zzz")).isEqualTo(3);
    }

    @Test
    public void usingTheLastStateIsAnnounced() throws Exception {
        givenThreeDuplicateSets();

        fim.succeeds("fdup", "-l");
        fim.outputContains("from the last committed State");

        fim.succeeds("fdup");
        fim.outputDoesNotContain("from the last committed State");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Hash modes
    // -----------------------------------------------------------------------------------------------------------------

    /** Duplicates are found by comparing content, so a mode that hashes nothing cannot serve. */
    @Test
    public void findingDuplicatesWithoutHashingIsRefused() throws Exception {
        givenThreeDuplicateSets();

        fim.failsAsBadUsage("fdup", "-n");
        fim.outputContains("File content hashing mandatory for this command");
    }

    @ParameterizedTest
    @ValueSource(strings = { "-s", "-f" })
    public void duplicatesAreFoundUsingTheBlockHashingModes(String hashModeOption) throws Exception {
        givenThreeDuplicateSets();

        fim.succeeds("fdup", hashModeOption, "--output-type", "csv");
        assertThat(setOrder(fim.output())).containsExactlyInAnyOrder("aaa", "zzz", "mmm");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Removing
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void removingKeepsTheFirstOfEachSet() throws Exception {
        givenThreeDuplicateSets();

        fim.succeeds("rdup", "-y");

        // One file of each set survives, the eight extra copies are gone.
        assertThat(repository.exists("aaa/a1.jpg")).isTrue();
        assertThat(repository.exists("aaa/a2.jpg")).isFalse();
        assertThat(repository.exists("aaa/a3.jpg")).isFalse();
        assertThat(repository.exists("zzz/b1.png")).isTrue();
        assertThat(repository.exists("zzz/b2.png")).isFalse();
        assertThat(repository.exists("mmm/c1.tmp")).isTrue();
        assertThat(repository.exists("mmm/c2.tmp")).isFalse();

        fim.outputContains("Removed 6 files");
    }

    @Test
    public void nothingIsRemovedWhenThereAreNoDuplicates() throws Exception {
        repository.createFiles("one.txt", "two.txt");
        fim.succeeds("init", "-y", "-m", "first");

        fim.succeeds("rdup", "-y");
        fim.outputContains("No duplicate file found");
        assertThat(repository.exists("one.txt")).isTrue();
        assertThat(repository.exists("two.txt")).isTrue();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Fixture
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Three sets whose ordering differs under every sort method:
     *
     * <pre>
     *   set  directory  files  size each  wasted   type
     *   A    aaa        3      6 KB       12 KB    jpg
     *   B    zzz        2      10 KB      10 KB    png
     *   C    mmm        4      2 KB       6 KB     tmp
     * </pre>
     */
    private void givenThreeDuplicateSets() throws Exception {
        repository.createFile("aaa/a1.jpg", "alpha", SET_A_SIZE);
        repository.copy("aaa/a1.jpg", "aaa/a2.jpg");
        repository.copy("aaa/a1.jpg", "aaa/a3.jpg");

        repository.createFile("zzz/b1.png", "beta", SET_B_SIZE);
        repository.copy("zzz/b1.png", "zzz/b2.png");

        repository.createFile("mmm/c1.tmp", "gamma", SET_C_SIZE);
        repository.copy("mmm/c1.tmp", "mmm/c2.tmp");
        repository.copy("mmm/c1.tmp", "mmm/c3.tmp");
        repository.copy("mmm/c1.tmp", "mmm/c4.tmp");

        // A file of its own, so the fixture also proves a unique file is never reported.
        repository.createFile("lonely.txt", "unique");

        fim.succeeds("init", "-y", "-m", "three duplicate sets");
    }

    /**
     * Run fdup with these arguments in csv form, and return the directory of each set in the order reported.
     * The csv output is used because it states the set index explicitly, so the order does not have to be
     * inferred from prose.
     */
    private List<String> setOrderOf(String... arguments) throws Exception {
        String[] full = new String[arguments.length + 3];
        full[0] = "fdup";
        System.arraycopy(arguments, 0, full, 1, arguments.length);
        full[arguments.length + 1] = "--output-type";
        full[arguments.length + 2] = "csv";

        fim.succeeds(full);
        return setOrder(fim.output());
    }

    /** The directory of the first file of each duplicate set, in the order the sets were reported. */
    private static List<String> setOrder(String csvOutput) {
        Map<String, String> firstFileOfSet = new LinkedHashMap<>();
        for (String line : nonEmptyLines(csvOutput)) {
            if (line.startsWith("SetIndex")) {
                continue;
            }
            String[] columns = line.split(",");
            String setIndex = columns[0];
            String filePath = columns[3];
            firstFileOfSet.putIfAbsent(setIndex, filePath);
        }
        return new ArrayList<>(firstFileOfSet.values());
    }

    /** How many files the reported set living in this directory holds. */
    private int filesOfSetContaining(String directory) {
        int count = 0;
        for (String line : nonEmptyLines(fim.output())) {
            if (!line.startsWith("SetIndex") && line.split(",")[3].equals(directory)) {
                count++;
            }
        }
        return count;
    }

    private static List<String> nonEmptyLines(String output) {
        List<String> lines = new ArrayList<>();
        for (String line : output.split("\\R")) {
            if (!line.isBlank()) {
                lines.add(line.trim());
            }
        }
        return lines;
    }
}
