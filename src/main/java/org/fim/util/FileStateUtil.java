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

package org.fim.util;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.fim.model.FileHash;
import org.fim.model.FileState;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class FileStateUtil {
    public static Map<String, FileState> buildFileNamesMap(Collection<FileState> fileStates) {
        Map<String, FileState> fileNamesMap = new HashMap<>();
        for (FileState fileState : fileStates) {
            fileNamesMap.put(fileState.getFileName(), fileState);
        }

        // Check that no entry is duplicated
        if (fileStates.size() != fileNamesMap.size()) {
            throw new IllegalStateException(String.format("Duplicated entries: Size=%d, MapSize=%d", fileStates.size(), fileNamesMap.size()));
        }
        return fileNamesMap;
    }

    public static Map<Long, FileState> buildHashCodeMap(Collection<FileState> fileStates) {
        Map<Long, FileState> hashCodeMap = new HashMap<>();
        for (FileState fileState : fileStates) {
            hashCodeMap.put(fileState.longHashCode(), fileState);
        }

        // Check that no entry is duplicated
        if (fileStates.size() != hashCodeMap.size()) {
            throw new IllegalStateException(String.format("Duplicated entries: Size=%d, MapSize=%d", fileStates.size(), hashCodeMap.size()));
        }
        return hashCodeMap;
    }

    public static ListMultimap<FileHash, FileState> buildFileHashList(Collection<FileState> fileStates) {
        ListMultimap<FileHash, FileState> fileHashMap = ArrayListMultimap.create();
        for (FileState fileState : fileStates) {
            fileHashMap.put(fileState.getFileHash(), fileState);
        }
        return fileHashMap;
    }
}
