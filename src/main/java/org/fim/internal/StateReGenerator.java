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

import org.fim.command.exception.FimInternalError;
import org.fim.internal.hash.FileHasher;
import org.fim.internal.hash.FileReHasher;
import org.fim.model.Context;
import org.fim.model.FileState;
import org.fim.util.Logger;

import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import static org.atteo.evo.inflector.English.plural;
import static org.fim.util.HashModeUtil.hashModeToString;

public class StateReGenerator extends StateGenerator {
    private BlockingDeque<FileState> toRehashQueue;

    public StateReGenerator(Context context) {
        super(context);
        if (context.getThreadCount() <= 0) {
            throw new FimInternalError("ThreadCount must be set");
        }
    }

    public void reHashFiles(List<FileState> toReHash) throws NoSuchAlgorithmException {
        int threadCount = context.getThreadCount();
        Logger.info(String.format("Retrieving the missing hash for all the modified files, using '%s' mode and %d %s",
                hashModeToString(context.getHashMode()), threadCount, plural("thread", threadCount)));

        rootDir = context.getRepositoryRootDir();

        toRehashQueue = new LinkedBlockingDeque<>(toReHash);

        long start = System.currentTimeMillis();
        hashProgress.outputInit();
        long overallTotalBytesHashed = 0;
        try {
            startFileHashers();
            waitAllFilesToBeHashed();

            for (FileHasher fileHasher : fileHashers) {
                overallTotalBytesHashed += fileHasher.getTotalBytesHashed();
            }
        } finally {
            hashProgress.outputStop();
        }

        long duration = System.currentTimeMillis() - start;

        int fileCount = toReHash.size();
        long fileContentLength = 0;
        for (FileState fileState : toReHash) {
            long fileLength = fileState.getFileLength();
            fileContentLength += fileLength;
        }

        displayStatistics(duration, fileCount, fileContentLength, overallTotalBytesHashed);
    }

    @Override
    protected void startFileHashers() throws NoSuchAlgorithmException {
        initializeFileHashers();
        hashProgress.hashStarted();
        for (int index = 0; index < context.getThreadCount(); index++) {
            FileHasher hasher = new FileReHasher(context, hashProgress, toRehashQueue, rootDir);
            executorService.submit(hasher);
            fileHashers.add(hasher);
        }
    }
}
