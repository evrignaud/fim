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

package org.fim.internal;

import org.fim.internal.hash.FileHasher;
import org.fim.model.Context;
import org.fim.util.Logger;

import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.fim.util.FileUtil.byteCountToDisplaySize;

public class DynamicScaling implements Runnable {
    private final StateGenerator stateGenerator;
    private final Context context;

    private final AtomicBoolean stopRequested;
    private long lastGoodThroughput;
    private long currentThroughput;
    private int resourceLimitReached;
    private int scaleLevel;
    private final int maxScaleLevel;

    public DynamicScaling(StateGenerator stateGenerator) {
        this.stateGenerator = stateGenerator;
        this.context = stateGenerator.getContext();
        this.stopRequested = new AtomicBoolean(false);
        this.lastGoodThroughput = 0;
        this.currentThroughput = 0;
        this.resourceLimitReached = 0;
        this.scaleLevel = 1;
        this.maxScaleLevel = Runtime.getRuntime().availableProcessors();
    }

    @Override
    public void run() {
        try {
            Thread.sleep(200L);

            while (!stopRequested.get() && scaleLevel < maxScaleLevel && resourceLimitReached <= 20) {

                checkThroughput();

                waitBeforeCheckingThroughput();
            }
        } catch (Exception ex) {
            Logger.error("Got exception", ex, context.isDisplayStackTrace());
        } finally {
            Logger.rawDebug("\n - Dynamic scaling finished. scaleLevel = " + scaleLevel);
        }
    }

    private void scaleUp() throws NoSuchAlgorithmException {
        stateGenerator.startFileHasher();
        scaleLevel = context.getThreadCount();
        Logger.rawDebug("\n - Scaling Up. scaleLevel = " + scaleLevel);
    }

    private void checkThroughput() throws NoSuchAlgorithmException {
        currentThroughput = getTotalInstantThroughput();
        if (lastGoodThroughput == 0) {
            lastGoodThroughput = currentThroughput;
        }
        Logger.rawDebug("\n - Current throughput = " + byteCountToDisplaySize(currentThroughput) + ", scaleLevel = " + scaleLevel);

        long difference = currentThroughput - lastGoodThroughput;
        Logger.rawDebug("\n - Throughput difference = " + byteCountToDisplaySize(difference));
        difference = difference / 1_024 / 1_024;

        if (difference > 10) {
            if (scaleLevel < maxScaleLevel) {
                scaleUp();
                resourceLimitReached = 0;
            }
            lastGoodThroughput = currentThroughput;
        } else {
            resourceLimitReached++;
        }
    }

    private void waitBeforeCheckingThroughput() throws InterruptedException {
        for (int i = 0; i < scaleLevel * 5; i++) {
            if (stopRequested.get()) {
                return;
            }
            Thread.sleep(200L);
        }
    }

    private long getTotalInstantThroughput() {
        long totalInstantThroughput = 0;
        for (FileHasher fileHasher : stateGenerator.getFileHashers()) {
            totalInstantThroughput += fileHasher.getInstantThroughput();
        }
        return totalInstantThroughput;
    }

    public void requestStop() {
        stopRequested.set(true);
    }
}
