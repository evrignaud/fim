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

import org.fim.util.Console;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.apache.commons.io.FileUtils.byteCountToDisplaySize;
import static org.atteo.evo.inflector.English.plural;

public class DuplicateResult {
    private static final Comparator<DuplicateSet> wastedSpaceDescendingComparator = new WastedSpaceDescendingComparator();

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

    public void sortDuplicateSets() {
        Collections.sort(duplicateSets, wastedSpaceDescendingComparator);
    }

    public DuplicateResult displayDuplicates(PrintStream out) {
        if (context.isVerbose()) {
            for (DuplicateSet duplicateSet : duplicateSets) {
                int index = duplicateSets.indexOf(duplicateSet) + 1;
                List<FileState> duplicatedFiles = duplicateSet.getDuplicatedFiles();
                long fileLength = duplicatedFiles.get(0).getFileLength();
                int duplicateTime = duplicatedFiles.size() - 1;
                long wastedSpace = duplicateSet.getWastedSpace();
                out.printf("- Duplicate set #%d: duplicated %d %s, %s each, %s of wasted space%n",
                    index, duplicateTime, plural("time", duplicateTime),
                    byteCountToDisplaySize(fileLength), byteCountToDisplaySize(wastedSpace));
                for (FileState fileState : duplicatedFiles) {
                    out.printf("      %s%n", fileState.getFileName());
                }
                Console.newLine();
            }
        }

        if (duplicatedFilesCount > 0) {
            int duplicateCount = duplicateSets.size();
            out.printf("%d duplicated %s spread into %d duplicate %s, %s of total wasted space%n",
                duplicatedFilesCount, pluralForLong("file", duplicatedFilesCount),
                duplicateCount, plural("set", duplicateCount), byteCountToDisplaySize(totalWastedSpace));
        } else {
            out.println("No duplicated file found");
        }
        return this;
    }

    public long getDuplicatedFilesCount() {
        return duplicatedFilesCount;
    }

    public long getTotalWastedSpace() {
        return totalWastedSpace;
    }

    public List<DuplicateSet> getDuplicateSets() {
        return duplicateSets;
    }

    private String pluralForLong(String word, long count) {
        return plural(word, count > 1 ? 2 : 1);
    }

    public static class WastedSpaceDescendingComparator implements Comparator<DuplicateSet> {
        @Override
        public int compare(DuplicateSet ds1, DuplicateSet ds2) {
            return Long.compare(ds2.getWastedSpace(), ds1.getWastedSpace());
        }
    }
}
