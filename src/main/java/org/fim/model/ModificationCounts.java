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

import java.util.List;

public class ModificationCounts {
    private int added;
    private int copied;
    private int duplicated;
    private int dateModified;
    private int contentModified;
    private int attributesModified;
    private int renamed;
    private int deleted;
    private int corrupted;

    public ModificationCounts() {
        // Use the default values
    }

    public ModificationCounts(List<FileState> fileStates) {
        fileStates.stream()
                .filter(fileState -> fileState.getModification() != null)
                .forEach(fileState -> {
                    switch (fileState.getModification()) {
                        case added -> added++;
                        case copied -> copied++;
                        case duplicated -> duplicated++;
                        case dateModified -> dateModified++;
                        case contentModified -> contentModified++;
                        case attributesModified -> attributesModified++;
                        case renamed -> renamed++;
                        case deleted -> deleted++;
                        case corrupted -> corrupted++;
                        default -> {
                            // Nothing to do
                        }
                    }
                });
    }

    public int getAdded() {
        return added;
    }

    public void setAdded(int added) {
        this.added = added;
    }

    public int getCopied() {
        return copied;
    }

    public void setCopied(int copied) {
        this.copied = copied;
    }

    public int getDuplicated() {
        return duplicated;
    }

    public void setDuplicated(int duplicated) {
        this.duplicated = duplicated;
    }

    public int getDateModified() {
        return dateModified;
    }

    public void setDateModified(int dateModified) {
        this.dateModified = dateModified;
    }

    public int getContentModified() {
        return contentModified;
    }

    public void setContentModified(int contentModified) {
        this.contentModified = contentModified;
    }

    public int getAttributesModified() {
        return attributesModified;
    }

    public void setAttributesModified(int attributesModified) {
        this.attributesModified = attributesModified;
    }

    public int getRenamed() {
        return renamed;
    }

    public void setRenamed(int renamed) {
        this.renamed = renamed;
    }

    public int getDeleted() {
        return deleted;
    }

    public void setDeleted(int deleted) {
        this.deleted = deleted;
    }

    public int getCorrupted() {
        return corrupted;
    }

    public void setCorrupted(int corrupted) {
        this.corrupted = corrupted;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("added", added)
                .add("copied", copied)
                .add("duplicated", duplicated)
                .add("dateModified", dateModified)
                .add("contentModified", contentModified)
                .add("attributesModified", attributesModified)
                .add("renamed", renamed)
                .add("deleted", deleted)
                .add("corrupted", corrupted)
                .toString();
    }

    @Override
    public ModificationCounts clone() {
        ModificationCounts cloned = new ModificationCounts();
        cloned.added = this.added;
        cloned.copied = this.copied;
        cloned.duplicated = this.duplicated;
        cloned.dateModified = this.dateModified;
        cloned.contentModified = this.contentModified;
        cloned.attributesModified = this.attributesModified;
        cloned.renamed = this.renamed;
        cloned.deleted = this.deleted;
        cloned.corrupted = this.corrupted;
        return cloned;
    }
}
