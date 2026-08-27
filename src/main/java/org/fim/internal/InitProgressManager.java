/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2026 Etienne Vrignaud
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
import org.fim.model.CorruptedStateException;
import org.fim.model.FileState;
import org.fim.model.FileTime;
import org.fim.model.HashMode;
import org.fim.model.State;
import org.fim.util.Logger;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Keeps the hashes computed by a running {@code fim init} on disk so that an interrupted init can carry on
 * from where it stopped.
 * <p>
 * Hashing a complete file tree can take hours, and everything an init computed used to live in memory until
 * the very last step, where the first State is written. Stopping the init, whichever way, threw all of it
 * away and the next attempt started again from the first file.
 * <p>
 * The progress is stored in {@code .fim/init-progress.json.gz}, which is an ordinary State file holding the
 * FileStates hashed so far. Reusing the State format means the hashes are read back through the same
 * integrity check as any other State, so a progress file that was damaged is detected and discarded rather
 * than trusted. The file is written to a temporary name and then moved into place, so a progress file is
 * either the previous one or the new one and never a truncated mix of both.
 * <p>
 * The file only exists while an init is unfinished: {@link #delete()} removes it once the first State has
 * been committed, which is what tells the next {@code fim init} that there is nothing to resume.
 */
public class InitProgressManager {
    public static final String INIT_PROGRESS_FILE = "init-progress.json.gz";

    private static final String TEMPORARY_SUFFIX = ".tmp";

    /**
     * How long to wait between two progress saves. Writing the progress costs a full serialization of
     * everything hashed so far, so it is done sparingly: an interruption gives up at most this much work.
     */
    private static final long SAVE_INTERVAL_MS = 60_000;

    private final Context context;
    private final Path progressFile;
    private final Path temporaryProgressFile;

    /**
     * The FileStates a previous init already hashed, by repository relative file name.
     * Filled by {@link #load()} before the scan starts and only read afterwards.
     */
    private final Map<String, FileState> alreadyHashedFiles = new HashMap<>();

    private final AtomicInteger reusedFileCount = new AtomicInteger();

    private long saveIntervalMs = SAVE_INTERVAL_MS;
    private ScheduledExecutorService autoSaveExecutor;
    private Supplier<List<FileState>> fileStatesSupplier;
    private volatile boolean initCompleted;

    public InitProgressManager(Context context) {
        this.context = context;
        this.progressFile = context.getRepositoryDotFimDir().resolve(INIT_PROGRESS_FILE);
        this.temporaryProgressFile = context.getRepositoryDotFimDir().resolve(INIT_PROGRESS_FILE + TEMPORARY_SUFFIX);
    }

    public Path getProgressFile() {
        return progressFile;
    }

    public boolean exists() {
        return Files.exists(progressFile);
    }

    /**
     * Tells whether the given directory holds the progress of an unfinished init.
     */
    public static boolean holdsInitProgress(Path dotFimDir) {
        return Files.exists(dotFimDir.resolve(INIT_PROGRESS_FILE));
    }

    void setSaveIntervalMs(long saveIntervalMs) {
        this.saveIntervalMs = saveIntervalMs;
    }

    /**
     * Read the hashes left by a previous init.
     * <p>
     * Progress that cannot be reused is dropped instead of failing the init, because starting the hashing
     * again is always a valid outcome. That covers a damaged file, a file written by another Fim version and
     * a file produced using a different hash mode: mixing hash modes inside one State would describe some
     * files more thoroughly than others.
     *
     * @return the number of hashes available for reuse
     */
    public int load() {
        alreadyHashedFiles.clear();

        if (!exists()) {
            return 0;
        }

        State progressState;
        try {
            progressState = State.loadFromGZipFile(progressFile, true);
        } catch (CorruptedStateException ex) {
            Logger.warning("The progress of the interrupted 'fim init' is corrupted. Hashing everything again.");
            return 0;
        } catch (IOException ex) {
            Logger.warning(String.format("Not able to read the progress of the interrupted 'fim init' (%s). Hashing everything again.",
                    ex.getMessage()));
            return 0;
        }

        if (!State.CURRENT_MODEL_VERSION.equals(progressState.getModelVersion())) {
            Logger.warning("The progress of the interrupted 'fim init' uses a different model version. Hashing everything again.");
            return 0;
        }

        HashMode progressHashMode = progressState.getHashMode();
        if (progressHashMode != context.getHashMode()) {
            Logger.warning(String.format("The interrupted 'fim init' was using the '%s' hash mode. Hashing everything again.",
                    progressHashMode));
            return 0;
        }

        for (FileState fileState : progressState.getFileStates()) {
            alreadyHashedFiles.put(fileState.getFileName(), fileState);
        }
        return alreadyHashedFiles.size();
    }

    /**
     * Look for a hash a previous init already computed for this file.
     * <p>
     * The hash is reused only when the file still has the length and the timestamps it had when it was
     * hashed. That is the same evidence {@code fim status} uses to call a file unmodified, so a file
     * touched between the two runs is hashed again rather than described by a stale hash.
     *
     * @return the FileState to reuse, or {@code null} when the file has to be hashed
     */
    public FileState findReusableFileState(String relativeFileName, long fileLength, FileTime fileTime) {
        FileState alreadyHashed = alreadyHashedFiles.get(relativeFileName);
        if (alreadyHashed == null) {
            return null;
        }

        if (alreadyHashed.getFileLength() != fileLength || !alreadyHashed.getFileTime().equals(fileTime)) {
            return null;
        }

        reusedFileCount.incrementAndGet();
        return alreadyHashed;
    }

    public int getReusedFileCount() {
        return reusedFileCount.get();
    }

    public boolean hasSomethingToReuse() {
        return !alreadyHashedFiles.isEmpty();
    }

    /**
     * Start saving the progress in the background, at a fixed pace, until {@link #stopAutoSave()} is called.
     * The supplier is asked for everything hashed so far, including the hashes reused from a previous run.
     */
    public void startAutoSave(Supplier<List<FileState>> supplier) {
        this.fileStatesSupplier = supplier;

        autoSaveExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "fim-init-progress");
            thread.setDaemon(true);
            return thread;
        });
        autoSaveExecutor.scheduleWithFixedDelay(this::saveCurrentProgress, saveIntervalMs, saveIntervalMs, TimeUnit.MILLISECONDS);
    }

    public void stopAutoSave() {
        if (autoSaveExecutor == null) {
            return;
        }

        autoSaveExecutor.shutdownNow();
        try {
            autoSaveExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        autoSaveExecutor = null;
    }

    /**
     * Save everything hashed so far, so that an init being stopped right now can be resumed.
     * Called from the JVM shutdown hook, hence the best effort behaviour: there is nothing useful left to
     * do with a failure at that point.
     */
    public void saveOnInterruption() {
        if (initCompleted) {
            return;
        }

        stopAutoSave();
        if (saveCurrentProgress()) {
            Logger.newLine();
            Logger.out.println("'fim init' interrupted. The hashes computed so far have been kept.");
            Logger.out.println("Run 'fim init' again in the same directory to resume it.");
        }
    }

    private boolean saveCurrentProgress() {
        if (initCompleted || fileStatesSupplier == null) {
            return false;
        }

        try {
            save(fileStatesSupplier.get());
            return true;
        } catch (Exception ex) {
            Logger.rawDebug(String.format("Not able to save the progress of 'fim init' into '%s': %s", progressFile, ex.getMessage()));
            return false;
        }
    }

    /**
     * Write the progress file, going through a temporary file so that the one in place stays usable until
     * the new one is complete.
     */
    public synchronized void save(List<FileState> fileStates) throws IOException {
        if (initCompleted) {
            return;
        }

        State progressState = new State();
        progressState.setComment(context.getComment());
        progressState.setHashMode(context.getHashMode());
        progressState.getFileStates().addAll(fileStates);
        progressState.saveToGZipFile(temporaryProgressFile);

        moveIntoPlace();
    }

    private void moveIntoPlace() throws IOException {
        try {
            Files.move(temporaryProgressFile, progressFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporaryProgressFile, progressFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Drop the progress file, which is what marks the init as finished. Called once the first State is
     * committed, and also when the progress could not be reused.
     */
    public synchronized void delete() {
        initCompleted = true;
        deleteQuietly(temporaryProgressFile);
        deleteQuietly(progressFile);
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            Logger.rawDebug(String.format("Not able to remove '%s': %s", file, ex.getMessage()));
        }
    }
}
