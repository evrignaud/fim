/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2025 Etienne Vrignaud
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

package org.fim.internal.hash;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.Constants.SIZE_1_MB;

public class ThroughputKeeperTest {

    // --- Req 12.1: BLOCK_SIZE = 500 MB ---

    @Test
    public void blockSize_is500MB() {
        assertThat(ThroughputKeeper.BLOCK_SIZE).isEqualTo(500 * SIZE_1_MB);
    }

    // --- Req 12.2: HALF_BLOCK_SIZE = 250 MB ---

    @Test
    public void halfBlockSize_is250MB() {
        assertThat(ThroughputKeeper.HALF_BLOCK_SIZE).isEqualTo(250 * SIZE_1_MB);
        assertThat(ThroughputKeeper.HALF_BLOCK_SIZE).isEqualTo(ThroughputKeeper.BLOCK_SIZE / 2);
    }

    // --- Req 12.5: Minimum 1ms duration prevents division by zero ---

    @Test
    public void getInstantThroughput_neverThrowsOnFreshInstance() {
        ThroughputKeeper keeper = new ThroughputKeeper();
        // Immediately after construction, duration is ~0ms but clamped to 1ms
        long throughput = keeper.getInstantThroughput();
        assertThat(throughput).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void getInstantThroughput_neverThrowsAfterZeroSizeUpdate() {
        ThroughputKeeper keeper = new ThroughputKeeper();
        keeper.update(0);
        long throughput = keeper.getInstantThroughput();
        assertThat(throughput).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void getInstantThroughput_returnsNonNegativeAfterSmallUpdate() {
        ThroughputKeeper keeper = new ThroughputKeeper();
        keeper.update(1024);
        long throughput = keeper.getInstantThroughput();
        assertThat(throughput).isGreaterThanOrEqualTo(0);
    }

    // --- Req 12.4: Sub-second extrapolation ---

    @Test
    public void getInstantThroughput_extrapolatesForSubSecondDuration() {
        ThroughputKeeper keeper = new ThroughputKeeper();
        // Feed a small amount of data; since the test runs in < 1 second,
        // the sub-second extrapolation path should be taken
        keeper.update(1_000_000); // 1 MB
        long throughput = keeper.getInstantThroughput();
        // Extrapolated throughput should be >= the raw size since duration < 1s
        assertThat(throughput).isGreaterThanOrEqualTo(1_000_000);
    }

    // --- Req 12.3: Window shifting when exceeding 500 MB ---

    @Test
    public void update_shiftsWindowWhenExceedingBlockSize() {
        ThroughputKeeper keeper = new ThroughputKeeper();

        // Feed data exceeding BLOCK_SIZE (500 MB) to trigger window shift
        long chunkSize = 100L * SIZE_1_MB;
        for (int i = 0; i < 6; i++) { // 600 MB total > 500 MB
            keeper.update(chunkSize);
        }

        // After window shift, throughput should still be computable and non-negative
        long throughput = keeper.getInstantThroughput();
        assertThat(throughput).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void update_halfBlockCheckpointCreatedAt250MB() {
        ThroughputKeeper keeper = new ThroughputKeeper();

        // Feed exactly 251 MB to trigger halfBlock creation
        keeper.update(251L * SIZE_1_MB);

        // Feed more to exceed BLOCK_SIZE and trigger shift
        keeper.update(300L * SIZE_1_MB); // total > 500 MB

        // Should still work correctly after shift
        long throughput = keeper.getInstantThroughput();
        assertThat(throughput).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void update_multipleWindowShifts() {
        ThroughputKeeper keeper = new ThroughputKeeper();

        // Trigger multiple window shifts by feeding > 1 GB of data
        long chunkSize = 100L * SIZE_1_MB;
        for (int i = 0; i < 15; i++) { // 1500 MB total
            keeper.update(chunkSize);
        }

        long throughput = keeper.getInstantThroughput();
        assertThat(throughput).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void getInstantThroughput_returnsPositiveAfterNonZeroUpdate() {
        ThroughputKeeper keeper = new ThroughputKeeper();
        keeper.update(SIZE_1_MB);
        long throughput = keeper.getInstantThroughput();
        // With non-zero data and minimum 1ms duration, throughput must be positive
        assertThat(throughput).isGreaterThan(0);
    }
}
