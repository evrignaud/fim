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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.apache.commons.lang3.SystemUtils;
import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.model.Difference;
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.FileTime;
import org.fim.model.Modification;
import org.fim.model.State;
import org.fim.util.Logger;
import org.fim.util.SELinux;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.fim.model.FileAttribute.DosFilePermissions;
import static org.fim.model.FileAttribute.PosixFilePermissions;
import static org.fim.model.FileAttribute.SELinuxLabel;
import static org.fim.model.HashMode.dontHash;
import static org.fim.model.Modification.deleted;
import static org.fim.util.FileStateUtil.buildFileHashList;
import static org.fim.util.FileStateUtil.buildFileNamesMap;
import static org.fim.util.FileStateUtil.buildHashCodeMap;

public class StateComparator {
    private final Context context;

    private State lastState;
    private final State currentState;

    private ListMultimap<FileHash, FileState> previousFileStates;
    private List<FileState> notFoundInCurrentFileState;
    private List<FileState> addedOrModified;
    private int notModifiedCount;

    private CompareResult result;
    private boolean hardwareCorruptionDetection;

    public StateComparator(Context context, State lastState, State currentState) {
        this.context = context;
        this.lastState = lastState;
        this.currentState = currentState;
        this.hardwareCorruptionDetection = false;

        init();
    }

    /**
     * Remove modification, previousFileState and deleted entries
     */
    public static void resetFileStates(List<FileState> fileStates) {
        for (Iterator<FileState> iterator = fileStates.iterator(); iterator.hasNext(); ) {
            FileState fileState = iterator.next();
            if (fileState.getModification() == deleted) {
                iterator.remove();
            } else {
                fileState.setModification(null);
                fileState.setPreviousFileState(null);
            }
        }
    }

    private void init() {
        if (lastState != null && !lastState.getModelVersion().equals(currentState.getModelVersion())) {
            Logger.warning("Not able to compare with a State that have a different model version.");
            lastState = null;
        }

        makeLastStateComparable();

        result = new CompareResult(context, lastState);

        previousFileStates = ArrayListMultimap.create();
        notFoundInCurrentFileState = new ArrayList<>();
        addedOrModified = new ArrayList<>();
    }

    /**
     * Allow to compare the current State with a State created on another OS.
     */
    private void makeLastStateComparable() {
        if (lastState == null) {
            return;
        }

        if (SystemUtils.IS_OS_WINDOWS) {
            filterOut(lastState, PosixFilePermissions.name());
        } else {
            filterOut(lastState, DosFilePermissions.name());
        }

        if (!SELinux.ENABLED) {
            filterOut(lastState, SELinuxLabel.name());
        }

        resetFileStates(lastState.getFileStates());

        if (context.getIgnored().isAttributesIgnored()) {
            currentState.getFileStates().forEach(fileState -> fileState.getFileAttributes().clear());
            lastState.getFileStates().forEach(fileState -> fileState.getFileAttributes().clear());
        }

        if (context.getIgnored().isDatesIgnored()) {
            FileTime noTime = new FileTime(0, 0);
            currentState.getFileStates().forEach(fileState -> fileState.setFileTime(noTime));
            lastState.getFileStates().forEach(fileState -> fileState.setFileTime(noTime));
        }
    }

    private void filterOut(State state, String unsupportedFileAttr) {
        final AtomicBoolean attrRemoved = new AtomicBoolean(false);
        state.getFileStates().stream()
                .filter(fileState -> fileState.getFileAttributes() != null)
                .forEach(fileState -> {
                    if (fileState.getFileAttributes().remove(unsupportedFileAttr) != null) {
                        attrRemoved.set(true);
                    }
                    if (fileState.getFileAttributes().isEmpty()) {
                        fileState.setFileAttributes(null);
                    }
                });

        if (attrRemoved.get()) {
            Logger.warning(String.format("Last State contain %s file attributes that are not supported. They are ignored", unsupportedFileAttr));
        }
    }

    public StateComparator searchForHardwareCorruption() {
        hardwareCorruptionDetection = true;
        return this;
    }

    public CompareResult compare() {
        searchForAddedOrModified();
        searchForSameFileNames();

        if (!hardwareCorruptionDetection) {
            searchForDifferences();
            checkAllFilesManagedCorrectly();

            searchForDeleted();
        }

        result.sortResults();
        return result;
    }

    private void searchForAddedOrModified() {
        if (lastState != null) {
            logDebug("---------------------------------------------------------------------",
                    "lastState", lastState.getFileStates(), "currentState", currentState.getFileStates());

            for (FileState fileState : lastState.getFileStates()) {
                previousFileStates.put(fileState.getFileHash(), fileState);
            }
        } else {
            logDebug("---------------------------------------------------------------------",
                    "currentState", currentState.getFileStates());
        }

        resetNewHash(previousFileStates.values());

        Map<Long, FileState> previousFileStatesHashCodeMap = buildHashCodeMap(previousFileStates.values());

        notModifiedCount = 0;
        List<FileState> fileStates = currentState.getFileStates();
        for (FileState fileState : fileStates) {
            if (previousFileStatesHashCodeMap.remove(fileState.longHashCode()) != null) {
                notModifiedCount++;
            } else {
                addedOrModified.add(fileState);
            }
        }
        notFoundInCurrentFileState.addAll(previousFileStatesHashCodeMap.values());

        logDebug("Built addedOrModified", "notFoundInCurrentFileState", notFoundInCurrentFileState, "addedOrModified", addedOrModified);
    }

    private void searchForSameFileNames() {
        Map<String, FileState> notFoundInCurrentFileStateNamesMap = buildFileNamesMap(notFoundInCurrentFileState);

        boolean managed;
        FileState previousFileState;
        List<FileState> newAddedOrModified = new ArrayList<>();
        for (FileState fileState : addedOrModified) {
            managed = false;
            if ((previousFileState = findFileWithSameFileName(fileState, notFoundInCurrentFileStateNamesMap)) != null) {
                notFoundInCurrentFileStateNamesMap.remove(previousFileState.getFileName());

                if (hardwareCorruptionDetection) {
                    if (!previousFileState.getFileHash().equals(fileState.getFileHash()) &&
                        previousFileState.getFileTime().equals(fileState.getFileTime())) {
                        result.getCorrupted().add(new Difference(previousFileState, fileState));
                        fileState.setModification(Modification.corrupted);
                        managed = true;
                    }
                } else {
                    if (sameContent(previousFileState, fileState)) {
                        if (!previousFileState.getFileTime().equals(fileState.getFileTime())) {
                            result.getDateModified().add(new Difference(previousFileState, fileState));
                            fileState.setModification(Modification.dateModified);
                            managed = true;
                        } else if (!Objects.equals(previousFileState.getFileAttributes(), fileState.getFileAttributes())) {
                            result.getAttributesModified().add(new Difference(previousFileState, fileState));
                            fileState.setModification(Modification.attributesModified);
                            managed = true;
                        }
                    } else {
                        result.getContentModified().add(new Difference(previousFileState, fileState));
                        fileState.setModification(Modification.contentModified);
                        managed = true;

                        // File has been modified so set the new hash for accurate duplicate detection
                        previousFileState.setNewFileHash(new FileHash(fileState.getFileHash()));
                    }
                }
            }

            if (!managed) {
                newAddedOrModified.add(fileState);
            }
        }
        addedOrModified = newAddedOrModified;
        notFoundInCurrentFileState = new ArrayList<>(notFoundInCurrentFileStateNamesMap.values());

        logDebug("Search done for same FileNames", "notFoundInCurrentFileState", notFoundInCurrentFileState, "addedOrModified", addedOrModified);
    }

    // Compare the FileLength and the FileHash
    private boolean sameContent(FileState fileState1, FileState fileState2) {
        return fileState1.getFileLength() == fileState2.getFileLength() && fileState1.getFileHash().equals(fileState2.getFileHash());
    }

    private void searchForDifferences() {
        ListMultimap<FileHash, FileState> notFoundInCurrentFileStateList = buildFileHashList(notFoundInCurrentFileState);
        Map<FileHash, FileState> foundInPreviousState = new HashMap<>();

        List<FileState> samePreviousHashes;
        for (FileState fileState : addedOrModified) {
            if ((fileState.getFileLength() > 0) &&
                (context.getHashMode() != dontHash) &&
                (!(samePreviousHashes = findFilesWithSameHash(fileState, previousFileStates)).isEmpty())) {
                FileState originalFileState = samePreviousHashes.getFirst();
                FileHash originalFileHash = originalFileState.getFileHash();
                if (notFoundInCurrentFileStateList.containsKey(originalFileHash) ||
                    foundInPreviousState.containsKey(originalFileHash)) {
                    if (context.getIgnored().isRenamedIgnored()) {
                        notModifiedCount++;
                    } else {
                        result.getRenamed().add(new Difference(originalFileState, fileState));
                        fileState.setModification(Modification.renamed);
                    }
                } else {
                    if (contentChanged(originalFileState)) {
                        result.getCopied().add(new Difference(originalFileState, fileState));
                        fileState.setModification(Modification.copied);
                    } else {
                        result.getDuplicated().add(new Difference(originalFileState, fileState));
                        fileState.setModification(Modification.duplicated);
                    }
                }
                List<FileState> removed = notFoundInCurrentFileStateList.removeAll(originalFileHash);
                if (removed != null && !removed.isEmpty()) {
                    // Used to check other duplicate files that have been renamed
                    foundInPreviousState.put(originalFileHash, originalFileState);
                }
            } else {
                result.getAdded().add(new Difference(null, fileState));
                fileState.setModification(Modification.added);
            }
        }
        addedOrModified.clear();
        notFoundInCurrentFileState = new ArrayList<>(notFoundInCurrentFileStateList.values());
    }

    private void checkAllFilesManagedCorrectly() {
        if (!addedOrModified.isEmpty()) {
            throw new IllegalStateException(String.format("Comparison algorithm error: addedOrModified size=%d", addedOrModified.size()));
        }

        if (notModifiedCount + result.modifiedCount() != currentState.getFileCount()) {
            throw new IllegalStateException(String.format("Comparison algorithm error: notModifiedCount=%d modifiedCount=%d currentStateFileCount=%d",
                    notModifiedCount, result.modifiedCount(), currentState.getFileCount()));
        }
    }

    private void searchForDeleted() {
        // Add as 'deleted' all the remaining entries that are not ignored
        notFoundInCurrentFileState.stream()
                .filter(fileState -> !isFileIgnored(fileState))
                .forEach(fileState -> {
                    fileState.setModification(deleted);
                    fileState.restoreOriginalHash();
                    result.getDeleted().add(new Difference(null, fileState));
                });
    }

    private boolean isFileIgnored(FileState fileState) {
        for (String ignoredFile : currentState.getIgnoredFiles()) {
            String fileName = fileState.getFileName();
            if (ignoredFile.endsWith("/")) {
                if (fileName.startsWith(ignoredFile)) {
                    return true;
                }
            } else {
                if (fileName.equals(ignoredFile)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean contentChanged(FileState fileState) {
        return !fileState.getFileHash().equals(fileState.getNewFileHash());
    }

    private void logDebug(String message, String desc, List<FileState> fileStates) {
        Logger.rawDebug("\n-- " + message);
        Logger.rawDebug(fileStatesToString(desc, fileStates));
    }

    private void logDebug(String message, String desc1, List<FileState> fileStates1, String desc2, List<FileState> fileStates2) {
        Logger.rawDebug("\n-- " + message);
        Logger.rawDebug(fileStatesToString(desc1, fileStates1));
        Logger.rawDebug(fileStatesToString(desc2, fileStates2));
    }

    protected String fileStatesToString(String message, List<FileState> fileStates) {
        if (!Logger.debugEnabled) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("  ").append(message).append(":\n");
        for (FileState fileState : fileStates) {
            builder.append("      ").append(fileState).append("\n");
        }
        return builder.toString();
    }

    private void resetNewHash(Collection<FileState> fileStates) {
        for (FileState fileState : fileStates) {
            fileState.resetNewHash();
        }
    }

    private FileState findFileWithSameFileName(FileState search, Map<String, FileState> fileStates) {
        return fileStates.get(search.getFileName());
    }

    private List<FileState> findFilesWithSameHash(FileState search, ListMultimap<FileHash, FileState> fileStates) {
        return fileStates.get(search.getFileHash());
    }
}
