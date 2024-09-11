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

public class CommitDetails {
    private HashMode hashModeUsedToGetTheStatus;
    private String fromSubDirectory;

    public CommitDetails() {
        // Empty constructor for Jackson
    }

    public CommitDetails(HashMode hashModeUsedToGetTheStatus, String fromSubDirectory) {
        setHashModeUsedToGetTheStatus(hashModeUsedToGetTheStatus);
        setFromSubDirectory(fromSubDirectory);
    }

    public HashMode getHashModeUsedToGetTheStatus() {
        return hashModeUsedToGetTheStatus;
    }

    public void setHashModeUsedToGetTheStatus(HashMode hashModeUsedToGetTheStatus) {
        this.hashModeUsedToGetTheStatus = hashModeUsedToGetTheStatus;
    }

    public String getFromSubDirectory() {
        return fromSubDirectory;
    }

    public void setFromSubDirectory(String fromSubDirectory) {
        this.fromSubDirectory = fromSubDirectory;
    }

    @Override
    public CommitDetails clone() {
        CommitDetails cloned = new CommitDetails();
        cloned.hashModeUsedToGetTheStatus = this.hashModeUsedToGetTheStatus;
        cloned.fromSubDirectory = this.fromSubDirectory;
        return cloned;
    }
}
