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

package org.fim.internal;

import org.fim.model.Constants;
import org.fim.model.Context;
import org.fim.model.DuplicateResult;
import org.fim.model.FileHash;
import org.fim.model.FilePattern;
import org.fim.model.FileState;
import org.fim.model.State;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DuplicateFinder {
    private static final Comparator<FileState> HASH_COMPARATOR = new FileState.HashComparator();

    private final Context context;

    public DuplicateFinder(Context context) {
        this.context = context;
    }

    public DuplicateResult findDuplicates(State state) {
        DuplicateResult result = new DuplicateResult(context);

        List<FileState> fileStates = new ArrayList<>(state.getFileStates());
        fileStates.sort(HASH_COMPARATOR);

        List<FileState> duplicatedFiles = new ArrayList<>();
        long previousFileLength = 0;
        FileHash previousFileHash = new FileHash(Constants.NO_HASH, Constants.NO_HASH, Constants.NO_HASH);
        for (FileState fileState : fileStates) {
            if (fileState.getFileLength() == 0) {
                continue;
            }

            Path file = context.getRepositoryRootDir().resolve(fileState.getFileName());
            if (!Files.exists(file)) {
                continue;
            }
            String fileName = file.getFileName().toString();
            if (!FilePattern.matchPatterns(fileName, context.getIncludePatterns(), true) ||
                FilePattern.matchPatterns(fileName, context.getExcludePatterns(), false)) {
                continue;
            }

            if (previousFileLength != fileState.getFileLength() || !previousFileHash.equals(fileState.getFileHash())) {
                result.addDuplicatedFiles(duplicatedFiles);
                duplicatedFiles.clear();
            }

            previousFileLength = fileState.getFileLength();
            previousFileHash = fileState.getFileHash();
            duplicatedFiles.add(fileState);
        }
        result.addDuplicatedFiles(duplicatedFiles);

        result.sortDuplicateSets();
        return result;
    }
}
