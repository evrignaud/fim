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

import com.google.common.base.MoreObjects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class DuplicateSet {
    private final List<FileState> duplicatedFiles;
    private final long wastedSpace;

    public DuplicateSet(List<FileState> duplicatedFiles) {
        this.duplicatedFiles = new ArrayList<>(duplicatedFiles);
        wastedSpace = (this.duplicatedFiles.size() - 1) * this.duplicatedFiles.getFirst().getFileLength();
    }

    public List<FileState> getDuplicatedFiles() {
        return Collections.unmodifiableList(duplicatedFiles);
    }

    public long getWastedSpace() {
        return wastedSpace;
    }

    public int getDuplicatedFilesCount() {
        return duplicatedFiles.size();
    }

    public long getDuplicatedFileSize() {
        return duplicatedFiles.getFirst().getFileLength();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof DuplicateSet duplicateSet)) {
            return false;
        }

        return Objects.equals(this.duplicatedFiles, duplicateSet.duplicatedFiles);

    }

    @Override
    public int hashCode() {
        return Objects.hash(duplicatedFiles);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("duplicatedFiles", duplicatedFiles)
                .toString();
    }
}
