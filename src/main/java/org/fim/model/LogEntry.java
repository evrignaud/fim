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

import org.fim.util.Logger;

import static org.atteo.evo.inflector.English.plural;
import static org.fim.model.HashMode.hashAll;
import static org.fim.util.FileUtil.byteCountToDisplaySize;
import static org.fim.util.FormatUtil.formatDate;

public class LogEntry {
    private int stateNumber;
    private String comment;
    private long timestamp;
    private int fileCount;
    private ModificationCounts modificationCounts;
    private CommitDetails commitDetails;
    private CompareResult compareResult;
    private long filesContentLength;

    public LogEntry(Context context, State state, int stateNumber) {
        setStateNumber(stateNumber);
        setComment(state.getComment());
        setTimestamp(state.getTimestamp());
        setFileCount(state.getFileCount());
        setFilesContentLength(state.getFilesContentLength());
        setModificationCounts(state.getModificationCounts());
        setCommitDetails(getStateCommitDetails(state));
        setCompareResult(new CompareResult(context, null, state));
    }

    public int getStateNumber() {
        return stateNumber;
    }

    public void setStateNumber(int stateNumber) {
        this.stateNumber = stateNumber;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }

    public long getFilesContentLength() {
        return filesContentLength;
    }

    public void setFilesContentLength(long filesContentLength) {
        this.filesContentLength = filesContentLength;
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

    public CompareResult getCompareResult() {
        return compareResult;
    }

    public void setCompareResult(CompareResult compareResult) {
        this.compareResult = compareResult;
    }

    public void displayEntryHeader() {
        Logger.out.printf("- State #%d: %s (%d %s - %s - generated%s using hash mode %s)%n", getStateNumber(), formatDate(getTimestamp()),
                getFileCount(), plural("file", getFileCount()), byteCountToDisplaySize(getFilesContentLength()),
                commitDetails.getFromSubDirectory() != null ? " from " + commitDetails.getFromSubDirectory() : "",
                commitDetails.getHashModeUsedToGetTheStatus());
        if (!getComment().isEmpty()) {
            Logger.out.printf("\tComment: %s%n", getComment());
        }
    }

    private CommitDetails getStateCommitDetails(State state) {
        if (state.getCommitDetails() != null) {
            return state.getCommitDetails();
        }
        // For backward compatibility
        return new CommitDetails(hashAll, null);
    }
}
