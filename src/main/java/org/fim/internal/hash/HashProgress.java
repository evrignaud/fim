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
package org.fim.internal.hash;

import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.fim.model.Constants;
import org.fim.model.Context;
import org.fim.util.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.fim.model.HashMode.dontHash;
import static org.fim.util.FileUtil.byteCountToDisplaySize;

public class HashProgress {
    public static final int PROGRESS_DISPLAY_FILE_COUNT = 10;

    private static final List<Pair<Character, Integer>> progressChars = Arrays.asList(
        Pair.of('.', 0),
        Pair.of('o', Constants._20_MB),
        Pair.of('8', Constants._50_MB),
        Pair.of('O', Constants._100_MB),
        Pair.of('@', Constants._200_MB),
        Pair.of('#', Constants._1_GB)
    );

    private final Context context;
    private long summedFileLength;
    private int fileCount;
    private int hashProgressWidth;
    private CountDownLatch hashIndicator;

    public HashProgress(Context context) {
        this.context = context;
        this.hashProgressWidth = getHashProgressWidth();
        this.hashIndicator = new CountDownLatch(2);
    }

    public void hashStarted() {
        hashIndicator.countDown();
    }

    public boolean isHashStarted() {
        return hashIndicator.getCount() == 1;
    }

    public void noMoreFileToHash() {
        hashIndicator.countDown();
    }

    public void waitAllFilesToBeHashed() {
        try {
            hashIndicator.await();
        } catch (InterruptedException e) {
            // Ok. Just get out
        }
    }

    public synchronized void outputInit() {
        summedFileLength = 0;
        fileCount = 0;
    }

    synchronized void updateOutput(long fileSize) {
        fileCount++;

        if (isProgressDisplayed()) {
            summedFileLength += fileSize;

            if (fileCount % PROGRESS_DISPLAY_FILE_COUNT == 0) {
                Logger.out.print(getProgressChar(summedFileLength));
                summedFileLength = 0;
            }
        }

        if (fileCount % (hashProgressWidth * PROGRESS_DISPLAY_FILE_COUNT) == 0) {
            if (isProgressDisplayed()) {
                Logger.newLine();
            }
        }
    }

    public String hashLegend() {
        StringBuilder sb = new StringBuilder();
        for (int progressIndex = progressChars.size() - 1; progressIndex >= 0; progressIndex--) {
            Pair<Character, Integer> progressPair = progressChars.get(progressIndex);
            char marker = progressPair.getLeft();
            sb.append(marker);

            int fileLength = progressPair.getRight();
            if (fileLength == 0) {
                sb.append(" otherwise");
            } else {
                sb.append(" > ").append(byteCountToDisplaySize(fileLength));
            }
            sb.append(", ");
        }
        String legend = sb.toString();
        legend = legend.substring(0, legend.length() - 2);
        return legend;
    }

    char getProgressChar(long fileLength) {
        int progressIndex;
        for (progressIndex = progressChars.size() - 1; progressIndex >= 0; progressIndex--) {
            Pair<Character, Integer> progressPair = progressChars.get(progressIndex);
            if (fileLength >= progressPair.getRight()) {
                return progressPair.getLeft();
            }
        }

        return ' ';
    }

    public synchronized void outputStop() {
        if (isProgressDisplayed()) {
            if (fileCount >= PROGRESS_DISPLAY_FILE_COUNT) {
                Logger.newLine();
            }
        }
    }

    public boolean isProgressDisplayed() {
        return context.isVerbose() && context.getHashMode() != dontHash;
    }

    private int getHashProgressWidth() {
        int width = 100;

        if (!SystemUtils.IS_OS_WINDOWS) {
            try {
                String result = System.getenv("TERMINAL_COLUMNS");
                int terminalColumns = Integer.parseInt(result);
                if (terminalColumns > 0) {
                    width = (int) (terminalColumns * 0.9);
                }
            } catch (Exception e) {
                // Never mind use the default value
            }
        }
        return width;
    }
}
