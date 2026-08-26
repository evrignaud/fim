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
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.FileTime;
import org.fim.tooling.TestConstants;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

public class FileUtilTest {
    @Test
    public void canNormalizeFileNameWithBackslashes() {
        String normalizedFileName = FileUtil.getNormalizedFileName(Paths.get("C:\\dir1\\dir2"));
        assertThat(normalizedFileName).isEqualTo("C:/dir1/dir2");
    }

    @Test
    public void canNormalizeFileNameWithForwardSlashes() {
        String normalizedFileName = FileUtil.getNormalizedFileName(Paths.get("/dir1/dir2"));
        if (SystemUtils.IS_OS_WINDOWS) {
            assertThat(normalizedFileName).isEqualTo("C:/dir1/dir2");
        } else {
            assertThat(normalizedFileName).isEqualTo("/dir1/dir2");
        }
    }

    @Test
    public void canGetRelativeFileNameWhenFileIsInDirectory() {
        String relativeFileName = FileUtil.getRelativeFileName("/dir1/dir2/dir3", "/dir1/dir2/dir3/dir4/file1");
        assertThat(relativeFileName).isEqualTo("dir4/file1");
    }

    @Test
    public void canGetRelativeFileNameWhenFileIsNotInDirectory() {
        String relativeFileName = FileUtil.getRelativeFileName("/dir5/dir6/dir7", "/dir1/dir2/dir3/dir4/file1");
        assertThat(relativeFileName).isEqualTo("dir1/dir2/dir3/dir4/file1");
    }

    @Test
    public void canGetRelativeFileNameWhenFileNameIsRelative() {
        String relativeFileName = FileUtil.getRelativeFileName("/dir1/dir2/dir3", "dir4/file1");
        assertThat(relativeFileName).isEqualTo("dir4/file1");
    }

    @Test
    public void canRemoveFileSuccessfully() throws IOException {
        Context context = new Context();
        Path rootDir = Paths.get(TestConstants.BUILD_TEST_OUTPUTS + "/FileUtilTest-canRemoveFileSuccessfully");
        org.apache.commons.io.FileUtils.deleteDirectory(rootDir.toFile());
        Files.createDirectories(rootDir);

        String fileName = "fileToRemove.txt";
        Path file = Paths.get(rootDir.toString(), fileName);
        file.toFile().createNewFile();

        FileState fileState = new FileState(fileName, 0, new FileTime(), new FileHash(), null);
        TimeUtil.sleepSafely(10);

        boolean result = FileUtil.removeFile(context, rootDir, fileState);
        assertThat(result).isTrue();
        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    public void cannotRemoveFileDueToAccessDenied() throws IOException {
        Context context = new Context();
        Path rootDir = Paths.get(TestConstants.BUILD_TEST_OUTPUTS + "/FileUtilTest-canRemoveFileSuccessfully");
        org.apache.commons.io.FileUtils.deleteDirectory(rootDir.toFile());
        Files.createDirectories(rootDir);

        String fileName = "fileToRemove.txt";
        Path file = Paths.get(rootDir.toString(), fileName);
        file.toFile().createNewFile();

        FileState fileState = new FileState(fileName + "dummy", 0, new FileTime(), new FileHash(), null);
        TimeUtil.sleepSafely(10);

        boolean result = FileUtil.removeFile(context, rootDir, fileState);
        assertThat(result).isFalse();
    }

    /**
     * Tests for the writability checks behind
     * <a href="https://github.com/evrignaud/fim/issues/25">issue #25</a>, where creating a repository on
     * a CIFS share failed because the reported permissions did not match the access the server granted.
     */
    @Test
    public void writableDirectoryIsReportedAsWritable() throws IOException {
        Path dir = freshDir("writableDirectory");

        assertThat(FileUtil.isWritableDirectory(dir)).isTrue();
    }

    @Test
    public void writeProbeLeavesNoFileBehind() throws IOException {
        Path dir = freshDir("writeProbeCleanup");

        assertThat(FileUtil.isWritableDirectory(dir)).isTrue();

        try (var entries = Files.list(dir)) {
            assertThat(entries.toList()).isEmpty();
        }
    }

    @Test
    public void missingDirectoryIsNotWritable() throws IOException {
        Path dir = freshDir("missingDirectory").resolve("does-not-exist");

        assertThat(FileUtil.isWritableDirectory(dir)).isFalse();
    }

    @Test
    public void existingWritableFileIsReportedAsWritable() throws IOException {
        Path file = freshDir("writableFile").resolve("settings.json");
        Files.writeString(file, "{}");

        assertThat(FileUtil.isWritableFile(file)).isTrue();
        assertThat(Files.readString(file)).isEqualTo("{}");
    }

    /**
     * A file that has not been created yet is writable when its directory is, which is what matters for
     * the settings file of a repository that has not written one. Probing the missing file itself would
     * always have said no.
     */
    @Test
    public void missingFileIsWritableWhenItsDirectoryIs() throws IOException {
        Path file = freshDir("missingFile").resolve("settings.json");

        assertThat(Files.exists(file)).isFalse();
        assertThat(FileUtil.isWritableFile(file)).isTrue();
    }

    @Test
    public void fileInMissingDirectoryIsNotWritable() throws IOException {
        Path file = freshDir("fileInMissingDir").resolve("nope").resolve("settings.json");

        assertThat(FileUtil.isWritableFile(file)).isFalse();
    }

    /**
     * A directory that genuinely denies writing is still reported as read-only, so real permission
     * problems keep being caught. Windows does not deny directory creation through the read-only
     * attribute, so this only applies to POSIX systems.
     */
    @Test
    public void genuinelyReadOnlyDirectoryIsNotWritable() throws IOException {
        if (SystemUtils.IS_OS_WINDOWS) {
            return;
        }

        Path dir = freshDir("readOnlyDirectory");
        Files.setPosixFilePermissions(dir, java.nio.file.attribute.PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            assertThat(FileUtil.isWritableDirectory(dir)).isFalse();
        } finally {
            Files.setPosixFilePermissions(dir, java.nio.file.attribute.PosixFilePermissions.fromString("rwxrwxrwx"));
        }
    }

    private Path freshDir(String name) throws IOException {
        Path dir = Paths.get(TestConstants.BUILD_TEST_OUTPUTS + "/FileUtilTest-" + name);
        org.apache.commons.io.FileUtils.deleteDirectory(dir.toFile());
        Files.createDirectories(dir);
        return dir;
    }

    @Test
    public void canConvertZeroByteCountToDisplaySize() {
        String displaySize = FileUtil.byteCountToDisplaySize(0);
        assertThat(displaySize).isEqualTo("0 bytes");
    }

    @Test
    public void canConvertPositiveByteCountToDisplaySize() {
        String displaySize = FileUtil.byteCountToDisplaySize(5 * 1000);
        assertThat(displaySize).isEqualTo("5 KB");
    }

    @Test
    public void canConvertMegabytesToDisplaySize() {
        String displaySize = FileUtil.byteCountToDisplaySize(30 * 1000 * 1000);
        assertThat(displaySize).isEqualTo("30 MB");
    }

    @Test
    public void canConvertGigabytesToDisplaySize() {
        String displaySize = FileUtil.byteCountToDisplaySize(2 * 1000 * 1000 * 1000);
        assertThat(displaySize).isEqualTo("2 GB");
    }
}
