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
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.text.DecimalFormat;

public class FileUtil {
    private FileUtil() {
        // Utility class, no instantiation
    }

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.#");
    private static final Object DELETE_LOCK = new Object();
    private static final String WRITE_PROBE_PREFIX = ".fim-write-probe-";
    private static final int DELETE_ATTEMPTS = 10;

    /**
     * The encoding the JVM maps file names with. It is derived from the locale of the process and cannot
     * be overridden with a system property, so it is read rather than set.
     */
    private static final String NATIVE_ENCODING_PROPERTY = "sun.jnu.encoding";

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
        Path file = rootDir.resolve(fileState.getFileName());

        IOException failure;
        // Files.delete() not ThreadSafe on Windows and macOS.
        // Based on https://github.com/apache/flink/blob/master/flink-core/src/main/java/org/apache/flink/util/FileUtils.java#L395
        if (SystemUtils.IS_OS_WINDOWS || SystemUtils.IS_OS_MAC_OSX) {
            synchronized (DELETE_LOCK) {
                failure = removeFileWithRetries(file);
            }
        } else {
            failure = tryToRemoveFile(file);
        }

        if (failure == null) {
            return true;
        }

        // Reported once, after every attempt, rather than once per attempt.
        reportDeletionFailure(context, file, failure);
        return false;
    }

    private static IOException removeFileWithRetries(Path file) {
        IOException failure = null;
        for (int attempt = 1; attempt <= DELETE_ATTEMPTS; attempt++) {
            failure = tryToRemoveFile(file);
            if (failure == null) {
                return null;
            }

            // Waiting cannot make a file that is not there appear, so only contention is worth retrying.
            if (failure instanceof NoSuchFileException) {
                return failure;
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
        return failure;
    }

    private static IOException tryToRemoveFile(Path file) {
        try {
            Files.delete(file);
            return null;
        } catch (IOException ex) {
            return ex;
        }
    }

    /**
     * Name the file that could not be deleted, and say why.
     * <p>
     * This used to report only "Error deleting file", which left no way to tell which file failed or
     * what stopped it.
     * <p>
     * A file name that the JVM cannot encode is called out explicitly, because nothing about it is
     * visible in the usual output: the name is read from disk through {@code sun.jnu.encoding}, and when
     * that charset cannot represent every character the name comes back altered, so it no longer refers
     * to the file it came from. Deleting it then fails however many times it is retried. That charset
     * follows the locale of the process and cannot be changed with a system property, which is why the
     * advice is about the locale.
     */
    private static void reportDeletionFailure(Context context, Path file, IOException failure) {
        String message = String.format("Error deleting file '%s'", file);
        if (!isEncodableByTheJvm(file.toString())) {
            message += String.format(", whose name cannot be represented using '%s', the encoding this JVM maps file names with." +
                                     " Run Fim with a UTF-8 locale, for example LANG=C.UTF-8, so that this name can be resolved",
                    System.getProperty(NATIVE_ENCODING_PROPERTY));
        }
        Logger.error(message, failure, context.isDisplayStackTrace());
    }

    /**
     * Whether the JVM is able to hand this name to the operating system unaltered. A name carrying the
     * Unicode replacement character has already lost information while being read.
     */
    private static boolean isEncodableByTheJvm(String fileName) {
        if (fileName.indexOf('\uFFFD') >= 0) {
            return false;
        }

        String encoding = System.getProperty(NATIVE_ENCODING_PROPERTY);
        if (encoding == null) {
            return true;
        }

        try {
            return Charset.forName(encoding).newEncoder().canEncode(fileName);
        } catch (IllegalArgumentException | UnsupportedOperationException ex) {
            // Unknown or non-encoding charset: nothing can be concluded, so do not blame the encoding.
            return true;
        }
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
