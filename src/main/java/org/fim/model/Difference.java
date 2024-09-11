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

package org.fim.model;

import java.util.Comparator;

public class Difference {
    private FileState fileState;

    public Difference(FileState previousFileState, FileState fileState) {
        this.setFileState(fileState);
        if (previousFileState != null) {
            FileState clonedFileState = previousFileState.clone();
            clonedFileState.restoreOriginalHash(); // To get all the hash and not 'no_hash' depending on the hash mode
            this.setPreviousFileState(clonedFileState);
        }
    }

    public Difference(FileState fileState) {
        this.setFileState(fileState);
    }

    public FileState getPreviousFileState() {
        return fileState.getPreviousFileState();
    }

    public void setPreviousFileState(FileState previousFileState) {
        fileState.setPreviousFileState(previousFileState);
    }

    public FileState getFileState() {
        return fileState;
    }

    public void setFileState(FileState fileState) {
        this.fileState = fileState;
    }

    public boolean isCreationTimeChanged() {
        return getPreviousFileState().getFileTime().getCreationTime() / 1000 != fileState.getFileTime().getCreationTime() / 1000;
    }

    public boolean isLastModifiedChanged() {
        return getPreviousFileState().getFileTime().getLastModified() / 1000 != fileState.getFileTime().getLastModified() / 1000;
    }

    public static class FileNameComparator implements Comparator<Difference> {
        @Override
        public int compare(Difference diff1, Difference diff2) {
            return diff1.getFileState().getFileName().compareTo(diff2.getFileState().getFileName());
        }
    }
}
