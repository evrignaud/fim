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

import com.google.common.base.Charsets;
import com.google.common.base.MoreObjects;
import com.google.common.hash.Hasher;
import org.fim.util.ObjectsUtil;

import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class FileState implements Hashable {
    private String fileName;
    private long fileLength;
    private FileTime fileTime;
    private Modification modification;
    private FileHash fileHash;
    private Map<String, String> fileAttributes;

    private transient FileHash newFileHash; // Used by StateComparator to detect accurately duplicates

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
        setFileAttributes(toMap(attributeList));
    }

    public FileState(String fileName, BasicFileAttributes attributes, FileHash fileHash, List<Attribute> attributeList) {
        this(fileName, attributes.size(), new FileTime(attributes), fileHash, attributeList);
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
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

    public void setFileAttributes(Map<String, String> fileAttributes) {
        this.fileAttributes = fileAttributes;
    }

    public FileHash getNewFileHash() {
        return newFileHash;
    }

    public void setNewFileHash(FileHash newFileHash) {
        this.newFileHash = newFileHash;
    }

    public void resetNewHash() {
        newFileHash = fileHash;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || !(other instanceof FileState)) {
            return false;
        }

        FileState otherFileState = (FileState) other;

        return Objects.equals(this.fileName, otherFileState.fileName)
            && Objects.equals(this.fileLength, otherFileState.fileLength)
            && Objects.equals(this.fileTime, otherFileState.fileTime)
            && Objects.equals(this.fileHash, otherFileState.fileHash)
            && Objects.equals(this.fileAttributes, otherFileState.fileAttributes);
    }

    /**
     * @deprecated hashCode() should not be used, because there is a big risk of hash collision. Use longHashCode() instead.
     *             Those hash collision appears when you manage millions of FileStates.
     */
    @Override
    public int hashCode() {
        return Objects.hash(fileName, fileLength, fileTime, fileHash, fileAttributes);
    }

    /**
     * Returns a long hash code value for the object.
     * A long is used to avoid hashCode collisions when we have a huge number of FileStates.
     */
    public long longHashCode() {
        return ObjectsUtil.longHash(fileName, fileLength, fileTime, fileHash, fileAttributes);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("fileName", fileName)
            .add("fileLength", fileLength)
            .add("fileTime", fileTime)
            .add("fileHash", fileHash)
            .add("fileAttributes", fileAttributes)
            .add("newFileHash", newFileHash)
            .toString();
    }

    @Override
    public void hashObject(Hasher hasher) {
        hasher
            .putString("FileState", Charsets.UTF_8)
            .putChar(HASH_FIELD_SEPARATOR)
            .putString(fileName, Charsets.UTF_8)
            .putChar(HASH_FIELD_SEPARATOR)
            .putLong(fileLength);

        hasher.putChar(HASH_OBJECT_SEPARATOR);
        fileTime.hashObject(hasher);

        hasher.putChar(HASH_OBJECT_SEPARATOR);
        fileHash.hashObject(hasher);

        hasher.putChar(HASH_OBJECT_SEPARATOR);
        if (fileAttributes != null) {
            for (String key : fileAttributes.keySet()) {
                hasher
                    .putString(key, Charsets.UTF_8)
                    .putChar(':')
                    .putChar(':')
                    .putString(fileAttributes.get(key), Charsets.UTF_8);
                hasher.putChar(HASH_OBJECT_SEPARATOR);
            }
        }
    }

    private Map<String, String> toMap(List<Attribute> attrs) {
        if (attrs == null) {
            return null;
        }

        Map<String, String> map = new HashMap<>();
        for (Attribute attr : attrs) {
            map.put(attr.getName(), attr.getValue());
        }
        return map;
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
