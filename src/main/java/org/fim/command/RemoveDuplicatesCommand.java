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

package org.fim.command;

import org.fim.command.exception.BadFimUsageException;
import org.fim.command.exception.DontWantToContinueException;
import org.fim.internal.StateGenerator;
import org.fim.internal.StateManager;
import org.fim.model.Command;
import org.fim.model.Context;
import org.fim.model.DuplicateResult;
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.HashMode;
import org.fim.model.State;
import org.fim.util.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.atteo.evo.inflector.English.plural;
import static org.fim.model.HashMode.hashMediumBlock;
import static org.fim.util.FileUtil.removeFile;
import static org.fim.util.HashModeUtil.hashModeToString;

public class RemoveDuplicatesCommand extends AbstractCommand {
    @Override
    public String getCmdName() {
        return "remove-duplicates";
    }

    @Override
    public String getShortCmdName() {
        return "rdup";
    }

    @Override
    public String getDescription() {
        return """
                Remove duplicates found by the 'fdup' command.
                                                If you specify the '-M' option it removes duplicates based on a master repository""";
    }

    @Override
    public FimReposConstraint getFimReposConstraint() {
        return FimReposConstraint.DONT_CARE;
    }

    @Override
    public Object execute(Context context) throws Exception {
        if (context.getMasterFimRepositoryDir() == null) {
            context.setRemoveDuplicates(true);
            Command command = new FindDuplicatesCommand();
            DuplicateResult result = (DuplicateResult) command.execute(context);
            return result.getFilesRemoved();
        }

        checkHashMode(context, Option.ALLOW_COMPATIBLE);

        fileContentHashingMandatory(context);

        if ((context.getHashMode() == HashMode.hashSmallBlock) || (context.getHashMode() == hashMediumBlock)) {
            Logger.out.printf("You are going to detect duplicates and remove them using '%s' mode.%n", hashModeToString(context.getHashMode()));
            if (!confirmAction(context, "continue")) {
                throw new DontWantToContinueException();
            }
        }

        Path masterFimRepository = Paths.get(context.getMasterFimRepositoryDir());

        Path normalizedMasterFimRepository = masterFimRepository.toAbsolutePath().normalize();
        Path normalizedCurrentDir = context.getCurrentDirectory().toAbsolutePath().normalize();

        if (normalizedMasterFimRepository.equals(normalizedCurrentDir)) {
            Logger.error("Cannot remove duplicates into the master directory");
            throw new BadFimUsageException();
        }

        if (normalizedCurrentDir.startsWith(normalizedMasterFimRepository)) {
            Logger.error("Cannot remove duplicates into a sub-directory of the master directory");
            throw new BadFimUsageException();
        }

        Path masterDotFimDir = masterFimRepository.resolve(Context.DOT_FIM_DIR);
        if (!Files.exists(masterDotFimDir)) {
            Logger.error(String.format("Directory %s is not a Fim repository", context.getMasterFimRepositoryDir()));
            throw new BadFimUsageException();
        }
        context.setRepositoryRootDir(masterFimRepository);

        Logger.info(String.format("Searching for duplicate files using the %s directory as master", context.getMasterFimRepositoryDir()));
        Logger.newLine();

        State masterState = new StateManager(context).loadLastState();
        Map<FileHash, FileState> masterFilesHash = buildFileHashMap(masterState);

        long duplicatedFilesCount = 0;
        long totalFilesRemoved = 0;
        State localState = new StateGenerator(context).generateState("", context.getCurrentDirectory(), context.getCurrentDirectory());
        for (FileState localFileState : localState.getFileStates()) {
            if (localFileState.getFileLength() == 0) {
                continue;
            }

            FileState masterFileState = masterFilesHash.get(localFileState.getFileHash());
            if (masterFileState != null) {
                duplicatedFilesCount++;
                Logger.out.printf("'%s' is a duplicate of '%s/%s'%n", localFileState.getFileName(),
                        context.getMasterFimRepositoryDir(), masterFileState.getFileName());
                if (confirmAction(context, "remove it")) {
                    if (removeFile(context, normalizedCurrentDir, localFileState)) {
                        Logger.out.printf("  '%s' removed%n", localFileState.getFileName());
                        totalFilesRemoved++;
                    }
                }
            }
        }

        if (totalFilesRemoved == 0) {
            if (duplicatedFilesCount == 0) {
                Logger.out.println("No duplicate file found");
            } else {
                Logger.out.printf("Found %d duplicate %s. No files removed%n", duplicatedFilesCount, pluralForLong("file", duplicatedFilesCount));
            }
        } else {
            Logger.newLine();
            Logger.out.printf("%d duplicate %s found. %d duplicate %s removed%n",
                    duplicatedFilesCount, pluralForLong("file", duplicatedFilesCount),
                    totalFilesRemoved, pluralForLong("file", totalFilesRemoved));
        }
        return totalFilesRemoved;
    }

    private Map<FileHash, FileState> buildFileHashMap(State state) {
        Map<FileHash, FileState> filesHashMap = new HashMap<>();
        for (FileState fileState : state.getFileStates()) {
            filesHashMap.put(fileState.getFileHash(), fileState);
        }
        return filesHashMap;
    }

    private String pluralForLong(String word, long count) {
        return plural(word, count > 1 ? 2 : 1);
    }
}
