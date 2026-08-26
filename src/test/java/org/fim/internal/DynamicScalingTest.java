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

package org.fim.internal;

import org.fim.tooling.BuildableContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DynamicScalingTest {

    private BuildableContext createContext(boolean dynamicScaling, int threadCount) {
        BuildableContext ctx = new BuildableContext();
        ctx.setUseDynamicScaling(dynamicScaling);
        ctx.setThreadCount(threadCount);
        return ctx;
    }

    // --- Req 13.5: Minimum thread count is 1 ---

    @Test
    public void minThreads_isOne() {
        BuildableContext ctx = createContext(true, 1);
        DynamicScaling ds = new DynamicScaling(ctx);
        assertThat(ds.getMinThreads()).isEqualTo(1);
    }

    // --- Req 13.4: Maximum thread count = max(4, 2 * availableProcessors) ---

    @Test
    public void maxThreads_isAtLeast4() {
        BuildableContext ctx = createContext(true, 1);
        DynamicScaling ds = new DynamicScaling(ctx);
        assertThat(ds.getMaxThreads()).isGreaterThanOrEqualTo(4);
    }

    @Test
    public void maxThreads_isTwiceCoresOrFour() {
        BuildableContext ctx = createContext(true, 1);
        DynamicScaling ds = new DynamicScaling(ctx);
        int expected = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
        assertThat(ds.getMaxThreads()).isEqualTo(expected);
    }

    // --- Req 13.1: Starts with configured thread count ---

    @Test
    public void initialThreadCount_usesConfiguredValue() {
        BuildableContext ctx = createContext(true, 3);
        DynamicScaling ds = new DynamicScaling(ctx);
        assertThat(ds.getCurrentThreadCount()).isEqualTo(3);
    }

    @Test
    public void initialThreadCount_defaultsToOneWhenNotSpecified() {
        BuildableContext ctx = createContext(true, 0);
        DynamicScaling ds = new DynamicScaling(ctx);
        assertThat(ds.getCurrentThreadCount()).isEqualTo(1);
    }

    @Test
    public void initialThreadCount_defaultsToOneWhenNegative() {
        BuildableContext ctx = createContext(true, -1);
        DynamicScaling ds = new DynamicScaling(ctx);
        assertThat(ds.getCurrentThreadCount()).isEqualTo(1);
    }

    // --- Req 13.6: Disabled mode uses fixed thread count ---

    @Test
    public void disabledMode_doesNotAdjustThreadCount() throws InterruptedException {
        BuildableContext ctx = createContext(false, 2);
        DynamicScaling ds = new DynamicScaling(ctx);

        int initialCount = ds.getCurrentThreadCount();

        // Process many files with increasing sizes to simulate throughput improvement
        for (int i = 0; i < 100; i++) {
            ds.fileProcessed(10_000_000L * (i + 1));
            if (i % 10 == 0) {
                Thread.sleep(15); // small delay to allow time to pass
            }
        }

        // Thread count should remain unchanged when disabled
        assertThat(ds.getCurrentThreadCount()).isEqualTo(initialCount);
    }

    // --- Req 13.7: Skip adjustment when at max threads ---

    @Test
    public void atMaxThreads_skipsAdjustment() throws InterruptedException {
        int maxThreads = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
        BuildableContext ctx = createContext(true, maxThreads);
        DynamicScaling ds = new DynamicScaling(ctx);

        assertThat(ds.getCurrentThreadCount()).isEqualTo(maxThreads);

        // Process files — should not change since already at max
        for (int i = 0; i < 50; i++) {
            ds.fileProcessed(50_000_000L);
            if (i % 10 == 0) {
                Thread.sleep(15);
            }
        }

        assertThat(ds.getCurrentThreadCount()).isEqualTo(maxThreads);
    }

    // --- Req 13.3, 13.5: Thread count stays within bounds ---

    @Test
    public void threadCount_neverBelowMin() throws InterruptedException {
        BuildableContext ctx = createContext(true, 1);
        DynamicScaling ds = new DynamicScaling(ctx);

        for (int i = 0; i < 50; i++) {
            ds.fileProcessed(1_000_000L);
            if (i % 10 == 0) {
                Thread.sleep(15);
            }
        }

        assertThat(ds.getCurrentThreadCount()).isGreaterThanOrEqualTo(ds.getMinThreads());
    }

    @Test
    public void threadCount_neverAboveMax() throws InterruptedException {
        BuildableContext ctx = createContext(true, 1);
        DynamicScaling ds = new DynamicScaling(ctx);

        // Process many files with large sizes to try to trigger scaling
        for (int i = 0; i < 200; i++) {
            ds.fileProcessed(100_000_000L);
            if (i % 5 == 0) {
                Thread.sleep(15);
            }
        }

        assertThat(ds.getCurrentThreadCount()).isLessThanOrEqualTo(ds.getMaxThreads());
    }

    // --- Req 13.2: 100ms measurement interval ---

    @Test
    public void fileProcessed_noAdjustmentBeforeMeasurementInterval() {
        BuildableContext ctx = createContext(true, 1);
        DynamicScaling ds = new DynamicScaling(ctx);

        // Process a single file immediately — not enough time has passed for measurement
        ds.fileProcessed(1_000_000L);

        // Initial throughput is 0.0, so no adjustment should happen
        assertThat(ds.getLastThroughput()).isEqualTo(0.0);
    }
}
