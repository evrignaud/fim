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

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import org.fim.tooling.FimCli;
import org.fim.tooling.IntegrationRepository;
import org.fim.tooling.TestTiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Choosing which copies to keep when 'fim rdup' asks, one duplicate set at a time.
 *
 * This prompt is the one place where fim deletes the user's own files rather than its own bookkeeping, and it
 * had no coverage from the command line at all. The existing tests either passed -y, which skips the asking
 * and keeps the first of each set, or called the selection method directly with a string.
 *
 * The answer says which files to preserve, not which to remove, and that inversion is worth being careful
 * about: reading it the wrong way round would delete exactly the files the user meant to keep. Each case here
 * therefore checks both sides, what is gone and what is still there.
 */
@Tag(TestTiers.INTEGRATION)
public class InteractiveRemovalIT {
    private static final String SHARED_CONTENT = "the very same bytes in every copy";

    private IntegrationRepository repository;
    private FimCli fim;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        repository = new IntegrationRepository(testInfo);
        fim = repository.fim();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Choosing by number
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Each copy is listed against the number that preserves it. The names are shown as paths, so the numbers
     * are matched against the end of the line rather than against a bare file name.
     */
    @Test
    public void theCopiesAreListedWithTheNumberUsedToPreserveThem() throws Exception {
        givenThreeCopies();

        assertThat(fim.answering("1").run("rdup")).isZero();

        assertThat(fim.output())
                .containsPattern("\\[1] .*dup-a\\.txt")
                .containsPattern("\\[2] .*dup-b\\.txt")
                .containsPattern("\\[3] .*dup-c\\.txt");
        fim.outputContains("Preserve files [1 - 3, all or a, none or n]:");
    }

    @Test
    public void preservingOneByNumberRemovesTheOthers() throws Exception {
        givenThreeCopies();

        assertThat(fim.answering("1").run("rdup")).isZero();

        assertThat(repository.exists("dup-a.txt")).isTrue();
        assertThat(repository.exists("dup-b.txt")).isFalse();
        assertThat(repository.exists("dup-c.txt")).isFalse();
    }

    /** The number chooses a position in the list, so preserving the last one keeps the last one. */
    @Test
    public void preservingTheLastOneKeepsTheLastOne() throws Exception {
        givenThreeCopies();

        assertThat(fim.answering("3").run("rdup")).isZero();

        assertThat(repository.exists("dup-a.txt")).isFalse();
        assertThat(repository.exists("dup-b.txt")).isFalse();
        assertThat(repository.exists("dup-c.txt")).isTrue();
    }

    /** Several numbers on one line preserve several files. */
    @Test
    public void severalNumbersOnOneLinePreserveSeveralFiles() throws Exception {
        repository.createIdenticalFiles(SHARED_CONTENT, "dup-a.txt", "dup-b.txt", "dup-c.txt", "dup-d.txt");
        fim.succeeds("init", "-y", "-m", "first");

        assertThat(fim.answering("1 3").run("rdup")).isZero();

        assertThat(repository.exists("dup-a.txt")).isTrue();
        assertThat(repository.exists("dup-b.txt")).isFalse();
        assertThat(repository.exists("dup-c.txt")).isTrue();
        assertThat(repository.exists("dup-d.txt")).isFalse();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Choosing all, or none
    // -----------------------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = { "a", "all" })
    public void preservingAllRemovesNothing(String answer) throws Exception {
        givenThreeCopies();

        assertThat(fim.answering(answer).run("rdup")).isZero();

        assertThat(repository.exists("dup-a.txt")).isTrue();
        assertThat(repository.exists("dup-b.txt")).isTrue();
        assertThat(repository.exists("dup-c.txt")).isTrue();
    }

    /**
     * Preserving none removes every copy, the first one included.
     *
     * This is the answer with the most surprising reach, since every other answer keeps at least one file.
     * Worth having written down so that it stays a deliberate choice.
     */
    @ParameterizedTest
    @ValueSource(strings = { "n", "none" })
    public void preservingNoneRemovesEveryCopy(String answer) throws Exception {
        givenThreeCopies();

        assertThat(fim.answering(answer).run("rdup")).isZero();

        assertThat(repository.exists("dup-a.txt")).isFalse();
        assertThat(repository.exists("dup-b.txt")).isFalse();
        assertThat(repository.exists("dup-c.txt")).isFalse();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Answers that cannot be used
    // -----------------------------------------------------------------------------------------------------------------

    /** An answer that is not a number, all or none is asked again rather than guessed at. */
    @ParameterizedTest
    @ValueSource(strings = { "banana", "0", "4", "-1", "" })
    public void anAnswerThatCannotBeUsedIsAskedAgain(String unusable) throws Exception {
        givenThreeCopies();

        assertThat(fim.answering(unusable, "1").run("rdup")).isZero();

        // Asked twice: once for the answer that could not be used, once for the one that could.
        assertThat(countOccurrences(fim.output(), "Preserve files")).isEqualTo(2);

        assertThat(repository.exists("dup-a.txt")).isTrue();
        assertThat(repository.exists("dup-b.txt")).isFalse();
    }

    /**
     * Standard input ending with the question unanswered gives up, rather than looping forever on an answer
     * that is never going to arrive.
     */
    @Test
    public void inputEndingWithoutAnAnswerGivesUpAndRemovesNothing() throws Exception {
        givenThreeCopies();

        assertThat(fim.answering().run("rdup")).isZero();

        assertThat(repository.exists("dup-a.txt")).isTrue();
        assertThat(repository.exists("dup-b.txt")).isTrue();
        assertThat(repository.exists("dup-c.txt")).isTrue();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // More than one duplicate set
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Each set is asked about on its own, and the answers are taken in order.
     *
     * Reading several answers over one run is the reason the selection keeps a single Scanner rather than
     * making one per question: a Scanner reads ahead, so a fresh one would throw away the answers that had
     * already been buffered. This is the test that would notice if that were changed back.
     */
    @Test
    public void eachSetIsAskedAboutSeparately() throws Exception {
        givenTwoSetsOfDifferentSizes();

        // Trim the set that wastes the most, keep the other one whole.
        assertThat(fim.answering("1", "a").run("rdup")).isZero();

        assertThat(countOccurrences(fim.output(), "Preserve files")).isEqualTo(2);

        assertThat(repository.exists("big-a.txt")).isTrue();
        assertThat(repository.exists("big-b.txt")).isFalse();
        assertThat(repository.exists("small-a.txt")).isTrue();
        assertThat(repository.exists("small-b.txt")).isTrue();
    }

    /** Giving up partway through keeps whatever the earlier answers already removed. */
    @Test
    public void givingUpPartwayThroughKeepsWhatWasAlreadyDone() throws Exception {
        givenTwoSetsOfDifferentSizes();

        // One answer for two questions.
        assertThat(fim.answering("1").run("rdup")).isZero();

        assertThat(repository.exists("big-b.txt")).isFalse();
        assertThat(repository.exists("small-a.txt")).isTrue();
        assertThat(repository.exists("small-b.txt")).isTrue();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // What the repository looks like afterwards
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Removing duplicates does not record anything, so the removals show up as deletions until they are
     * committed. Nothing about rdup should quietly write a State.
     */
    @Test
    public void theRemovalsAreNotRecordedUntilCommitted() throws Exception {
        givenThreeCopies();

        assertThat(fim.answering("1").run("rdup")).isZero();
        assertThat(repository.stateCount()).isEqualTo(1);

        fim.succeeds("st");
        fim.outputContains("2 deleted");

        fim.succeeds("ci", "-y", "-m", "after removing duplicates");
        fim.succeeds("st");
        fim.outputContains("Nothing modified");
    }

    /** With nothing duplicated there is nothing to ask about. */
    @Test
    public void nothingDuplicatedMeansNoQuestion() throws Exception {
        repository.createFiles("file-1", "file-2", "file-3");
        fim.succeeds("init", "-y", "-m", "first");

        fim.succeeds("rdup");
        fim.outputDoesNotContain("Preserve files");
        fim.outputContains("No duplicate file found");
    }

    // -----------------------------------------------------------------------------------------------------------------

    /** Three files with the same content, named so that the listed order is the alphabetical one. */
    private void givenThreeCopies() throws Exception {
        repository.createIdenticalFiles(SHARED_CONTENT, "dup-a.txt", "dup-b.txt", "dup-c.txt");
        fim.succeeds("init", "-y", "-m", "first");
    }

    /**
     * Two duplicate sets that waste different amounts of space, so that the order they are asked about in is
     * decided by the default sort rather than left to chance. The larger one comes first.
     */
    private void givenTwoSetsOfDifferentSizes() throws Exception {
        repository.createIdenticalFiles("the larger group", 8 * 1_024, "big-a.txt", "big-b.txt");
        repository.createIdenticalFiles("the smaller group", 2 * 1_024, "small-a.txt", "small-b.txt");
        fim.succeeds("init", "-y", "-m", "first");
    }

    private static int countOccurrences(String text, String fragment) {
        int count = 0;
        int index = text.indexOf(fragment);
        while (index >= 0) {
            count++;
            index = text.indexOf(fragment, index + fragment.length());
        }
        return count;
    }
}
