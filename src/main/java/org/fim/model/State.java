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
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.fim.util.Ascii85Util;
import org.fim.util.FileUtil;
import org.fim.util.JsonIO;
import org.fim.util.Logger;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.fim.model.HashMode.hashAll;
import static org.fim.util.Ascii85Util.UTF8;

public class State implements Hashable {
    public static final String CURRENT_MODEL_VERSION = "5";

    private static final Comparator<FileState> FILE_NAME_COMPARATOR = new FileState.FileNameComparator();
    private static final JsonIO JSON_IO = new JsonIO();

    private String stateHash; // Ensure the integrity of the complete State content

    private String modelVersion;
    private long timestamp;
    private String comment;
    private int fileCount;
    private long filesContentLength;
    private HashMode hashMode;
    private CommitDetails commitDetails;

    private ModificationCounts modificationCounts; // Not taken in account in equals(), hashCode(), hashObject()
    private Set<String> ignoredFiles;
    private List<FileState> fileStates;

    public State() {
        modelVersion = CURRENT_MODEL_VERSION;
        timestamp = System.currentTimeMillis();
        comment = "";
        fileCount = 0;
        filesContentLength = 0;
        hashMode = hashAll;
        modificationCounts = new ModificationCounts();
        ignoredFiles = new HashSet<>();
        fileStates = new ArrayList<>();
        commitDetails = new CommitDetails(hashMode, null);
    }

    public static State loadFromGZipFile(Path stateFile, boolean loadFullState) throws IOException, CorruptedStateException {
        try (Reader reader = new InputStreamReader(new GZIPInputStream(new FileInputStream(stateFile.toFile())), UTF8)) {
            State state = JSON_IO.getObjectMapper().readValue(reader, State.class);
            System.gc(); // Force to clean up unused memory

            if (state == null) {
                throw new CorruptedStateException();
            }

            if (loadFullState) {
                if (!CURRENT_MODEL_VERSION.equals(state.getModelVersion())) {
                    Logger.warning(String.format("State %s use a different model version. Some features will not work completely.",
                            stateFile.getFileName().toString()));
                } else {
                    checkIntegrity(state);
                }
            }
            return state;
        }
    }

    private static void checkIntegrity(State state) throws CorruptedStateException {
        String hash = state.hashState();
        if (!state.stateHash.equals(hash)) {
            throw new CorruptedStateException();
        }
    }

    public void saveToGZipFile(Path stateFile) throws IOException {
        fileStates.sort(FILE_NAME_COMPARATOR);

        updateFileCount();
        updateFilesContentLength();
        stateHash = hashState();

        try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(stateFile.toFile())), UTF8)) {
            JSON_IO.getObjectWriter().writeValue(writer, this);
        }
        System.gc(); // Force to clean up unused memory
    }

    public State filterDirectory(Path repositoryRootDir, Path currentDirectory, boolean keepFilesInside) {
        State filteredState = clone();
        filteredState.getFileStates().clear();

        String rootDir = FileUtil.getNormalizedFileName(repositoryRootDir);
        String curDir = FileUtil.getNormalizedFileName(currentDirectory);
        String subDirectory = FileUtil.getRelativeFileName(rootDir, curDir) + '/';

        fileStates.stream()
                .filter(fileState -> fileState.getFileName().startsWith(subDirectory) == keepFilesInside)
                .forEach(fileState -> filteredState.getFileStates().add(fileState));

        return filteredState;
    }

    public void updateFileCount() {
        fileCount = fileStates.size();
    }

    public void updateFilesContentLength() {
        filesContentLength = 0;
        for (FileState fileState : fileStates) {
            filesContentLength += fileState.getFileLength();
        }
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public long getTimestamp() {
        return timestamp;
    }

    protected void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        // Intern Strings to decrease memory usage
        this.comment = comment.intern();
    }

    public int getFileCount() {
        updateFileCount();
        return fileCount;
    }

    public long getFilesContentLength() {
        updateFilesContentLength();
        return filesContentLength;
    }

    public HashMode getHashMode() {
        return hashMode;
    }

    public void setHashMode(HashMode hashMode) {
        this.hashMode = hashMode;
    }

    public Set<String> getIgnoredFiles() {
        return ignoredFiles;
    }

    public void setIgnoredFiles(Set<String> ignoredFiles) {
        this.ignoredFiles = ignoredFiles;
    }

    public List<FileState> getFileStates() {
        return fileStates;
    }

    protected void setFileStates(ArrayList<FileState> fileStates) {
        this.fileStates = fileStates;
    }

    public ModificationCounts getModificationCounts() {
        return modificationCounts;
    }

    public void setModificationCounts(ModificationCounts modificationCounts) {
        this.modificationCounts = modificationCounts;
    }

    public CommitDetails getCommitDetails() {
        return commitDetails;
    }

    public void setCommitDetails(CommitDetails commitDetails) {
        this.commitDetails = commitDetails;
    }

    public String getStateHash() {
        return stateHash;
    }

    public void setStateHash(String stateHash) {
        this.stateHash = stateHash;
    }

    public String hashState() {
        HashFunction hashFunction = Hashing.sha512();
        Hasher hasher = hashFunction.newHasher(Constants.SIZE_10_MB);
        hashObject(hasher);
        HashCode hash = hasher.hash();
        return Ascii85Util.encode(hash.asBytes());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof State state)) {
            return false;
        }

        return Objects.equals(this.modelVersion, state.modelVersion)
               && Objects.equals(this.timestamp, state.timestamp)
               && Objects.equals(this.comment, state.comment)
               && Objects.equals(this.fileCount, state.fileCount)
               && Objects.equals(this.filesContentLength, state.filesContentLength)
               && Objects.equals(this.hashMode, state.hashMode)
               && Objects.equals(this.ignoredFiles, state.ignoredFiles)
               && Objects.equals(this.fileStates, state.fileStates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelVersion, timestamp, comment, fileCount, filesContentLength, hashMode, ignoredFiles, fileStates);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("stateHash", stateHash)
                .add("modelVersion", modelVersion)
                .add("timestamp", timestamp)
                .add("comment", comment)
                .add("fileCount", fileCount)
                .add("filesContentLength", filesContentLength)
                .add("hashMode", hashMode)
                .add("commitDetails", commitDetails)
                .add("modificationCounts", modificationCounts)
                .add("ignoredFiles", ignoredFiles)
                .add("fileStates", fileStates)
                .toString();
    }

    @Override
    public void hashObject(Hasher hasher) {
        hasher
                .putString("State", Charsets.UTF_8)
                .putChar(HASH_FIELD_SEPARATOR)
                .putString(modelVersion, Charsets.UTF_8)
                .putChar(HASH_FIELD_SEPARATOR)
                .putLong(timestamp)
                .putChar(HASH_FIELD_SEPARATOR)
                .putString(comment, Charsets.UTF_8)
                .putChar(HASH_FIELD_SEPARATOR)
                .putInt(fileCount)
                .putChar(HASH_FIELD_SEPARATOR)
                .putLong(filesContentLength)
                .putChar(HASH_FIELD_SEPARATOR)
                .putString(hashMode.name(), Charsets.UTF_8);

        hasher.putChar(HASH_OBJECT_SEPARATOR);
        for (String ignoredFile : ignoredFiles) {
            hasher
                    .putString(ignoredFile, Charsets.UTF_8)
                    .putChar(HASH_OBJECT_SEPARATOR);
        }

        hasher.putChar(HASH_OBJECT_SEPARATOR);
        for (FileState fileState : fileStates) {
            fileState.hashObject(hasher);
            hasher.putChar(HASH_OBJECT_SEPARATOR);
        }
    }

    @Override
    public State clone() {
        State cloned = new State();
        cloned.stateHash = this.stateHash;
        cloned.modelVersion = this.modelVersion;
        cloned.timestamp = this.timestamp;
        cloned.comment = this.comment;
        cloned.fileCount = this.fileCount;
        cloned.filesContentLength = this.filesContentLength;
        cloned.hashMode = this.hashMode;
        cloned.commitDetails = this.commitDetails.clone();
        cloned.modificationCounts = this.modificationCounts.clone();
        cloned.ignoredFiles = new HashSet<>(this.ignoredFiles);

        cloned.fileStates = null;
        if (this.fileStates != null) {
            cloned.fileStates = new ArrayList<>();
            for (FileState fileState : this.fileStates) {
                cloned.fileStates.add(fileState.clone());
            }
        }

        return cloned;
    }
}
