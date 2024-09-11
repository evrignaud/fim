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

package org.fim.tooling;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.fim.model.Constants;
import org.fim.model.Context;
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.FileTime;
import org.fim.model.State;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;

import static java.lang.Math.min;

public class BuildableState extends State {
    private static final Comparator<FileState> FILE_NAME_COMPARATOR = new FileState.FileNameComparator();

    private final transient Context context;

    public BuildableState(Context context) {
        this.context = context;
    }

    public BuildableState addFiles(String... fileNames) {
        return addFiles(10_000, fileNames);
    }

    public BuildableState addEmptyFiles(String... fileNames) {
        return addFiles(0, fileNames);
    }

    public BuildableState addFiles(int maxFileLength, String... fileNames) {
        BuildableState newState = clone();
        for (String fileName : fileNames) {
            if (findFileState(newState, fileName, false) != null) {
                throw new IllegalArgumentException("New file: Duplicate fileName " + fileName);
            }

            // By default put the fileName as fileContent that will be the hash
            int fileLength = min(maxFileLength, fileName.length());
            String content = fileName.substring(0, fileLength);
            FileState fileState = new FileState(fileName, fileLength, new FileTime(getNow()), createHash(content), null);
            newState.getFileStates().add(fileState);
        }
        sortFileStates(newState);
        return newState;
    }

    public BuildableState copy(String sourceFileName, String targetFileName) {
        BuildableState newState = clone();
        if (findFileState(newState, targetFileName, false) != null) {
            throw new IllegalArgumentException("Copy: File already exist " + targetFileName);
        }

        FileState sourceFileState = findFileState(newState, sourceFileName, true);
        FileState targetFileState = new FileState(targetFileName, sourceFileState.getFileLength(), new FileTime(sourceFileState.getFileTime()),
                new FileHash(sourceFileState.getFileHash()), null);
        newState.getFileStates().add(targetFileState);
        sortFileStates(newState);
        return newState;
    }

    public BuildableState rename(String sourceFileName, String targetFileName) {
        BuildableState newState = clone();
        if (findFileState(newState, targetFileName, false) != null) {
            throw new IllegalArgumentException("Rename: File already exist " + targetFileName);
        }

        FileState fileState = findFileState(newState, sourceFileName, true);
        fileState.setFileName(targetFileName);
        sortFileStates(newState);
        return newState;
    }

    public BuildableState delete(String fileName) {
        BuildableState newState = clone();
        FileState fileState = findFileState(newState, fileName, true);
        newState.getFileStates().remove(fileState);
        return newState;
    }

    public BuildableState touch(String fileName) {
        BuildableState newState = clone();
        FileState fileState = findFileState(newState, fileName, true);
        long now = getNow();
        if (now < fileState.getFileTime().getLastModified() + 1_000) {
            now = fileState.getFileTime().getLastModified() + 1_000;
        }
        fileState.getFileTime().setLastModified(now);
        return newState;
    }

    public BuildableState setContent(String fileName, String fileContent) {
        BuildableState newState = clone();
        FileState fileState = findFileState(newState, fileName, true);
        fileState.setFileLength(fileContent.length());
        fileState.setFileHash(createHash(fileContent));
        return newState;
    }

    public BuildableState appendContent(String fileName, String fileContent) {
        BuildableState newState = clone();
        FileState fileState = findFileState(newState, fileName, true);
        fileState.setFileLength(fileState.getFileLength() + fileContent.length());
        fileState.setFileHash(appendHash(fileState.getFileHash(), fileContent));
        return newState;
    }

    public BuildableState forceDifferentFileLength(String fileName, long fileLength) {
        BuildableState newState = clone();
        FileState fileState = findFileState(newState, fileName, true);
        fileState.setFileLength(fileLength);
        return newState;
    }

    private FileHash createHash(String content) {
        String smallBlockHash = "small_block_" + content;
        String mediumBlockHash = "medium_block_" + content;
        String fullHash = "full_" + content;
        return createFileHash(smallBlockHash, mediumBlockHash, fullHash);
    }

    private FileHash appendHash(FileHash fileHash, String content) {
        String smallBlockHash = fileHash.getSmallBlockHash() + "_" + content;
        String mediumBlockHash = fileHash.getMediumBlockHash() + "_" + content;
        String fullHash = fileHash.getFullHash() + "_" + content;
        return createFileHash(smallBlockHash, mediumBlockHash, fullHash);
    }

    private FileHash createFileHash(String smallBlockHash, String mediumBlockHash, String fullHash) {
        switch (context.getHashMode()) {
            case dontHash:
                smallBlockHash = Constants.NO_HASH;
                mediumBlockHash = Constants.NO_HASH;
                fullHash = Constants.NO_HASH;
                break;

            case hashSmallBlock:
                mediumBlockHash = Constants.NO_HASH;
                fullHash = Constants.NO_HASH;
                break;

            case hashMediumBlock:
                fullHash = Constants.NO_HASH;
                break;

            case hashAll:
                // Nothing to do
                break;
        }

        return new FileHash(smallBlockHash, mediumBlockHash, fullHash);
    }

    private void sortFileStates(BuildableState state) {
        state.getFileStates().sort(FILE_NAME_COMPARATOR);
        state.updateFileCount();
        state.updateFilesContentLength();
    }

    private FileState findFileState(BuildableState state, String fileName, boolean throwEx) {
        for (FileState fileState : state.getFileStates()) {
            if (fileState.getFileName().equals(fileName)) {
                return fileState;
            }
        }

        if (throwEx) {
            throw new IllegalArgumentException("Unknown file " + fileName);
        }
        return null;
    }

    @JsonIgnore
    private long getNow() {
        return new Date().getTime();
    }

    @Override
    public BuildableState clone() {
        BuildableState cloned = new BuildableState(this.context);

        cloned.setComment(this.getComment());
        cloned.setTimestamp(this.getTimestamp());
        cloned.setHashMode(this.getHashMode());
        cloned.setIgnoredFiles(new HashSet<>(this.getIgnoredFiles()));
        cloned.setModificationCounts(this.getModificationCounts().clone());
        cloned.setCommitDetails(this.getCommitDetails().clone());
        cloned.setStateHash(this.getStateHash());

        cloned.setFileStates(null);
        if (this.getFileStates() != null) {
            ArrayList<FileState> clonedFileStates = new ArrayList<>();
            for (FileState fileState : this.getFileStates()) {
                clonedFileStates.add(fileState.clone());
            }
            cloned.setFileStates(clonedFileStates);
        }

        sortFileStates(cloned);

        return cloned;
    }
}
