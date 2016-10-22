/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2016  Etienne Vrignaud
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
package org.fim.internal;

import org.fim.internal.hash.FileHasher;
import org.fim.model.Context;
import org.fim.util.Logger;

import static org.fim.util.FileUtil.byteCountToDisplaySize;

public class DynamicScaling implements Runnable {
    private final StateGenerator stateGenerator;
    private final Context context;

    private long lastThroughput;
    private long currentThroughput;
    private int resourceLimitReached;
    private int scaleLevel;
    private int maxScaleLevel;
    private boolean stopRequested;

    public DynamicScaling(StateGenerator stateGenerator) {
        this.stateGenerator = stateGenerator;
        this.context = stateGenerator.getContext();
        this.lastThroughput = 0;
        this.currentThroughput = 0;
        this.resourceLimitReached = 0;
        this.scaleLevel = 0;
        this.maxScaleLevel = Runtime.getRuntime().availableProcessors();
        this.stopRequested = false;
    }

    @Override
    public void run() {
        try {
            waitBeforeCheckingThroughput();

            while (true) {
                checkThroughput();

                if (stopRequested || scaleLevel >= maxScaleLevel || resourceLimitReached > 20) {
                    break;
                }

                Logger.rawDebug("\n - Current throughput = " + byteCountToDisplaySize(currentThroughput) + ", scaleLevel = " + scaleLevel);
            }
        } catch (Exception ex) {
            Logger.error("Got exception", ex, context.isDisplayStackTrace());
        } finally {
            Logger.rawDebug("\n - Dynamic scaling finished. scaleLevel = " + scaleLevel);
            context.setDynamicScaling(false);
        }
    }

    private void scaleUp() throws Exception {
        stateGenerator.startFileHasher();
        scaleLevel = context.getThreadCount();
        Logger.rawDebug("\n - Scaling Up. scaleLevel = " + scaleLevel);
    }

    private void checkThroughput() throws Exception {
        long difference = currentThroughput - lastThroughput;
        Logger.rawDebug("\n - Throughput difference = " + byteCountToDisplaySize(difference));
        difference = difference / 1_024 / 1_024;

        if (difference > 10) {
            if (scaleLevel < maxScaleLevel) {
                scaleUp();
                resourceLimitReached = 0;
            }
            lastThroughput = currentThroughput;
        } else {
            resourceLimitReached++;
        }

        waitBeforeCheckingThroughput();

        currentThroughput = getTotalInstantThroughput();
        if (lastThroughput == 0) {
            lastThroughput = currentThroughput;
        }
    }

    private void waitBeforeCheckingThroughput() throws InterruptedException {
        for (int i = 0; i < scaleLevel * 2; i++) {
            if (stopRequested) {
                return;
            }
            Thread.sleep(500L);
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
        stopRequested = true;
    }
}
