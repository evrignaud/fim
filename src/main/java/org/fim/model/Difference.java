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
package org.fim.model;

import java.util.Comparator;

public class Difference {
    private FileState previousFileState;
    private FileState fileState;

    public Difference(FileState previousFileState, FileState fileState) {
        this.setPreviousFileState(previousFileState);
        this.setFileState(fileState);
    }

    public FileState getPreviousFileState() {
        return previousFileState;
    }

    public void setPreviousFileState(FileState previousFileState) {
        this.previousFileState = previousFileState;
    }

    public FileState getFileState() {
        return fileState;
    }

    public void setFileState(FileState fileState) {
        this.fileState = fileState;
    }

    public boolean isCreationTimeChanged() {
        return previousFileState.getFileTime().getCreationTime() / 1000 != fileState.getFileTime().getCreationTime() / 1000;
    }

    public boolean isLastModifiedChanged() {
        return previousFileState.getFileTime().getLastModified() / 1000 != fileState.getFileTime().getLastModified() / 1000;
    }

    public static class FileNameComparator implements Comparator<Difference> {
        @Override
        public int compare(Difference diff1, Difference diff2) {
            return diff1.getFileState().getFileName().compareTo(diff2.getFileState().getFileName());
        }
    }
}
