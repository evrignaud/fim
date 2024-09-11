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

package org.fim.tooling;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.fim.model.Context;
import org.fim.model.HashMode;
import org.fim.util.DosFilePermissions;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static java.nio.file.StandardOpenOption.CREATE;
import static org.fim.model.HashMode.dontHash;
import static org.fim.model.HashMode.hashAll;

public class RepositoryTool {
    public static final int FILE_SIZE = 10 * 1_024 * 1_024;

    private final Path rootDir;
    private int fileCount;
    private final Context context;

    public RepositoryTool(TestInfo testInfo) throws IOException {
        this(testInfo, hashAll);
    }

    public RepositoryTool(TestInfo testInfo, HashMode hashMode) throws IOException {
        String testClassName = testInfo.getTestClass().get().getSimpleName();
        String testName = testInfo.getTestMethod().get().getName();
        String dirName = TestConstants.BUILD_TEST_OUTPUTS + "/" + testClassName + "-" + testName + "-" + hashMode;
        rootDir = Paths.get(dirName);
        FileUtils.deleteDirectory(rootDir.toFile());
        Files.createDirectories(rootDir);

        this.fileCount = 1;

        this.context = createContext(hashMode, true);
    }

    public void useFixedThreadCount() {
        if (context.getHashMode() == dontHash) {
            context.setThreadCount(1);
        } else {
            context.setThreadCount(Runtime.getRuntime().availableProcessors() / 2);
        }
        context.setDynamicScaling(false);
    }

    public Path getRootDir() {
        return rootDir;
    }

    public Context getContext() {
        return context;
    }

    public Context createContext(HashMode hashMode, boolean verbose) {
        Context ctx = new Context();
        ctx.setHashMode(hashMode);
        ctx.setAlwaysYes(true);
        ctx.setCurrentDirectory(rootDir);
        ctx.setRepositoryRootDir(rootDir);
        ctx.setVerbose(verbose);
        ctx.setComment("Using hash mode " + hashMode);
        return ctx;
    }

    public Context createInvokedFromSubDirContext(HashMode hashMode, String subDirectory, boolean verbose) {
        Context ctx = createContext(hashMode, verbose);
        ctx.setCurrentDirectory(ctx.getCurrentDirectory().resolve(subDirectory));
        ctx.setInvokedFromSubDirectory(true);
        return ctx;
    }

    public void createASetOfFiles(int count) throws IOException {
        for (int index = 1; index <= count; index++) {
            createOneFile();
        }
    }

    public void createOneFile() throws IOException {
        createFile("file" + String.format("%02d", fileCount));
    }

    public void touchCreationTime(String fileName) throws IOException {
        Path file = rootDir.resolve(fileName);
        long timeStamp = Math.max(System.currentTimeMillis(), getCreationTime(file).toMillis());
        timeStamp += 1_000;
        setCreationTime(file, FileTime.fromMillis(timeStamp));
    }

    public void touchLastModified(String fileName) throws IOException {
        Path file = rootDir.resolve(fileName);
        long timeStamp = Math.max(System.currentTimeMillis(), Files.getLastModifiedTime(file).toMillis());
        timeStamp += 1_000;
        Files.setLastModifiedTime(file, FileTime.fromMillis(timeStamp));
    }

    public void createFimIgnore(Path directory, String content) throws IOException {
        Path file = directory.resolve(".fimignore");
        if (Files.exists(file)) {
            Files.delete(file);
        }
        Files.write(file, content.getBytes(), CREATE);
    }

    public void createFile(String fileName) throws IOException {
        Path file = rootDir.resolve(fileName);
        createFile(file);
    }

    public void createFile(Path file) throws IOException {
        setFileContent(file, "File content " + String.format("%02d", fileCount));
        fileCount++;
    }

    public void createFile(Path file, int fileSize) throws IOException {
        setFileContent(file, "File content " + String.format("%02d", fileCount), fileSize);
        fileCount++;
    }

    public void setFileContent(String fileName, String content) throws IOException {
        Path file = rootDir.resolve(fileName);
        setFileContent(file, content);
    }

    public void setFileContent(Path file, String content) throws IOException {
        int fileSize = FILE_SIZE + (301_457 * fileCount);
        setFileContent(file, content, fileSize);
    }

    public void setFileContent(Path file, String content, int fileSize) throws IOException {
        if (Files.exists(file)) {
            Files.delete(file);
        }

        // Creates a big content based on the provided content
        StringBuilder sb = new StringBuilder(fileSize);
        int index = 0;
        while (sb.length() < fileSize) {
            index++;
            sb.append("b_").append(index).append(": ").append(content).append('\n');
        }

        Files.write(file, sb.toString().getBytes(), CREATE);
    }

    public void appendFileContent(Path file, String content) throws IOException {
        Files.write(file, content.getBytes(), StandardOpenOption.APPEND);
    }

    public void setReadOnly(Path rootDir) throws IOException {
        setPermissions(rootDir.toAbsolutePath().toString(), "r-xr-xr-x", "R");
    }

    public void setReadWrite(Path rootDir) throws IOException {
        setPermissions(rootDir.toAbsolutePath().toString(), "rwxrwxrwx", "");
    }

    public void setPermissions(String fileName, String posixPermissions, String dosPermissions) throws IOException {
        Path file = rootDir.resolve(fileName);
        if (SystemUtils.IS_OS_WINDOWS) {
            DosFilePermissions.setPermissions(context, file, dosPermissions);
        } else {
            Set<PosixFilePermission> permissionSet = PosixFilePermissions.fromString(posixPermissions);
            Files.getFileAttributeView(file, PosixFileAttributeView.class).setPermissions(permissionSet);
        }
    }

    private FileTime getCreationTime(Path file) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
        return attributes.creationTime();
    }

    private void setCreationTime(Path file, FileTime creationTime) throws IOException {
        Files.getFileAttributeView(file, BasicFileAttributeView.class).setTimes(null, null, creationTime);
    }
}
