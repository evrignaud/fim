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

import org.apache.commons.lang3.SystemUtils;
import org.fim.internal.StateManager;
import org.fim.model.Context;
import org.fim.model.FileAttribute;
import org.fim.model.FileState;
import org.fim.model.State;
import org.fim.util.DosFilePermissions;
import org.fim.util.Logger;
import org.fim.util.SELinux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.atteo.evo.inflector.English.plural;
import static org.fim.util.FormatUtil.formatDate;

public class ResetFileAttributesCommand extends AbstractCommand {
    @Override
    public String getCmdName() {
        return "reset-file-attrs";
    }

    @Override
    public String getShortCmdName() {
        return "rfa";
    }

    @Override
    public String getDescription() {
        return "Reset the files attributes like they were stored in the last committed State";
    }

    @Override
    public Object execute(Context context) throws Exception {
        StateManager manager = new StateManager(context);
        State lastState = manager.loadLastState();
        int fileResetCount = 0;

        Logger.out.printf(String.format("You are going to reset files attributes based on the last committed State done %s%n",
                formatDate(lastState.getTimestamp())));
        if (confirmAction(context, "continue")) {
            if (lastState.getComment().length() > 0) {
                Logger.out.println("Comment: " + lastState.getComment());
            }
            Logger.newLine();

            if (context.isInvokedFromSubDirectory()) {
                lastState = lastState.filterDirectory(context.getRepositoryRootDir(), context.getCurrentDirectory(), true);
            }

            for (FileState fileState : lastState.getFileStates()) {
                Path file = context.getRepositoryRootDir().resolve(fileState.getFileName());
                if (Files.exists(file)) {
                    fileResetCount += resetFileAttributes(context, fileState, file);
                }
            }

            if (fileResetCount == 0) {
                Logger.info("No file attributes have been reset");
            } else {
                Logger.newLine();
                Logger.info(String.format("The attributes of %d %s have been reset", fileResetCount, plural("file", fileResetCount)));
            }
        }
        return fileResetCount;
    }

    private int resetFileAttributes(Context context, FileState fileState, Path file) throws IOException {
        boolean attributesModified = false;

        try {
            BasicFileAttributes attributes;

            if (SystemUtils.IS_OS_WINDOWS) {
                DosFileAttributes dosFileAttributes = Files.readAttributes(file, DosFileAttributes.class);
                attributes = dosFileAttributes;

                attributesModified = resetDosPermissions(context, file, fileState, dosFileAttributes) || attributesModified;
            } else {
                PosixFileAttributes posixFileAttributes = Files.readAttributes(file, PosixFileAttributes.class);
                attributes = posixFileAttributes;

                attributesModified = resetPosixPermissions(file, fileState, posixFileAttributes) || attributesModified;
            }

            attributesModified = resetCreationTime(file, fileState, attributes) || attributesModified;
            attributesModified = resetLastModified(file, fileState, attributes) || attributesModified;
            attributesModified = resetSELinux(context, file, fileState) || attributesModified;
        } catch (Exception ex) {
            Logger.error(ex.getMessage());
        }
        return attributesModified ? 1 : 0;
    }

    private boolean resetDosPermissions(Context context, Path file, FileState fileState, DosFileAttributes dosFileAttributes) {
        String permissions = DosFilePermissions.toString(dosFileAttributes);
        String previousPermissions = getAttribute(fileState, FileAttribute.DosFilePermissions);
        if (previousPermissions != null && !Objects.equals(permissions, previousPermissions)) {
            DosFilePermissions.setPermissions(context, file, previousPermissions);
            Logger.out.printf("Set permissions: %s \t%s -> %s%n", fileState.getFileName(), permissions, previousPermissions);
            return true;
        }
        return false;
    }

    private boolean resetPosixPermissions(Path file, FileState fileState, PosixFileAttributes posixFileAttributes) throws IOException {
        String permissions = PosixFilePermissions.toString(posixFileAttributes.permissions());
        String previousPermissions = getAttribute(fileState, FileAttribute.PosixFilePermissions);
        if (previousPermissions != null && !Objects.equals(permissions, previousPermissions)) {
            Set<PosixFilePermission> permissionSet = PosixFilePermissions.fromString(previousPermissions);
            Files.getFileAttributeView(file, PosixFileAttributeView.class).setPermissions(permissionSet);
            Logger.out.printf("Set permissions: %s \t%s -> %s%n", fileState.getFileName(), permissions, previousPermissions);
            return true;
        }
        return false;
    }

    private boolean resetCreationTime(Path file, FileState fileState, BasicFileAttributes attributes) throws IOException {
        long creationTime = attributes.creationTime().toMillis();
        long previousCreationTime = fileState.getFileTime().getCreationTime();
        if (creationTime != previousCreationTime) {
            setCreationTime(file, FileTime.fromMillis(previousCreationTime));
            Logger.out.printf("Set creation Time: %s \t%s -> %s%n", fileState.getFileName(), formatDate(creationTime),
                    formatDate(previousCreationTime));
            return true;
        }
        return false;
    }

    private boolean resetLastModified(Path file, FileState fileState, BasicFileAttributes attributes) throws IOException {
        long lastModified = attributes.lastModifiedTime().toMillis();
        long previousLastModified = fileState.getFileTime().getLastModified();
        if (lastModified != previousLastModified) {
            Files.setLastModifiedTime(file, FileTime.fromMillis(previousLastModified));
            Logger.out.printf("Set last modified: %s \t%s -> %s%n", fileState.getFileName(), formatDate(lastModified),
                    formatDate(previousLastModified));
            return true;
        }
        return false;
    }

    private boolean resetSELinux(Context context, Path file, FileState fileState) {
        if (SELinux.ENABLED) {
            String label = SELinux.getLabel(context, file);
            String previousLabel = getAttribute(fileState, FileAttribute.SELinuxLabel);
            if (previousLabel != null && !Objects.equals(label, previousLabel)) {
                SELinux.setLabel(context, file, previousLabel);
                Logger.out.printf("Set SELinux: %s \t%s -> %s%n", fileState.getFileName(), label, previousLabel);
                return true;
            }
        }
        return false;
    }

    private String getAttribute(FileState fileState, FileAttribute fileAttribute) {
        Map<String, String> fileAttributes = fileState.getFileAttributes();
        return fileAttributes != null ? fileAttributes.get(fileAttribute.name()) : null;
    }

    private void setCreationTime(Path file, FileTime creationTime) throws IOException {
        Files.getFileAttributeView(file, BasicFileAttributeView.class).setTimes(null, null, creationTime);
    }
}
