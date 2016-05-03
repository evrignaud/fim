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
import org.fim.model.Attribute;
import org.fim.model.Context;
import org.fim.model.FileAttribute;
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.HashMode;
import org.fim.model.Range;
import org.fim.util.Console;
import org.fim.util.DosFilePermissions;
import org.fim.util.FileUtil;
import org.fim.util.Logger;
import org.fim.util.SELinux;
import sun.misc.Cleaner;
import sun.nio.ch.DirectBuffer;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.fim.model.Constants.NO_HASH;
import static org.fim.model.HashMode.dontHash;

public class FileHasher implements Runnable {
    protected final Context context;
    private final AtomicBoolean scanInProgress;
    final HashProgress hashProgress;

    private final BlockingDeque<Path> filesToHashQueue;
    private final String rootDir;
    private final List<FileState> fileStates;
    private final FrontHasher frontHasher;

    public FileHasher(Context context, AtomicBoolean scanInProgress, HashProgress hashProgress, BlockingDeque<Path> filesToHashQueue, String rootDir) throws NoSuchAlgorithmException {
        this.context = context;
        this.scanInProgress = scanInProgress;
        this.hashProgress = hashProgress;
        this.filesToHashQueue = filesToHashQueue;
        this.rootDir = rootDir;

        this.fileStates = new ArrayList<>();

        HashMode hashMode = hashProgress.getContext().getHashMode();
        frontHasher = new FrontHasher(hashMode);
    }

    public List<FileState> getFileStates() {
        return fileStates;
    }

    public long getTotalBytesHashed() {
        return frontHasher.getTotalBytesHashed();
    }

    protected FrontHasher getFrontHasher() {
        return frontHasher;
    }

    @Override
    public void run() {
        try {
            while (isQueueStillFilled()) {
                hashFilesInQueue();
                if (isQueueStillFilled()) {
                    Thread.sleep(500);
                }
            }
        } catch (InterruptedException ex) {
            Logger.error("Exception while hashing", ex, context.isDisplayStackTrace());
        }
    }

    private void hashFilesInQueue() throws InterruptedException {
        Path file;
        while ((file = filesToHashQueue.poll(100, TimeUnit.MILLISECONDS)) != null) {
            try {
                BasicFileAttributes attributes;
                List<Attribute> fileAttributes = null;

                if (SystemUtils.IS_OS_WINDOWS) {
                    DosFileAttributes dosFileAttributes = Files.readAttributes(file, DosFileAttributes.class);
                    fileAttributes = addAttribute(fileAttributes, FileAttribute.DosFilePermissions, DosFilePermissions.toString(dosFileAttributes));
                    attributes = dosFileAttributes;
                } else {
                    PosixFileAttributes posixFileAttributes = Files.readAttributes(file, PosixFileAttributes.class);
                    fileAttributes = addAttribute(fileAttributes, FileAttribute.PosixFilePermissions, PosixFilePermissions.toString(posixFileAttributes.permissions()));
                    if (SELinux.ENABLED) {
                        fileAttributes = addAttribute(fileAttributes, FileAttribute.SELinuxLabel, SELinux.getLabel(context, file));
                    }
                    attributes = posixFileAttributes;
                }

                hashProgress.updateOutput(attributes.size());

                FileHash fileHash = hashFile(file, attributes.size());
                String normalizedFileName = FileUtil.getNormalizedFileName(file);
                String relativeFileName = FileUtil.getRelativeFileName(rootDir, normalizedFileName);

                fileStates.add(new FileState(relativeFileName, attributes, fileHash, fileAttributes));
            } catch (Exception ex) {
                Console.newLine();
                Logger.error("Skipping - Error hashing file '" + file + "'", ex, context.isDisplayStackTrace());
            }
        }
    }

    private boolean isQueueStillFilled() {
        return scanInProgress.get() || filesToHashQueue.size() > 0;
    }

    private List<Attribute> addAttribute(List<Attribute> attributes, FileAttribute attribute, String value) {
        if (value == null) {
            return attributes;
        }

        List<Attribute> newAttributes = attributes;
        if (newAttributes == null) {
            newAttributes = new ArrayList<>();
        }

        newAttributes.add(new Attribute(attribute.name(), value));

        return newAttributes;
    }

    protected FileHash hashFile(Path file, long fileSize) throws IOException {
        HashMode hashMode = hashProgress.getContext().getHashMode();

        if (hashMode == dontHash) {
            return new FileHash(NO_HASH, NO_HASH, NO_HASH);
        }

        frontHasher.reset(fileSize);

        long filePosition = 0;
        long blockSize;
        int bufferSize;

        try (final FileChannel channel = FileChannel.open(file)) {
            while (filePosition < fileSize) {
                Range nextRange = frontHasher.getNextRange(filePosition);
                if (nextRange == null) {
                    break;
                }

                filePosition = nextRange.getFrom();
                blockSize = nextRange.getTo() - nextRange.getFrom();
                bufferSize = hashBuffer(channel, filePosition, blockSize);
                filePosition += bufferSize;
            }
        }

        if (false == frontHasher.hashComplete()) {
            throw new RuntimeException(String.format("Fim is not working correctly for file '%s' (size=%d). Some Hasher have not completed: small=%s, medium=%s, full=%s",
                file, fileSize, frontHasher.getSmallBlockHasher().hashComplete(), frontHasher.getMediumBlockHasher().hashComplete(), frontHasher.getFullHasher().hashComplete()));
        }

        return frontHasher.getFileHash();
    }

    private int hashBuffer(FileChannel channel, long filePosition, long size) throws IOException {
        MappedByteBuffer buffer = null;
        try {
            buffer = channel.map(FileChannel.MapMode.READ_ONLY, filePosition, size);
            int bufferSize = buffer.remaining();

            frontHasher.update(filePosition, buffer);

            return bufferSize;
        } finally {
            unmap(buffer);
        }
    }

    /**
     * Comes from here: http://stackoverflow.com/questions/8553158/prevent-outofmemory-when-using-java-nio-mappedbytebuffer
     */
    private void unmap(MappedByteBuffer bb) {
        if (bb == null) {
            return;
        }
        Cleaner cleaner = ((DirectBuffer) bb).cleaner();
        if (cleaner != null) {
            cleaner.clean();
        }
    }
}
