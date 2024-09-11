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

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.fim.model.Context;
import org.fim.model.DuplicateResult;
import org.fim.model.DuplicateSet;
import org.fim.model.FileState;
import org.fim.model.output.DuplicatedFile;
import org.fim.model.output.DuplicatedFiles;
import org.fim.util.JsonIO;
import org.fim.util.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DuplicateOutputGenerator {
    private final Context context;

    public DuplicateOutputGenerator(Context context) {
        this.context = context;
    }

    public void generate(DuplicateResult duplicateResult) {
        List<DuplicatedFiles> duplicates = generateDuplicatedFiles(duplicateResult);
        switch (context.getOutputType()) {
            case csv -> generateCSV(duplicates);
            case json -> generateJson(duplicates);
            case human -> generateHuman(duplicates);
        }
    }

    private void generateHuman(List<DuplicatedFiles> duplicates) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private List<DuplicatedFiles> generateDuplicatedFiles(DuplicateResult duplicateResult) {
        List<DuplicatedFiles> duplicateList = new ArrayList<>();
        for (DuplicateSet duplicateSet : duplicateResult.getDuplicateSets()) {
            DuplicatedFiles duplicatedFiles = new DuplicatedFiles();
            duplicatedFiles.setWastedSpace(duplicateSet.getWastedSpace());
            for (FileState fileState : duplicateSet.getDuplicatedFiles()) {
                DuplicatedFile file = new DuplicatedFile();
                file.setName(fileState.getFileName());
                file.setLength(fileState.getFileLength());
                file.setPath(getPath(fileState.getFileName()));
                file.setType(getExtension(fileState.getFileName()));
                duplicatedFiles.getFileList().add(file);
            }
            duplicateList.add(duplicatedFiles);
        }
        return duplicateList;
    }

    private String getPath(String fileName) {
        int index = fileName.lastIndexOf("/");
        if (index == -1) {
            return "";
        }
        return fileName.substring(0, index);
    }

    private String getExtension(String fileName) {
        int index = fileName.lastIndexOf(".");
        if (index == -1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase();
    }

    private void generateCSV(List<DuplicatedFiles> duplicates) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader("SetIndex", "FileIndex", "WastedSpace", "FilePath", "FileName", "FileLength", "FileType")
                .build();
        try (CSVPrinter csvPrinter = new CSVPrinter(Logger.out, format)) {
            int setIndex = 0;
            for (DuplicatedFiles files : duplicates) {
                setIndex++;
                int fileIndex = 0;
                for (DuplicatedFile file : files.getFileList()) {
                    fileIndex++;
                    csvPrinter.printRecord(setIndex, fileIndex, files.getWastedSpace(), file.getPath(), file.getName(), file.getLength(),
                            file.getType());
                }
            }
            csvPrinter.flush();
        } catch (IOException ex) {
            Logger.error("Error displaying duplicates in CSV format", ex, context.isDisplayStackTrace());
        }
    }

    private void generateJson(List<DuplicatedFiles> duplicates) {
        JsonIO jsonIO = new JsonIO();
        try {
            jsonIO.getObjectWriter().writeValue(Logger.out, duplicates);
            Logger.out.println();
        } catch (IOException ex) {
            Logger.error("Error displaying duplicates in JSON format", ex, context.isDisplayStackTrace());
        }
    }
}
