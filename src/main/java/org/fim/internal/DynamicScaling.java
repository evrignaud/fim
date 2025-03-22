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

import org.fim.model.Context;
import org.fim.util.Logger;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DynamicScaling adjusts the number of threads used for file processing based on observed throughput.
 * It increases the thread count as long as throughput continues to improve and stabilizes.
 */
public class DynamicScaling {
    private final Context context;
    private final int minThreads;
    private final int maxThreads;
    private final AtomicInteger currentThreads;
    private final AtomicLong volumeProcessed;
    private final AtomicLong lastMeasurementTime;
    private volatile double lastThroughput; // files per second
    private static final int MEASUREMENT_INTERVAL_MS = 100; // 100 milliseconds
    private static final double THROUGHPUT_THRESHOLD_UP = 0.02; // 2% improvement required to scale up

    public DynamicScaling(Context context) {
        this.context = context;
        this.minThreads = 1;
        this.maxThreads = Math.max(Runtime.getRuntime().availableProcessors() * 2, 4); // At least 4, up to CPU cores x 2
        this.currentThreads = new AtomicInteger(context.getThreadCount() > 0 ? context.getThreadCount() : minThreads);
        this.volumeProcessed = new AtomicLong(0);
        this.lastMeasurementTime = new AtomicLong(System.currentTimeMillis());
        this.lastThroughput = 0.0;

        if (!context.isUseDynamicScaling()) {
            Logger.rawDebug("Dynamic scaling is disabled. Using fixed thread count: " + currentThreads.get());
        }
    }

    /**
     * Record that a file has been processed and adjust thread count if necessary.
     */
    public void fileProcessed(long fileSize) {
        if (!context.isUseDynamicScaling()) {
            return; // No scaling if disabled
        }

        if (context.getThreadCount() == maxThreads) {
            return; // No scaling if thread count is already at max
        }

        long currentVolume = volumeProcessed.addAndGet(fileSize);
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - lastMeasurementTime.get();

        if (elapsedTime >= MEASUREMENT_INTERVAL_MS) {
            synchronized (this) {
                // Recheck under lock to avoid race conditions
                elapsedTime = currentTime - lastMeasurementTime.get();
                if (elapsedTime >= MEASUREMENT_INTERVAL_MS) {
                    double currentThroughput = (double) currentVolume / (elapsedTime / 1000.0); // bytes per second
                    adjustThreadCount(currentThroughput);
                    lastThroughput = currentThroughput;
                    lastMeasurementTime.set(currentTime);
                    volumeProcessed.set(0); // Reset counter
                }
            }
        }
    }

    /**
     * Adjust the thread count based on throughput changes.
     */
    private void adjustThreadCount(double currentThroughput) {
        int currentThreadCount = currentThreads.get();

        if (lastThroughput == 0.0) {
            // Initial measurement, no adjustment yet
            Logger.rawDebug(String.format("Initial throughput: %.2f bytes/sec with %d threads", currentThroughput, currentThreadCount));
            return;
        }

        double throughputChange = (currentThroughput - lastThroughput) / lastThroughput;

        if (throughputChange > THROUGHPUT_THRESHOLD_UP && currentThreadCount < maxThreads) {
            // Throughput increased significantly, scale up
            int newThreadCount = Math.min(currentThreadCount + 1, maxThreads);
            currentThreads.set(newThreadCount);
            context.setThreadCount(newThreadCount);
            Logger.rawDebug(String.format("Scaling up to %d threads. Throughput change %.2f", newThreadCount, throughputChange));
        }
    }

    /**
     * Get the current number of threads.
     */
    public int getCurrentThreadCount() {
        return currentThreads.get();
    }

    /**
     * Get the maximum allowed threads.
     */
    public int getMaxThreads() {
        return maxThreads;
    }

    /**
     * Get the minimum allowed threads.
     */
    public int getMinThreads() {
        return minThreads;
    }

    /**
     * Get the last measured throughput (files per second).
     */
    public double getLastThroughput() {
        return lastThroughput;
    }
}
