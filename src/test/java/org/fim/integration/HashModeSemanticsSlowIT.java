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

import org.fim.tooling.FimCli;
import org.fim.tooling.IntegrationRepository;
import org.fim.tooling.TestTiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * What each hash mode can and cannot notice, shown on a file large enough for the difference to exist.
 *
 * This is the tier that needs real file sizes. The quick modes read three blocks rather than the whole file,
 * and on a small file those three blocks are the whole file, so every mode behaves identically and the
 * distinction being relied on cannot be observed. The promise the documentation makes, that a quick mode is
 * fast because it may miss a change, is only testable on a file where the blocks leave gaps.
 *
 * For the 3 MB file used here, with a small block of 4 KB and a medium block of 1 MB:
 *
 * <pre>
 *   super-fast reads  [4 KB, 8 KB)   [1536 KB, 1540 KB)   and the last 4 KB
 *   fast reads        [1 MB, 2 MB)
 *   full reads        everything
 * </pre>
 *
 * Those regions barely overlap, which is why a change can be visible to one quick mode and invisible to the
 * other. Every change below keeps the length and both timestamps as they were, so the mode is the only reason
 * one run notices it and another does not.
 */
@Tag(TestTiers.SLOW)
public class HashModeSemanticsSlowIT {
    private static final int THREE_MEGABYTES = 3 * 1_024 * 1_024;

    /** Inside the first block super-fast reads, and before the single block fast reads. */
    private static final long INSIDE_THE_SMALL_BLOCK = 5_000;

    /** Inside the block fast reads, and between two of the blocks super-fast reads. */
    private static final long INSIDE_THE_MEDIUM_BLOCK = 1_500_000;

    /** Before every block either quick mode reads. */
    private static final long OUTSIDE_EVERY_BLOCK = 512 * 1_024;

    private IntegrationRepository repository;
    private FimCli fim;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        repository = new IntegrationRepository(testInfo);
        fim = repository.fim();
    }

    /**
     * The case the quick modes exist for, and the price of using them. Nothing in the first megabyte other
     * than the first few kilobytes is read by either quick mode, so a change there is invisible to both.
     */
    @Test
    public void aChangeOutsideEveryBlockIsMissedByBothQuickModesAndCaughtByFullHashing() throws Exception {
        givenALargeFileCommittedWithFullHashing();

        repository.overwriteBytesKeepingSizeAndTimestamps("large.bin", OUTSIDE_EVERY_BLOCK, "TAMPERED");

        fim.succeeds("st", "-s");
        fim.outputContains("Nothing modified");

        fim.succeeds("st", "-f");
        fim.outputContains("Nothing modified");

        // status reports the change for what it is; calling it corruption is what dcor is for.
        fim.succeeds("st");
        fim.outputContains("large.bin", "1 content modified");
    }

    /**
     * The modes are nested rather than separate: fast reads everything super-fast reads and the medium block
     * as well, and full reads everything. So a change the quickest mode notices is noticed by all of them,
     * and there is never a reason to prefer a slower mode for a change already visible to a faster one.
     */
    @Test
    public void aChangeInTheSmallBlockIsCaughtByEveryHashingMode() throws Exception {
        givenALargeFileCommittedWithFullHashing();

        repository.overwriteBytesKeepingSizeAndTimestamps("large.bin", INSIDE_THE_SMALL_BLOCK, "TAMPERED");

        fim.succeeds("st", "-s");
        fim.outputContains("large.bin", "1 content modified");

        fim.succeeds("st", "-f");
        fim.outputContains("large.bin", "1 content modified");

        fim.succeeds("st");
        fim.outputContains("large.bin", "1 content modified");
    }

    /** Fast reads the middle megabyte, which falls between two of the blocks super-fast reads. */
    @Test
    public void aChangeInTheMediumBlockIsCaughtByFastAndMissedBySuperFast() throws Exception {
        givenALargeFileCommittedWithFullHashing();

        repository.overwriteBytesKeepingSizeAndTimestamps("large.bin", INSIDE_THE_MEDIUM_BLOCK, "TAMPERED");

        fim.succeeds("st", "-f");
        fim.outputContains("large.bin", "1 content modified");

        fim.succeeds("st", "-s");
        fim.outputContains("Nothing modified");

        fim.succeeds("st");
        fim.outputContains("1 content modified");
    }

    /** Not hashing at all cannot notice a change of content, whatever its size or position. */
    @Test
    public void notHashingMissesEveryChangeOfContent() throws Exception {
        givenALargeFileCommittedWithFullHashing();

        repository.overwriteBytesKeepingSizeAndTimestamps("large.bin", INSIDE_THE_SMALL_BLOCK, "TAMPERED");

        fim.succeeds("st", "-n");
        fim.outputContains("Nothing modified");
    }

    /** Detecting corruption reads everything, so it finds a change wherever it was made. */
    @Test
    public void corruptionDetectionFindsAChangeAnywhereInTheFile() throws Exception {
        givenALargeFileCommittedWithFullHashing();

        repository.overwriteBytesKeepingSizeAndTimestamps("large.bin", OUTSIDE_EVERY_BLOCK, "TAMPERED");

        fim.succeeds("dcor");
        fim.outputContains("large.bin", "1 corrupted");
    }

    /**
     * The super-fast commit: the modes that read three blocks are used to find out what changed, and the
     * files that did change are then hashed in full. So a quick commit still leaves a State holding complete
     * hashes, which is why full hashing sees nothing to do afterwards.
     */
    @Test
    public void aQuickCommitStillRecordsCompleteHashes() throws Exception {
        givenALargeFileCommittedWithFullHashing();

        // A change super-fast is able to notice, with the modification time moved as an ordinary edit would.
        repository.overwriteBytesKeepingSizeAndTimestamps("large.bin", INSIDE_THE_SMALL_BLOCK, "EDITED!!");
        repository.touchLastModified("large.bin");

        fim.succeeds("ci", "-s", "-y", "-m", "committed the quick way");
        fim.outputContains("Retrieving the missing hash");

        // The State holds full hashes, so the thorough comparison has nothing left to report.
        fim.succeeds("st");
        fim.outputContains("Nothing modified");
    }

    /**
     * A repository initialised in a quick mode records that as its ceiling, and asking for more later is
     * refused rather than silently served: the earlier States never held the hashes a thorough comparison
     * would need.
     */
    @Test
    public void aRepositoryInitialisedQuicklyWillNotBeComparedThoroughly() throws Exception {
        repository.createFile("large.bin", "original content", THREE_MEGABYTES);
        fim.succeeds("init", "-y", "-s", "-m", "initialised the quick way");
        fim.outputContains("Global hash mode set to 'super-fast'");

        fim.succeeds("st");
        fim.outputContains("Using global hash mode 'super-fast'", "Hash mode forced");
    }

    // -----------------------------------------------------------------------------------------------------------------

    private void givenALargeFileCommittedWithFullHashing() throws Exception {
        repository.createFile("large.bin", "original content", THREE_MEGABYTES);
        repository.createFile("small.txt");
        fim.succeeds("init", "-y", "-m", "a large file hashed in full");
    }
}
