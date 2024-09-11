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

import org.fim.internal.DuplicateOutputGenerator;
import org.fim.util.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

import static org.atteo.evo.inflector.English.plural;
import static org.fim.util.FileUtil.byteCountToDisplaySize;
import static org.fim.util.FileUtil.removeFile;

public class DuplicateResult {
    private final Context context;
    private final List<DuplicateSet> duplicateSets;
    private long duplicatedFilesCount;
    private long totalWastedSpace;
    private long filesRemoved;
    private long spaceFreed;

    public DuplicateResult(Context context) {
        this.context = context;
        this.duplicateSets = new ArrayList<>();
        this.duplicatedFilesCount = 0;
        this.totalWastedSpace = 0;
        this.filesRemoved = 0;
        this.spaceFreed = 0;
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
        Comparator<DuplicateSet> duplicateSetComparator = createDuplicateSetComparator();
        duplicateSets.sort(duplicateSetComparator);
    }

    private Comparator<DuplicateSet> createDuplicateSetComparator() {
        return switch (context.getSortMethod()) {
            case wasted -> new WastedSpaceComparator();
            case number -> new NumberOfFileComparator();
            case size -> new FileSizeComparator();
            default -> null;
        };
    }

    public DuplicateResult displayAndRemoveDuplicates() {
        if (context.getOutputType() != OutputType.human) {
            DuplicateOutputGenerator generator = new DuplicateOutputGenerator(context);
            generator.generate(this);
            return this;
        }

        if (context.isVerbose() || context.isRemoveDuplicates()) {
            for (DuplicateSet duplicateSet : duplicateSets) {
                manageDuplicateSet(duplicateSet);
            }
        }

        if (filesRemoved == 0) {
            if (duplicatedFilesCount > 0) {
                Logger.out.printf("%d duplicate %s, %s of total wasted space%n",
                        duplicatedFilesCount, pluralForLong("file", duplicatedFilesCount), byteCountToDisplaySize(totalWastedSpace));
            } else {
                Logger.out.println("No duplicate file found");
            }
        } else {
            Logger.out.printf("Removed %d files and freed %s%n", filesRemoved, byteCountToDisplaySize(spaceFreed));
            long remainingDuplicates = duplicatedFilesCount - filesRemoved;
            long remainingWastedSpace = totalWastedSpace - spaceFreed;
            if (remainingDuplicates > 0) {
                Logger.out.printf("Still have %d duplicate %s, %s of total wasted space%n",
                        remainingDuplicates, pluralForLong("file", remainingDuplicates), byteCountToDisplaySize(remainingWastedSpace));
            } else {
                Logger.out.println("No duplicate file remains");
            }
        }
        return this;
    }

    private void manageDuplicateSet(DuplicateSet duplicateSet) {
        int index = duplicateSets.indexOf(duplicateSet) + 1;
        List<FileState> duplicatedFiles = duplicateSet.getDuplicatedFiles();
        long fileLength = duplicatedFiles.getFirst().getFileLength();
        int duplicateTime = duplicatedFiles.size() - 1;
        long wastedSpace = duplicateSet.getWastedSpace();
        Logger.out.printf("- Duplicate set #%d: duplicated %d %s, %s each, %s of wasted space%n",
                index, duplicateTime, plural("time", duplicateTime),
                byteCountToDisplaySize(fileLength), byteCountToDisplaySize(wastedSpace));

        if (context.isRemoveDuplicates()) {
            selectFilesToRemove(duplicatedFiles);
        }

        String action;
        for (FileState fileState : duplicatedFiles) {
            action = "   ";
            if (fileState.isToRemove() && removeFile(context, context.getRepositoryRootDir(), fileState)) {
                action = "[-]";
                filesRemoved++;
                spaceFreed += fileState.getFileLength();
            }
            Logger.out.printf("  %s %s%n", action, fileState.getFileName());
        }
        Logger.newLine();
    }

    protected void selectFilesToRemove(List<FileState> duplicatedFiles) {
        if (context.isAlwaysYes()) {
            for (FileState fileState : duplicatedFiles) {
                if (duplicatedFiles.indexOf(fileState) > 0) {
                    fileState.setToRemove(true);
                }
            }
        } else {
            for (FileState fileState : duplicatedFiles) {
                int index = duplicatedFiles.indexOf(fileState) + 1;
                Logger.out.printf("  [%s] %s%n", index, fileState.getFileName());
            }

            while (true) {
                Logger.out.printf("  Preserve files [1 - %d, all or a, none or n]: ", duplicatedFiles.size());
                String inputLine = readInputLine();
                if (manageAnswers(duplicatedFiles, inputLine)) {
                    break;
                }
            }
        }
    }

    protected boolean manageAnswers(List<FileState> duplicatedFiles, String inputLine) {
        boolean gotCorrectAnswer = false;

        for (FileState fileState : duplicatedFiles) {
            fileState.setToRemove(true);
        }

        try (Scanner scanner = new Scanner(inputLine)) {
            while (scanner.hasNext()) {
                String answer = scanner.next();
                if ("a".equals(answer) || "all".equals(answer)) {
                    for (FileState fileState : duplicatedFiles) {
                        fileState.setToRemove(false);
                    }
                    gotCorrectAnswer = true;
                    break;
                } else if ("n".equals(answer) || "none".equals(answer)) {
                    gotCorrectAnswer = true;

                    // Add the additional file that we will remove. It was not counted in the initial wasted space.
                    duplicatedFilesCount++;
                    totalWastedSpace += duplicatedFiles.getFirst().getFileLength();
                    break;
                }

                int index = safeParseInt(answer);

                if (index >= 1 && index <= duplicatedFiles.size()) {
                    duplicatedFiles.get(index - 1).setToRemove(false);
                    gotCorrectAnswer = true;
                }
            }
        }
        return gotCorrectAnswer;
    }

    private String readInputLine() {
        try (Scanner scanner = new Scanner(System.in)) {
            return scanner.nextLine();
        }
    }

    private int safeParseInt(String answer) {
        try {
            return Integer.parseInt(answer);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    public long getDuplicatedFilesCount() {
        return duplicatedFilesCount;
    }

    public long getTotalWastedSpace() {
        return totalWastedSpace;
    }

    public long getFilesRemoved() {
        return filesRemoved;
    }

    public long getSpaceFreed() {
        return spaceFreed;
    }

    public List<DuplicateSet> getDuplicateSets() {
        return duplicateSets;
    }

    private String pluralForLong(String word, long count) {
        return plural(word, count > 1 ? 2 : 1);
    }

    public class WastedSpaceComparator implements Comparator<DuplicateSet> {
        @Override
        public int compare(DuplicateSet ds1, DuplicateSet ds2) {
            if (context.isSortAscending()) {
                return Long.compare(ds1.getWastedSpace(), ds2.getWastedSpace());
            } else {
                return Long.compare(ds2.getWastedSpace(), ds1.getWastedSpace());
            }
        }
    }

    public class NumberOfFileComparator implements Comparator<DuplicateSet> {
        @Override
        public int compare(DuplicateSet ds1, DuplicateSet ds2) {
            if (context.isSortAscending()) {
                return Integer.compare(ds1.getDuplicatedFilesCount(), ds2.getDuplicatedFilesCount());
            } else {
                return Integer.compare(ds2.getDuplicatedFilesCount(), ds1.getDuplicatedFilesCount());
            }
        }
    }

    public class FileSizeComparator implements Comparator<DuplicateSet> {
        @Override
        public int compare(DuplicateSet ds1, DuplicateSet ds2) {
            if (context.isSortAscending()) {
                return Long.compare(ds1.getDuplicatedFileSize(), ds2.getDuplicatedFileSize());
            } else {
                return Long.compare(ds2.getDuplicatedFileSize(), ds1.getDuplicatedFileSize());
            }
        }
    }
}
