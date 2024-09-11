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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Charsets;
import com.google.common.base.MoreObjects;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class FileState implements Hashable {
    private String fileName;
    private long fileLength;
    private FileTime fileTime;
    private Modification modification;
    private FileHash fileHash;
    private Map<String, String> fileAttributes;
    private FileState previousFileState;

    private transient FileHash newFileHash; // Used by StateComparator to detect accurately duplicates
    private transient FileHash originalFileHash;
    private transient boolean toRemove;

    public FileState() {
        // Empty constructor for Jackson
    }

    public FileState(String fileName, long fileLength, FileTime fileTime, FileHash fileHash, List<Attribute> attributeList) {
        if (fileName == null) {
            throw new IllegalArgumentException("Invalid null fileName");
        }
        if (fileHash == null) {
            throw new IllegalArgumentException("Invalid null hash");
        }

        setFileName(fileName);
        setFileLength(fileLength);
        setFileTime(fileTime);
        setFileHash(fileHash);
        setFileAttributesList(attributeList);
    }

    public FileState(String fileName, BasicFileAttributes attributes, FileHash fileHash, List<Attribute> attributeList) {
        this(fileName, attributes.size(), new FileTime(attributes), fileHash, attributeList);
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        // Intern Strings to decrease memory usage
        this.fileName = fileName.intern();
    }

    public long getFileLength() {
        return fileLength;
    }

    public void setFileLength(long fileLength) {
        this.fileLength = fileLength;
    }

    public FileTime getFileTime() {
        return fileTime;
    }

    public void setFileTime(FileTime fileTime) {
        this.fileTime = fileTime;
    }

    public Modification getModification() {
        return modification;
    }

    public void setModification(Modification modification) {
        this.modification = modification;
    }

    public FileHash getFileHash() {
        return fileHash;
    }

    public void setFileHash(FileHash fileHash) {
        this.fileHash = fileHash;
    }

    public Map<String, String> getFileAttributes() {
        return fileAttributes;
    }

    private void setFileAttributesList(List<Attribute> attributeList) {
        this.fileAttributes = toMap(attributeList);
    }

    public void setFileAttributes(Map<String, String> fileAttributes) {
        this.fileAttributes = internAttributes(fileAttributes);
    }

    public FileState getPreviousFileState() {
        return previousFileState;
    }

    public void setPreviousFileState(FileState previousFileState) {
        this.previousFileState = previousFileState;
    }

    @JsonIgnore
    public FileHash getNewFileHash() {
        return newFileHash;
    }

    public void setNewFileHash(FileHash newFileHash) {
        this.newFileHash = newFileHash;
    }

    public void resetNewHash() {
        newFileHash = fileHash;
    }

    public void storeOriginalHash() {
        originalFileHash = fileHash;
    }

    public void restoreOriginalHash() {
        if (originalFileHash != null) {
            fileHash = originalFileHash;
        }
    }

    public boolean isToRemove() {
        return toRemove;
    }

    public void setToRemove(boolean toRemove) {
        this.toRemove = toRemove;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof FileState otherFileState)) {
            return false;
        }

        return Objects.equals(this.fileName, otherFileState.fileName)
               && Objects.equals(this.fileLength, otherFileState.fileLength)
               && Objects.equals(this.fileTime, otherFileState.fileTime)
               && Objects.equals(this.fileHash, otherFileState.fileHash)
               && Objects.equals(this.fileAttributes, otherFileState.fileAttributes);
    }

    /**
     * @deprecated hashCode() should not be used, because there is a big risk of hash collision. Use longHashCode() instead.
     * Those hash collision appears when you manage millions of FileStates.
     */
    @Override
    @Deprecated
    public int hashCode() {
        return Objects.hash(fileName, fileLength, fileTime, fileHash, fileAttributes);
    }

    /**
     * Returns a long hash code value for the object.
     * A long is used to avoid hashCode collisions when we have a huge number of FileStates.
     */
    public long longHashCode() {
        HashFunction hashFunction = Hashing.sha512();
        Hasher hasher = hashFunction.newHasher(Constants.SIZE_4_KB);
        hashObject(hasher, true);
        HashCode hash = hasher.hash();
        return hash.asLong();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("fileName", fileName)
                .add("fileLength", fileLength)
                .add("fileTime", fileTime)
                .add("modification", modification)
                .add("fileHash", fileHash)
                .add("fileAttributes", fileAttributes)
                .add("previousFileState", previousFileState)
                .add("newFileHash", newFileHash)
                .add("originalFileHash", originalFileHash)
                .add("toRemove", toRemove)
                .toString();
    }

    @Override
    public void hashObject(Hasher hasher) {
        hashObject(hasher, false);
    }

    public void hashObject(Hasher hasher, boolean millisecondsRemoved) {
        hasher
                .putString("FileState", Charsets.UTF_8)
                .putChar(HASH_FIELD_SEPARATOR)
                .putString(fileName, Charsets.UTF_8)
                .putChar(HASH_FIELD_SEPARATOR)
                .putLong(fileLength);

        hasher.putChar(HASH_OBJECT_SEPARATOR);
        fileTime.hashObject(hasher, millisecondsRemoved);

        hasher.putChar(HASH_OBJECT_SEPARATOR);
        fileHash.hashObject(hasher);

        hasher.putChar(HASH_OBJECT_SEPARATOR);
        if (fileAttributes != null) {
            for (Map.Entry<String, String> entry : fileAttributes.entrySet()) {
                hasher
                        .putString(entry.getKey(), Charsets.UTF_8)
                        .putChar(':')
                        .putChar(':')
                        .putString(entry.getValue(), Charsets.UTF_8);
                hasher.putChar(HASH_OBJECT_SEPARATOR);
            }
        }
    }

    @Override
    public FileState clone() {
        FileState cloned = new FileState();
        cloned.fileName = this.fileName;
        cloned.fileLength = this.fileLength;
        cloned.fileTime = this.fileTime.clone();
        cloned.modification = this.modification;
        cloned.fileHash = this.fileHash.clone();
        cloned.fileAttributes = internAttributes(fileAttributes);
        cloned.previousFileState = this.previousFileState != null ? this.previousFileState.clone() : null;

        cloned.newFileHash = this.newFileHash != null ? this.newFileHash.clone() : null;
        cloned.originalFileHash = this.originalFileHash != null ? this.originalFileHash.clone() : null;
        cloned.toRemove = this.toRemove;

        return cloned;
    }

    private Map<String, String> toMap(List<Attribute> attrs) {
        if (attrs == null) {
            return null;
        }

        Map<String, String> map = new HashMap<>();
        for (Attribute attr : attrs) {
            // Intern Strings to decrease memory usage
            map.put(attr.getName().intern(), attr.getValue().intern());
        }
        return map;
    }

    /**
     * Intern Map content to decrease memory usage
     */
    private Map<String, String> internAttributes(Map<String, String> attributes) {
        if (attributes == null) {
            return null;
        }

        Map<String, String> newAttributes = new HashMap<>();
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            newAttributes.put(entry.getKey().intern(), entry.getValue().intern());
        }
        return newAttributes;
    }

    public static class FileNameComparator implements Comparator<FileState> {
        @Override
        public int compare(FileState fs1, FileState fs2) {
            return fs1.getFileName().compareTo(fs2.getFileName());
        }
    }

    public static class HashComparator implements Comparator<FileState> {
        @Override
        public int compare(FileState fs1, FileState fs2) {
            return fs1.getFileHash().compareTo(fs2.getFileHash());
        }
    }
}
