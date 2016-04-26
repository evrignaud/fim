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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.apache.commons.lang3.SystemUtils;
import org.fim.model.*;
import org.fim.util.Logger;
import org.fim.util.SELinux;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.fim.model.FileAttribute.*;
import static org.fim.model.HashMode.dontHash;

public class StateComparator {
    private static boolean logDebugEnabled = Boolean.getBoolean("DEBUG");

    private final Context context;

    private State lastState;
    private State currentState;

    private ListMultimap<FileHash, FileState> previousFileStates;
    private List<FileState> notFoundInCurrentFileState;
    private List<FileState> addedOrModified;
    private int notModifiedCount;

    private CompareResult result;

    public StateComparator(Context context, State lastState, State currentState) {
        this.context = context;
        this.lastState = lastState;
        this.currentState = currentState;

        init();
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
        result.setSearchForHardwareCorruption(true);
        return this;
    }

    public CompareResult compare() {
        searchForAddedOrModified();
        searchForSameFileNames();

        if (!result.isSearchForHardwareCorruption()) {
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
        for (int index = 0, fileStatesSize = fileStates.size(); index < fileStatesSize; index++) {
            FileState fileState = fileStates.get(index);
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

                if (result.isSearchForHardwareCorruption()) {
                    if (!previousFileState.getFileHash().equals(fileState.getFileHash()) && previousFileState.getFileTime().equals(fileState.getFileTime())) {
                        result.getCorrupted().add(new Difference(previousFileState, fileState));
                        fileState.setModification(Modification.corrupted);
                        managed = true;
                    }
                } else {
                    if (previousFileState.getFileHash().equals(fileState.getFileHash())) {
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

    private void searchForDifferences() {
        Map<Long, FileState> notFoundInCurrentFileStateHashCodeMap = buildHashCodeMap(notFoundInCurrentFileState);

        List<FileState> samePreviousHash;
        for (FileState fileState : addedOrModified) {
            if ((fileState.getFileLength() > 0) &&
                (context.getHashMode() != dontHash) &&
                ((samePreviousHash = findFilesWithSameHash(fileState, previousFileStates)).size() > 0)) {
                FileState originalFileState = samePreviousHash.get(0);
                long originalFileStateHashCode = originalFileState.longHashCode();
                if (notFoundInCurrentFileStateHashCodeMap.containsKey(originalFileStateHashCode)) {
                    result.getRenamed().add(new Difference(originalFileState, fileState));
                    fileState.setModification(Modification.renamed);
                } else {
                    if (contentChanged(originalFileState)) {
                        result.getCopied().add(new Difference(originalFileState, fileState));
                        fileState.setModification(Modification.copied);
                    } else {
                        result.getDuplicated().add(new Difference(originalFileState, fileState));
                        fileState.setModification(Modification.duplicated);
                    }
                }
                notFoundInCurrentFileStateHashCodeMap.remove(originalFileStateHashCode);
            } else {
                result.getAdded().add(new Difference(null, fileState));
                fileState.setModification(Modification.added);
            }
        }
        addedOrModified.clear();
        notFoundInCurrentFileState = new ArrayList<>(notFoundInCurrentFileStateHashCodeMap.values());
    }

    private Map<String, FileState> buildFileNamesMap(Collection<FileState> fileStates) {
        Map<String, FileState> fileNamesMap = new HashMap<>();
        for (FileState fileState : fileStates) {
            fileNamesMap.put(fileState.getFileName(), fileState);
        }

        // Check that no entry is duplicated
        if (fileStates.size() != fileNamesMap.size()) {
            throw new IllegalStateException(String.format("Duplicated entries: Size=%d, MapSize=%d", fileStates.size(), fileNamesMap.size()));
        }
        return fileNamesMap;
    }

    private Map<Long, FileState> buildHashCodeMap(Collection<FileState> fileStates) {
        Map<Long, FileState> hashCodeMap = new HashMap<>();
        for (FileState fileState : fileStates) {
            hashCodeMap.put(fileState.longHashCode(), fileState);
        }

        // Check that no entry is duplicated
        if (fileStates.size() != hashCodeMap.size()) {
            throw new IllegalStateException(String.format("Duplicated entries: Size=%d, MapSize=%d", fileStates.size(), hashCodeMap.size()));
        }
        return hashCodeMap;
    }

    private void checkAllFilesManagedCorrectly() {
        if (addedOrModified.size() != 0) {
            throw new IllegalStateException(String.format("Comparison algorithm error: addedOrModified size=%d", addedOrModified.size()));
        }

        if (notModifiedCount + result.modifiedCount() != currentState.getFileCount()) {
            throw new IllegalStateException(String.format("Comparison algorithm error: notModifiedCount=%d modifiedCount=%d currentStateFileCount=%d",
                notModifiedCount, result.modifiedCount(), currentState.getFileCount()));
        }
    }

    private void searchForDeleted() {
        notFoundInCurrentFileState.stream()
            .filter(fileState -> !isFileIgnored(fileState))
            .forEach(fileState -> result.getDeleted().add(new Difference(null, fileState)));
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
        logDebug("\n-- " + message);
        logDebug(fileStatesToString(desc, fileStates));
    }

    private void logDebug(String message, String desc_1, List<FileState> fileStates_1, String desc_2, List<FileState> fileStates_2) {
        logDebug("\n-- " + message);
        logDebug(fileStatesToString(desc_1, fileStates_1));
        logDebug(fileStatesToString(desc_2, fileStates_2));
    }

    private void logDebug(String message) {
        if (logDebugEnabled) {
            System.out.println(message);
        }
    }

    private String fileStatesToString(String message, List<FileState> fileStates) {
        if (!logDebugEnabled) {
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
