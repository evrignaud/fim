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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

/**
 * The hash mode a repository was created with, and what happens when a later command asks for another one.
 *
 * The mode given to init is remembered as the mode of the repository, and it decides what the States contain.
 * Comparing a State that holds three small blocks per file against a scan that hashed everything would report
 * differences that are not there, so a command that asks for a mode the repository cannot support has its mode
 * changed under it, with a warning. A weaker mode than the repository's is allowed as it is, since a State can
 * always answer a question narrower than the one it was built for.
 *
 * This suite is about those rules rather than about the hashing. It uses small files and stays in the default
 * tier; which bytes each mode actually reads is covered with files large enough to have separate blocks in
 * HashModeSemanticsSlowIT.
 */
@Tag(TestTiers.INTEGRATION)
public class HashModeCompatibilityIT {
    private IntegrationRepository repository;
    private FimCli fim;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        repository = new IntegrationRepository(testInfo);
        fim = repository.fim();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // The mode of the repository
    // -----------------------------------------------------------------------------------------------------------------

    /** The mode given to init becomes the mode later commands run in, without having to be repeated. */
    @ParameterizedTest
    @CsvSource({
            "'',   full",
            "-s,   super-fast",
            "-f,   fast",
            "-n,   do not hash"
    })
    public void theModeGivenToInitIsTheModeLaterCommandsUse(String initFlag, String expectedMode) throws Exception {
        repository.createFiles("file-1", "file-2");
        initWith(initFlag);

        fim.succeeds("st");
        fim.outputContains(String.format("using '%s' mode", expectedMode));
        fim.outputContains("Nothing modified");
    }

    /** Whatever the mode, a change of content is still noticed. Only how much is read differs. */
    @ParameterizedTest
    @ValueSource(strings = { "-s", "-f", "-n" })
    public void everyModeStillNoticesAFileBeingReplaced(String initFlag) throws Exception {
        repository.createFiles("file-1", "file-2");
        initWith(initFlag);

        // The length changes too, which is what the mode that hashes nothing has to go on.
        repository.appendTo("file-1", "clearly different now");

        fim.succeeds("st");
        fim.outputContains("1 content modified", "file-1");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Asking for a mode the repository cannot answer
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Asking for more hashing than the repository holds is refused and the repository's mode used instead. The
     * States simply do not contain what the stronger mode would compare against.
     */
    @ParameterizedTest
    @CsvSource({
            "-s, '',   super-fast",
            "-s, -f,   super-fast",
            "-f, '',   fast",
            "-n, '',   do not hash",
            "-n, -s,   do not hash",
            "-n, -f,   do not hash"
    })
    public void askingForMoreHashingThanTheRepositoryHoldsIsOverruled(String initFlag, String statusFlag, String expectedMode) throws Exception {
        repository.createFiles("file-1", "file-2");
        initWith(initFlag);

        succeedWith("st", statusFlag);
        fim.outputContains("is not compatible with the current one. Hash mode forced");
        fim.outputContains(String.format("using '%s' mode", expectedMode));
    }

    /**
     * Asking for less is allowed, and left alone. A repository that hashes medium blocks can answer a question
     * about small blocks, so the smaller mode is used as asked rather than raised to the repository's.
     */
    @ParameterizedTest
    @CsvSource({
            "-s, -s,   super-fast",
            "-f, -f,   fast",
            "-f, -s,   super-fast",
            "-s, -n,   do not hash",
            "-f, -n,   do not hash",
            "-n, -n,   do not hash"
    })
    public void askingForLessHashingThanTheRepositoryHoldsIsAllowed(String initFlag, String statusFlag, String expectedMode) throws Exception {
        repository.createFiles("file-1", "file-2");
        initWith(initFlag);

        succeedWith("st", statusFlag);
        fim.outputContains("that is compatible with the current one");
        fim.outputDoesNotContain("Hash mode forced");
        fim.outputContains(String.format("using '%s' mode", expectedMode));
    }

    /**
     * A repository created with full hashing puts no limit on anything, so a weaker mode is used exactly as
     * asked and there is nothing to say about it.
     */
    @ParameterizedTest
    @CsvSource({
            "-s,   super-fast",
            "-f,   fast",
            "-n,   do not hash"
    })
    public void aFullRepositoryAcceptsAnyModeWithoutComment(String statusFlag, String expectedMode) throws Exception {
        repository.createFiles("file-1", "file-2");
        fim.succeeds("init", "-y", "-m", "first");

        fim.succeeds("st", statusFlag);
        fim.outputContains(String.format("using '%s' mode", expectedMode));
        fim.outputDoesNotContain("Hash mode forced", "compatible with the current one");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Commands that need the content
    // -----------------------------------------------------------------------------------------------------------------

    /** Duplicates are found by comparing content, so a mode that reads none of it cannot be used. */
    @ParameterizedTest
    @ValueSource(strings = { "fdup", "rdup" })
    public void findingDuplicatesWithoutHashingIsRefused(String command) throws Exception {
        repository.createIdenticalFiles("the same bytes", "copy-a.txt", "copy-b.txt");
        fim.succeeds("init", "-y", "-m", "first");

        fim.failsAsBadUsage(command, "-n");
        fim.outputContains("File content hashing mandatory for this command.");
    }

    /**
     * A repository that hashes nothing can never search for duplicates, whatever is asked for, because the
     * repository's mode is applied first and then found to be unusable.
     */
    @Test
    public void aRepositoryThatHashesNothingCannotSearchForDuplicates() throws Exception {
        repository.createIdenticalFiles("the same bytes", "copy-a.txt", "copy-b.txt");
        fim.succeeds("init", "-y", "-n", "-m", "first");

        fim.failsAsBadUsage("fdup");
        fim.outputContains("File content hashing mandatory for this command.");
    }

    /** Telling one copy of a file from a damaged one needs every byte, so no lesser mode is accepted. */
    @ParameterizedTest
    @ValueSource(strings = { "-s", "-f", "-n" })
    public void detectingCorruptionWithAnythingLessThanFullHashingIsRefused(String flag) throws Exception {
        repository.createFiles("file-1", "file-2");
        fim.succeeds("init", "-y", "-m", "first");

        fim.failsAsBadUsage("dcor", flag);
        fim.outputContains("Hardware corruption detection require to hash all the file content.");
    }

    /**
     * Corruption detection asks for full hashing, but a repository that does not hold it overrules that after
     * the check has already passed, so the scan ends up running in the repository's weaker mode.
     *
     * That is worth knowing rather than assuming: on such a repository the command reports on three blocks per
     * file, not on the whole content, even though it refuses to be asked for less outright. The test records
     * what happens today, and the warning is the only sign the user gets.
     */
    @Test
    public void detectingCorruptionOnALesserRepositoryFallsBackToItsMode() throws Exception {
        repository.createFiles("file-1", "file-2");
        fim.succeeds("init", "-y", "-f", "-m", "first");

        fim.succeeds("dcor");
        fim.outputContains("is not compatible with the current one. Hash mode forced", "using 'fast' mode");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Committing
    // -----------------------------------------------------------------------------------------------------------------

    /** A State is what everything later is compared against, so it cannot be recorded without hashing. */
    @Test
    public void committingWithoutHashingIsRefused() throws Exception {
        repository.createFiles("file-1", "file-2");
        fim.succeeds("init", "-y", "-m", "first");
        repository.createFile("file-3");

        fim.failsAsBadUsage("ci", "-y", "-m", "second", "-n");
        fim.outputContains("Computing hash is mandatory");
    }

    /** Unless the repository hashes nothing either, in which case there is nothing to insist on. */
    @Test
    public void committingWithoutHashingIsAllowedWhenTheRepositoryHashesNothing() throws Exception {
        repository.createFiles("file-1", "file-2");
        fim.succeeds("init", "-y", "-n", "-m", "first");
        repository.createFile("file-3");

        fim.succeeds("ci", "-y", "-m", "second", "-n");
        fim.succeeds("st", "-n");
        fim.outputContains("Nothing modified");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // How many threads
    // -----------------------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "1,   1 thread",
            "2,   2 threads",
            "4,   4 threads"
    })
    public void theRequestedNumberOfThreadsIsUsed(String threadCount, String expected) throws Exception {
        repository.createFiles("file-1", "file-2", "file-3", "file-4");
        fim.succeeds("init", "-y", "-m", "first");

        fim.succeeds("st", "-t", threadCount);
        fim.outputContains("using 'full' mode and " + expected);
    }

    /** Without a number, the count is worked out as it goes rather than fixed up front. */
    @Test
    public void withoutANumberTheCountIsWorkedOutWhileScanning() throws Exception {
        repository.createFiles("file-1", "file-2");
        fim.succeeds("init", "-y", "-m", "first");

        fim.succeeds("st");
        fim.outputContains("using 'full' mode and automatic scaling");
    }

    /** Zero or less is not a count, and means the same as not asking. */
    @ParameterizedTest
    @ValueSource(strings = { "0", "-1" })
    public void zeroOrLessMeansTheCountIsWorkedOutWhileScanning(String threadCount) throws Exception {
        repository.createFiles("file-1", "file-2");
        fim.succeeds("init", "-y", "-m", "first");

        fim.succeeds("st", "-t", threadCount);
        fim.outputContains("automatic scaling");
    }

    /** With no content to hash there is nothing to share out, so the count is brought back to one and said so. */
    @Test
    public void askingForThreadsWithoutHashingIsBroughtBackToOne() throws Exception {
        repository.createFiles("file-1", "file-2");
        fim.succeeds("init", "-y", "-m", "first");

        fim.succeeds("st", "-n", "-t", "4");
        fim.outputContains("Not hashing file content so thread count forced to 1",
                "using 'do not hash' mode and 1 thread");
    }

    /** Something that is not a number is refused rather than taken as a default. */
    @Test
    public void aThreadCountThatIsNotANumberIsRefused() throws Exception {
        repository.createFiles("file-1", "file-2");
        fim.succeeds("init", "-y", "-m", "first");

        fim.failsAsBadUsage("st", "-t", "plenty");
        fim.outputContains("Exception parsing command line");
    }

    /**
     * The number of threads changes how the work is divided and nothing else. A State written with one thread
     * has to match a scan done with four, or the count would be leaking into the result.
     */
    @Test
    public void theNumberOfThreadsDoesNotChangeWhatIsRecorded() throws Exception {
        repository.createFiles("file-1", "file-2", "file-3", "file-4", "file-5");
        fim.succeeds("init", "-y", "-t", "1", "-m", "first");

        fim.succeeds("st", "-t", "4");
        fim.outputContains("Nothing modified");

        fim.succeeds("ci", "-y", "-t", "4", "-m", "second");
        fim.succeeds("st", "-t", "1");
        fim.outputContains("Nothing modified");
    }

    // -----------------------------------------------------------------------------------------------------------------

    /** Run init, leaving the mode flag out entirely when the case does not use one. */
    private void initWith(String initFlag) throws Exception {
        if (initFlag.isEmpty()) {
            fim.succeeds("init", "-y", "-m", "first");
        } else {
            fim.succeeds("init", "-y", initFlag, "-m", "first");
        }
    }

    private void succeedWith(String command, String flag) throws Exception {
        if (flag.isEmpty()) {
            fim.succeeds(command);
        } else {
            fim.succeeds(command, flag);
        }
    }
}
