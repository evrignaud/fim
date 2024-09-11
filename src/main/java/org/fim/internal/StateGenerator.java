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

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.fim.internal.hash.FileHasher;
import org.fim.internal.hash.HashProgress;
import org.fim.model.Context;
import org.fim.model.FilePattern;
import org.fim.model.FileState;
import org.fim.model.FimIgnore;
import org.fim.model.State;
import org.fim.util.FileUtil;
import org.fim.util.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.atteo.evo.inflector.English.plural;
import static org.fim.internal.hash.HashProgress.PROGRESS_DISPLAY_FILE_COUNT;
import static org.fim.model.HashMode.dontHash;
import static org.fim.util.FileUtil.byteCountToDisplaySize;
import static org.fim.util.HashModeUtil.hashModeToString;

public class StateGenerator {
    private static final int FILES_QUEUE_CAPACITY = 500;

    private static final Comparator<FileState> FILE_NAME_COMPARATOR = new FileState.FileNameComparator();

    protected final Context context;
    final HashProgress hashProgress;
    private final FimIgnoreManager fimIgnoreManager;

    ExecutorService executorService;

    protected Path rootDir;
    private BlockingDeque<Path> filesToHashQueue;
    private AtomicBoolean scanInProgress;
    List<FileHasher> fileHashers;
    private DynamicScaling dynamicScaling;

    public StateGenerator(Context context) {
        this.context = context;
        this.hashProgress = new HashProgress(context);
        this.fimIgnoreManager = new FimIgnoreManager(context);
        this.dynamicScaling = null;
    }

    public State generateState(String comment, Path rootDir, Path dirToScan) throws NoSuchAlgorithmException {
        this.rootDir = rootDir;

        String usingThreads;
        if (context.isDynamicScaling()) {
            usingThreads = "automatic scaling";
        } else {
            usingThreads = String.format("%d %s", context.getThreadCount(), plural("thread", context.getThreadCount()));
        }
        Logger.info(String.format("Scanning recursively local files, using '%s' mode and %s",
                hashModeToString(context.getHashMode()), usingThreads));
        if (hashProgress.isProgressDisplayed()) {
            Logger.out.printf("(Hash progress legend for files grouped %d by %d: %s)%n", PROGRESS_DISPLAY_FILE_COUNT, PROGRESS_DISPLAY_FILE_COUNT,
                    hashProgress.hashLegend());
        }

        State state = new State();
        state.setComment(comment);
        state.setHashMode(context.getHashMode());
        state.getCommitDetails().setHashModeUsedToGetTheStatus(context.getHashMode());

        long start = System.currentTimeMillis();
        hashProgress.outputInit();

        filesToHashQueue = new LinkedBlockingDeque<>(FILES_QUEUE_CAPACITY);
        initializeFileHashers();

        FimIgnore initialFimIgnore = fimIgnoreManager.loadInitialFimIgnore();
        try {
            scanInProgress = new AtomicBoolean(true);
            scanFileTree(filesToHashQueue, dirToScan, initialFimIgnore);
        } finally {
            scanInProgress.set(false);
        }

        // In case the FileHashers have not already been started
        startFileHashers();

        waitAllFilesToBeHashed();

        System.gc(); // Force to cleanup unused memory

        long overallTotalBytesHashed = 0;
        for (FileHasher fileHasher : fileHashers) {
            state.getFileStates().addAll(fileHasher.getFileStates());
            overallTotalBytesHashed += fileHasher.getTotalBytesHashed();
        }

        state.getFileStates().sort(FILE_NAME_COMPARATOR);

        state.setIgnoredFiles(fimIgnoreManager.getIgnoredFiles());

        hashProgress.outputStop();
        long duration = System.currentTimeMillis() - start;
        displayStatistics(duration, state.getFileCount(), state.getFilesContentLength(), overallTotalBytesHashed);

        return state;
    }

    protected void initializeFileHashers() {
        fileHashers = new ArrayList<>();

        int maxThreads = context.getThreadCount();
        if (context.isDynamicScaling()) {
            maxThreads = Runtime.getRuntime().availableProcessors();
        }
        executorService = Executors.newFixedThreadPool(maxThreads);
    }

    protected void startFileHashers() throws NoSuchAlgorithmException {
        if (!hashProgress.isHashStarted()) {
            hashProgress.hashStarted();
            String normalizedRootDir = FileUtil.getNormalizedFileName(rootDir);
            if (context.isDynamicScaling()) {
                startFileHasher(normalizedRootDir);

                dynamicScaling = new DynamicScaling(this);
                Thread thread = new Thread(dynamicScaling, "dynamic-scaling");
                thread.start();
            } else {
                for (int index = 0; index < context.getThreadCount(); index++) {
                    startFileHasher(normalizedRootDir);
                }
            }
        }
    }

    public FileHasher startFileHasher() throws NoSuchAlgorithmException {
        String normalizedRootDir = FileUtil.getNormalizedFileName(rootDir);
        return startFileHasher(normalizedRootDir);
    }

    public FileHasher startFileHasher(String normalizedRootDir) throws NoSuchAlgorithmException {
        FileHasher hasher = new FileHasher(context, scanInProgress, hashProgress, filesToHashQueue, normalizedRootDir);
        executorService.submit(hasher);
        fileHashers.add(hasher);

        if (context.isDynamicScaling()) {
            context.setThreadCount(fileHashers.size());
        }
        return hasher;
    }

    public Context getContext() {
        return context;
    }

    public List<FileHasher> getFileHashers() {
        return fileHashers;
    }

    protected void waitAllFilesToBeHashed() {
        try {
            hashProgress.waitAllFilesToBeHashed();

            if (dynamicScaling != null) {
                dynamicScaling.requestStop();
            }

            executorService.shutdown();
            executorService.awaitTermination(3, TimeUnit.DAYS);
        } catch (InterruptedException ex) {
            Logger.error("Exception while waiting for files to be hashed", ex, context.isDisplayStackTrace());
        }
    }

    protected void displayStatistics(long duration, int fileCount, long filesContentLength, long totalBytesHashed) {
        String totalFileContentLengthStr = byteCountToDisplaySize(filesContentLength);
        String totalBytesHashedStr = byteCountToDisplaySize(totalBytesHashed);
        String durationStr = DurationFormatUtils.formatDuration(duration, "HH:mm:ss");

        long durationSeconds = duration / 1000;
        if (durationSeconds <= 0) {
            durationSeconds = 1;
        }

        long globalThroughput = totalBytesHashed / durationSeconds;
        String throughputStr = byteCountToDisplaySize(globalThroughput);

        String usingThreads = "";
        if (context.isDynamicScaling()) {
            usingThreads = String.format(", using %d %s", context.getThreadCount(), plural("thread", context.getThreadCount()));
        }

        if (context.getHashMode() == dontHash) {
            Logger.info(String.format("Scanned %d %s (%s)%s, during %s%n",
                    fileCount, plural("file", fileCount), totalFileContentLengthStr, usingThreads, durationStr));
        } else {
            Logger.info(String.format("Scanned %d %s (%s)%s, hashed %s (avg %s/s), during %s%n",
                    fileCount, plural("file", fileCount), totalFileContentLengthStr, usingThreads, totalBytesHashedStr, throughputStr, durationStr));
        }
    }

    private void scanFileTree(BlockingDeque<Path> filesToHashQueue, Path directory, FimIgnore parentFimIgnore) throws NoSuchAlgorithmException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            FimIgnore fimIgnore = fimIgnoreManager.loadLocalIgnore(directory, parentFimIgnore);

            for (Path file : stream) {
                if (!hashProgress.isHashStarted() && filesToHashQueue.size() > FILES_QUEUE_CAPACITY / 2) {
                    startFileHashers();
                }

                BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                String fileName = file.getFileName().toString();
                if (fimIgnoreManager.isIgnored(fileName, attributes, fimIgnore)) {
                    fimIgnoreManager.ignoreThisFiles(file, attributes);
                } else {
                    if (attributes.isRegularFile()) {
                        if (FilePattern.matchPatterns(fileName, context.getIncludePatterns(), true) &&
                            !FilePattern.matchPatterns(fileName, context.getExcludePatterns(), false)) {
                            enqueueFile(filesToHashQueue, file);
                        }
                    } else if (attributes.isDirectory()) {
                        scanFileTree(filesToHashQueue, file, fimIgnore);
                    }
                }
            }
        } catch (IOException ex) {
            Logger.newLine();
            Logger.error("Skipping - Error scanning directory '" + directory + "'", ex, context.isDisplayStackTrace());
        }
    }

    private void enqueueFile(BlockingDeque<Path> filesToHashQueue, Path file) {
        try {
            filesToHashQueue.offer(file, 120, TimeUnit.MINUTES);
        } catch (InterruptedException ex) {
            Logger.error("Exception while enqueuing file '" + file + "'", ex, context.isDisplayStackTrace());
        }
    }
}
