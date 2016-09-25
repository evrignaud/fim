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

import org.apache.commons.io.FileUtils;
import org.fim.util.Console;

import java.util.ArrayList;
import java.util.List;

import static org.atteo.evo.inflector.English.plural;

public class DuplicateResult {
    private final Context context;
    private final List<DuplicateSet> duplicateSets;
    private long duplicatedFilesCount;
    private long totalWastedSpace;

    public DuplicateResult(Context context) {
        this.context = context;
        this.duplicateSets = new ArrayList<>();
        this.duplicatedFilesCount = 0;
        this.totalWastedSpace = 0;
    }

    public void addDuplicatedFiles(List<FileState> duplicatedFiles) {
        if (duplicatedFiles.size() > 1) {
            duplicatedFilesCount += duplicatedFiles.size() - 1;

            duplicatedFiles.stream()
                .filter(fileState -> duplicatedFiles.indexOf(fileState) > 0)
                .forEach(fileState -> totalWastedSpace += fileState.getFileLength());

            DuplicateSet duplicateSet = new DuplicateSet(duplicatedFiles);
            duplicateSets.add(duplicateSet);
        }
    }

    public DuplicateResult displayDuplicates() {
        if (context.isVerbose()) {
            for (DuplicateSet duplicateSet : duplicateSets) {
                System.out.println("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
                long wastedSpace = getWastedSpace(duplicateSet);
                System.out.printf("- Duplicate set #%d, %s of wasted space%n", duplicateSets.indexOf(duplicateSet) + 1, FileUtils.byteCountToDisplaySize(wastedSpace));
                List<FileState> duplicatedFiles = duplicateSet.getDuplicatedFiles();
                for (FileState fileState : duplicatedFiles) {
                    if (duplicatedFiles.indexOf(fileState) == 0) {
                        int duplicateTime = duplicatedFiles.size() - 1;
                        System.out.printf("  %s duplicated %d %s%n", fileState.getFileName(), duplicateTime, plural("time", duplicateTime));
                    } else {
                        System.out.printf("      %s - %s%n", FileUtils.byteCountToDisplaySize(fileState.getFileLength()), fileState.getFileName());
                    }
                }
                Console.newLine();
            }
        }

        if (duplicatedFilesCount > 0) {
            int duplicateCount = duplicateSets.size();
            System.out.printf("%d duplicated %s spread into %d duplicate %s, %s of total wasted space%n",
                duplicatedFilesCount, pluralForLong("file", duplicatedFilesCount),
                duplicateCount, plural("set", duplicateCount), FileUtils.byteCountToDisplaySize(totalWastedSpace));
        } else {
            System.out.println("No duplicated file found");
        }
        return this;
    }

    public long getDuplicatedFilesCount() {
        return duplicatedFilesCount;
    }

    public long getTotalWastedSpace() {
        return totalWastedSpace;
    }

    public long getWastedSpace(DuplicateSet duplicateSet) {
        long wastedSpace = 0;
        List<FileState> duplicatedFiles = duplicateSet.getDuplicatedFiles();
        for (FileState fileState : duplicatedFiles) {
            if (duplicatedFiles.indexOf(fileState) > 0) {
                wastedSpace += fileState.getFileLength();
            }
        }
        return wastedSpace;
    }

    public List<DuplicateSet> getDuplicateSets() {
        return duplicateSets;
    }

    private String pluralForLong(String word, long count) {
        return plural(word, count > 1 ? 2 : 1);
    }
}
