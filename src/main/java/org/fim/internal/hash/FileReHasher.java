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

package org.fim.internal.hash;

import org.fim.model.Context;
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.util.FileUtil;
import org.fim.util.Logger;

import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;

public class FileReHasher extends FileHasher {
    private final BlockingDeque<FileState> toRehashQueue;
    private final Path rootDir;

    public FileReHasher(Context context, HashProgress hashProgress, BlockingDeque<FileState> toRehashQueue, Path rootDir)
            throws NoSuchAlgorithmException {
        super(context, null, hashProgress, null, FileUtil.getNormalizedFileName(rootDir));
        this.toRehashQueue = toRehashQueue;
        this.rootDir = rootDir;
    }

    @Override
    public void run() {
        try {
            FileState fileState;
            while ((fileState = toRehashQueue.poll(100, TimeUnit.MILLISECONDS)) != null) {
                try {
                    long fileLength = fileState.getFileLength();
                    hashProgress.updateOutput(fileLength);

                    FileHash fileHash = hashFile(rootDir.resolve(fileState.getFileName()), fileLength);
                    fileState.setFileHash(fileHash);
                } catch (Exception ex) {
                    Logger.newLine();
                    Logger.error("Skipping - Error hashing file '" + fileState.getFileName() + "'", ex, context.isDisplayStackTrace());
                }
            }
        } catch (InterruptedException ex) {
            Logger.error("Exception while hashing", ex, context.isDisplayStackTrace());
        } finally {
            hashProgress.noMoreFileToHash();
        }
    }
}
