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

import com.google.common.base.Charsets;
import com.google.common.base.MoreObjects;
import com.google.common.hash.Hasher;

import java.util.Objects;

public class FileHash implements Comparable<FileHash>, Hashable {
    private String smallBlockHash;
    private String mediumBlockHash;
    private String fullHash;

    public FileHash() {
        // Empty constructor for Jackson
    }

    public FileHash(String smallBlockHash, String mediumBlockHash, String fullHash) {
        setSmallBlockHash(smallBlockHash);
        setMediumBlockHash(mediumBlockHash);
        setFullHash(fullHash);
    }

    public FileHash(FileHash fileHash) {
        this(fileHash.getSmallBlockHash(), fileHash.getMediumBlockHash(), fileHash.getFullHash());
    }

    public String getSmallBlockHash() {
        return smallBlockHash;
    }

    public void setSmallBlockHash(String smallBlockHash) {
        // Intern Strings to decrease memory usage
        this.smallBlockHash = smallBlockHash.intern();
    }

    public String getMediumBlockHash() {
        return mediumBlockHash;
    }

    public void setMediumBlockHash(String mediumBlockHash) {
        // Intern Strings to decrease memory usage
        this.mediumBlockHash = mediumBlockHash.intern();
    }

    public String getFullHash() {
        return fullHash;
    }

    public void setFullHash(String fullHash) {
        // Intern Strings to decrease memory usage
        this.fullHash = fullHash.intern();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof FileHash otherFileHash)) {
            return false;
        }

        return Objects.equals(this.smallBlockHash, otherFileHash.smallBlockHash)
               && Objects.equals(this.mediumBlockHash, otherFileHash.mediumBlockHash)
               && Objects.equals(this.fullHash, otherFileHash.fullHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(smallBlockHash, mediumBlockHash, fullHash);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("smallBlockHash", smallBlockHash)
                .add("mediumBlockHash", mediumBlockHash)
                .add("fullHash", fullHash)
                .toString();
    }

    @Override
    public int compareTo(FileHash other) {
        int value = smallBlockHash.compareTo(other.smallBlockHash);
        if (value != 0) {
            return value;
        }

        value = mediumBlockHash.compareTo(other.mediumBlockHash);
        if (value != 0) {
            return value;
        }

        return fullHash.compareTo(other.fullHash);
    }

    @Override
    public void hashObject(Hasher hasher) {
        hasher
                .putString("FileHash", Charsets.UTF_8)
                .putChar(HASH_FIELD_SEPARATOR)
                .putString(smallBlockHash, Charsets.UTF_8)
                .putChar(HASH_FIELD_SEPARATOR)
                .putString(mediumBlockHash, Charsets.UTF_8)
                .putChar(HASH_FIELD_SEPARATOR)
                .putString(fullHash, Charsets.UTF_8);
    }

    @Override
    public FileHash clone() {
        return new FileHash(smallBlockHash, mediumBlockHash, fullHash);
    }
}
