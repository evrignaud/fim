/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2026 Etienne Vrignaud
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

import org.apache.commons.lang3.SystemUtils;
import org.fim.model.Context;
import org.fim.model.FileState;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;

public class FileUtil {
    private FileUtil() {
        // Utility class, no instantiation
    }

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.#");
    private static final Object DELETE_LOCK = new Object();
    private static final String WRITE_PROBE_PREFIX = ".fim-write-probe-";

    /**
     * Whether Fim can create files in the given directory.
     * <p>
     * {@link Files#isWritable(Path)} answers from the permissions the filesystem reports, and on a
     * network share those do not necessarily describe the access the server grants. A CIFS/SMB mount, a
     * NAS in practice, routinely presents ownership and mode bits that belong to the mount options
     * rather than to the account being used, and the JVM then reports a perfectly writable directory as
     * read-only. Fim refused to create its repository on such a share for that reason alone.
     * <p>
     * The reported permissions are still consulted first, since that settles the common case without
     * touching the disk. Only when they say no does this actually try to create a file, which is the
     * one answer that cannot be wrong. A genuinely read-only directory fails that attempt too, so real
     * permission problems are still reported.
     */
    public static boolean isWritableDirectory(Path directory) {
        if (Files.isWritable(directory)) {
            return true;
        }

        if (!Files.isDirectory(directory)) {
            return false;
        }

        Path probe = null;
        try {
            probe = Files.createTempFile(directory, WRITE_PROBE_PREFIX, ".tmp");
            return true;
        } catch (IOException | RuntimeException ex) {
            return false;
        } finally {
            deleteProbe(probe);
        }
    }

    /**
     * Whether Fim can write the given file, applying the same reasoning as
     * {@link #isWritableDirectory(Path)}.
     * <p>
     * An existing file is probed by opening it for writing and closing it again, which leaves the
     * content alone: no truncation and nothing written. A file that does not exist yet cannot be probed,
     * so what matters there is whether it can be created, which is a property of its directory. That
     * case is reached with a repository whose settings file has not been written yet, where testing the
     * missing file directly would always have reported failure.
     */
    public static boolean isWritableFile(Path file) {
        if (Files.isWritable(file)) {
            return true;
        }

        if (Files.exists(file)) {
            try (var channel = Files.newByteChannel(file, java.nio.file.StandardOpenOption.WRITE)) {
                return channel.isOpen();
            } catch (IOException | RuntimeException ex) {
                return false;
            }
        }

        Path parent = file.getParent();
        return parent != null && isWritableDirectory(parent);
    }

    private static void deleteProbe(Path probe) {
        if (probe == null) {
            return;
        }
        try {
            Files.deleteIfExists(probe);
        } catch (IOException ex) {
            Logger.rawDebug(String.format("Not able to remove the write probe file '%s'", probe));
        }
    }

    public static String getNormalizedFileName(Path file) {
        String normalizedFileName = file.toAbsolutePath().normalize().toString();
        if (File.separatorChar != '/') {
            normalizedFileName = normalizedFileName.replace(File.separatorChar, '/');
        }
        return normalizedFileName;
    }

    public static String getRelativeFileName(String directory, String fileName) {
        String relativeFileName = fileName;
        if (relativeFileName.startsWith(directory)) {
            relativeFileName = relativeFileName.substring(directory.length());
        }

        if (relativeFileName.startsWith("/")) {
            relativeFileName = relativeFileName.substring(1);
        }
        return relativeFileName;
    }

    public static boolean removeFile(Context context, Path rootDir, FileState fileState) {
        // Files.delete() not ThreadSafe on Windows and macOS.
        // Based on https://github.com/apache/flink/blob/master/flink-core/src/main/java/org/apache/flink/util/FileUtils.java#L395
        if (SystemUtils.IS_OS_WINDOWS || SystemUtils.IS_OS_MAC_OSX) {
            synchronized (DELETE_LOCK) {
                for (int attempt = 1; attempt <= 10; attempt++) {
                    if (removeFileInternal(context, rootDir, fileState)) {
                        return true;
                    }

                    // Got sometimes java.nio.file.AccessDeniedException during file delete.
                    // Windows has some unusual file locking behaviors.
                    // Deleting files shortly after using them is rarely possible to do quickly or expediently using Java on Windows,
                    // as the file locking will get in your way.

                    // briefly wait and fall through the loop
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        // restore the interruption flag and error out of the method
                        Thread.currentThread().interrupt();
                        throw new FileOperationInterruptedException("File deletion operation interrupted");
                    }
                }
                return false;
            }
        }

        return removeFileInternal(context, rootDir, fileState);
    }

    private static boolean removeFileInternal(Context context, Path rootDir, FileState fileState) {
        try {
            Path file = rootDir.resolve(fileState.getFileName());
            Files.delete(file);
            return true;
        } catch (IOException ex) {
            Logger.error("Error deleting file", ex, context.isDisplayStackTrace());
        }
        return false;
    }

    /**
     * Call commons.io.FileUtils.byteCountToDisplaySize() with negative number support.
     */
    public static String byteCountToDisplaySize(final long size) {
        long localSize = size;
        boolean isNegative = false;
        if (size < 0) {
            localSize = -size;
            isNegative = true;
        }

        String displaySize = humanReadableByteCount(localSize, true);
        if (isNegative) {
            displaySize = "-" + displaySize;
        }
        return displaySize;
    }

    /**
     * Original code comes from:
     * http://programming.guide/java/formatting-byte-size-to-human-readable-format.html
     */
    private static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) {
            return bytes + " bytes";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        return DECIMAL_FORMAT.format(bytes / Math.pow(unit, exp)) + " " + "KMGTPE".charAt(exp - 1) + (si ? "B" : "bit");
    }
}
