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

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.fim.model.Constants;
import org.fim.model.Context;
import org.fim.util.Console;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static org.fim.model.HashMode.dontHash;

public class HashProgress {
    public static final int PROGRESS_DISPLAY_FILE_COUNT = 10;

    private static final List<Pair<Character, Integer>> hashProgress = Arrays.asList(
        Pair.of('.', 0),
        Pair.of('o', Constants._20_MB),
        Pair.of('8', Constants._50_MB),
        Pair.of('O', Constants._100_MB),
        Pair.of('@', Constants._200_MB),
        Pair.of('#', Constants._1_GB)
    );

    private final ReentrantLock progressLock;
    private final Context context;
    private long summedFileLength;
    private int fileCount;

    public HashProgress(Context context) {
        this.context = context;
        this.progressLock = new ReentrantLock();
    }

    public void outputInit() {
        summedFileLength = 0;
        fileCount = 0;
    }

    public void updateOutput(long fileSize) throws IOException {
        progressLock.lock();
        try {
            fileCount++;

            if (isProgressDisplayed()) {
                summedFileLength += fileSize;

                if (fileCount % PROGRESS_DISPLAY_FILE_COUNT == 0) {
                    System.out.print(getProgressChar(summedFileLength));
                    summedFileLength = 0;
                }
            }

            if (fileCount % (100 * PROGRESS_DISPLAY_FILE_COUNT) == 0) {
                if (isProgressDisplayed()) {
                    Console.newLine();
                }
            }
        } finally {
            progressLock.unlock();
        }
    }

    public String hashLegend() {
        StringBuilder sb = new StringBuilder();
        for (int progressIndex = hashProgress.size() - 1; progressIndex >= 0; progressIndex--) {
            Pair<Character, Integer> progressPair = hashProgress.get(progressIndex);
            char marker = progressPair.getLeft();
            sb.append(marker);

            int fileLength = progressPair.getRight();
            if (fileLength == 0) {
                sb.append(" otherwise");
            } else {
                sb.append(" > ").append(FileUtils.byteCountToDisplaySize(fileLength));
            }
            sb.append(", ");
        }
        String legend = sb.toString();
        legend = legend.substring(0, legend.length() - 2);
        return legend;
    }

    protected char getProgressChar(long fileLength) {
        int progressIndex;
        for (progressIndex = hashProgress.size() - 1; progressIndex >= 0; progressIndex--) {
            Pair<Character, Integer> progressPair = hashProgress.get(progressIndex);
            if (fileLength >= progressPair.getRight()) {
                return progressPair.getLeft();
            }
        }

        return ' ';
    }

    public void outputStop() {
        if (isProgressDisplayed()) {
            if (fileCount >= PROGRESS_DISPLAY_FILE_COUNT) {
                Console.newLine();
            }
        }
    }

    public boolean isProgressDisplayed() {
        return context.isVerbose() && context.getHashMode() != dontHash;
    }

    public Context getContext() {
        return context;
    }
}
