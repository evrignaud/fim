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

import org.fim.model.Context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;

public class DosFilePermissions {
    public static String toString(DosFileAttributes dosFileAttributes) {
        StringBuilder builder = new StringBuilder();
        if (dosFileAttributes.isArchive()) {
            builder.append('A');
        }
        if (dosFileAttributes.isHidden()) {
            builder.append('H');
        }
        if (dosFileAttributes.isReadOnly()) {
            builder.append('R');
        }
        if (dosFileAttributes.isSystem()) {
            builder.append('S');
        }
        return builder.toString();
    }

    public static void setPermissions(Context context, Path file, String permissions) {
        DosFileAttributeView fileAttributeView = Files.getFileAttributeView(file, DosFileAttributeView.class);
        try {
            fileAttributeView.setArchive(permissions.contains("A"));
            fileAttributeView.setHidden(permissions.contains("H"));
            fileAttributeView.setReadOnly(permissions.contains("R"));
            fileAttributeView.setSystem(permissions.contains("S"));
        } catch (IOException ex) {
            Logger.error("Error setting permissions for '" + file + "'", ex, context.isDisplayStackTrace());
        }
    }
}
