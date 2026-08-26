/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2025 Etienne Vrignaud
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

package org.fim.internal.hash;

import org.fim.model.Context;
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.FileTime;
import org.fim.model.HashMode;
import org.fim.tooling.RepositoryTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.tooling.TestConstants.NO_HASH;

/**
 * Validates: Requirements 10.1, 10.2, 10.3, 10.4, 10.5
 * Tests for FileReHasher: re-hashing behavior, path resolution, error handling, and termination signaling.
 */
public class FileReHasherTest {

    private Context context;
    private Path rootDir;
    private TestInfo testInfo;

    @BeforeEach
    void init(TestInfo testInfo) throws IOException {
        this.testInfo = testInfo;
    }

    private void setUp(HashMode hashMode) {
        try {
            RepositoryTool tool = new RepositoryTool(testInfo, hashMode);
            rootDir = tool.getRootDir();
            context = tool.getContext();
            context.setUseDynamicScaling(false);
            context.initializeDynamicScaling();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Path createFileWithContent(String fileName, String content) throws IOException {
        Path file = rootDir.resolve(fileName);
        Files.write(file, content.getBytes());
        return file;
    }

    private FileState createFileState(String fileName, long fileLength) {
        FileHash hash = new FileHash(NO_HASH, NO_HASH, NO_HASH);
        FileTime fileTime = new FileTime(System.currentTimeMillis());
        return new FileState(fileName, fileLength, fileTime, hash, null);
    }

    /**
     * Validates: Requirements 10.2, 10.3
     * Verifies that FileReHasher reads FileState from a BlockingDeque and resolves
     * the file path relative to the repository root directory.
     */
    @Test
    void run_reHashesFileFromQueue_resolvingPathRelativeToRoot() throws Exception {
        setUp(HashMode.hashAll);

        // Create a real file in the repository root
        String fileName = "testfile.txt";
        Path file = createFileWithContent(fileName, "Hello, re-hashing world!");
        long fileLength = Files.size(file);

        // Create a FileState with the relative file name
        FileState fileState = createFileState(fileName, fileLength);

        // Put it in the queue
        BlockingDeque<FileState> queue = new LinkedBlockingDeque<>();
        queue.add(fileState);

        HashProgress hashProgress = new HashProgress(context);

        FileReHasher reHasher = new FileReHasher(context, hashProgress, queue, rootDir);
        reHasher.run();

        // After re-hashing, the FileState should have a new FileHash (not all NO_HASH)
        assertThat(fileState.getFileHash()).isNotNull();
        assertThat(fileState.getFileHash().getSmallBlockHash()).isNotEqualTo(NO_HASH);
        assertThat(fileState.getFileHash().getMediumBlockHash()).isNotEqualTo(NO_HASH);
        assertThat(fileState.getFileHash().getFullHash()).isNotEqualTo(NO_HASH);
    }

    /**
     * Validates: Requirements 10.3
     * Verifies that FileReHasher correctly resolves paths for files in subdirectories
     * relative to the repository root.
     */
    @Test
    void run_resolvesSubdirectoryPaths() throws Exception {
        setUp(HashMode.hashSmallBlock);

        // Create a subdirectory and file
        Path subDir = rootDir.resolve("subdir");
        Files.createDirectories(subDir);
        String relativeFileName = "subdir/nested_file.txt";
        Path file = rootDir.resolve(relativeFileName);
        Files.write(file, "Nested file content for re-hashing".getBytes());
        long fileLength = Files.size(file);

        FileState fileState = createFileState(relativeFileName, fileLength);

        BlockingDeque<FileState> queue = new LinkedBlockingDeque<>();
        queue.add(fileState);

        HashProgress hashProgress = new HashProgress(context);

        FileReHasher reHasher = new FileReHasher(context, hashProgress, queue, rootDir);
        reHasher.run();

        // The small block hash should be computed
        assertThat(fileState.getFileHash().getSmallBlockHash()).isNotEqualTo(NO_HASH);
    }

    /**
     * Validates: Requirements 10.4
     * Verifies that when an error occurs during re-hashing (e.g., file not found),
     * the FileReHasher logs the error and continues processing remaining files.
     */
    @Test
    void run_continuesAfterError_whenFileNotFound() throws Exception {
        setUp(HashMode.hashAll);

        // Create a valid file
        String validFileName = "valid_file.txt";
        Path validFile = createFileWithContent(validFileName, "Valid file content");
        long validFileLength = Files.size(validFile);

        // Create FileStates: one for a missing file, one for the valid file
        FileState missingFileState = createFileState("nonexistent_file.txt", 1024);
        FileState validFileState = createFileState(validFileName, validFileLength);

        BlockingDeque<FileState> queue = new LinkedBlockingDeque<>();
        queue.add(missingFileState);
        queue.add(validFileState);

        HashProgress hashProgress = new HashProgress(context);

        FileReHasher reHasher = new FileReHasher(context, hashProgress, queue, rootDir);
        reHasher.run();

        // The missing file should still have its original NO_HASH (error was caught and logged)
        assertThat(missingFileState.getFileHash().getSmallBlockHash()).isEqualTo(NO_HASH);

        // The valid file should have been re-hashed successfully
        assertThat(validFileState.getFileHash()).isNotNull();
        assertThat(validFileState.getFileHash().getSmallBlockHash()).isNotEqualTo(NO_HASH);
    }

    /**
     * Validates: Requirements 10.5
     * Verifies that when the re-hash queue is empty, the FileReHasher terminates
     * and signals HashProgress.noMoreFileToHash().
     */
    @Test
    void run_terminatesAndSignals_whenQueueEmpty() throws Exception {
        setUp(HashMode.hashAll);

        BlockingDeque<FileState> queue = new LinkedBlockingDeque<>();
        HashProgress hashProgress = new HashProgress(context);

        FileReHasher reHasher = new FileReHasher(context, hashProgress, queue, rootDir);
        reHasher.run();

        // After run() completes with empty queue, noMoreFileToHash() should have been called.
        // Verify by checking that waitAllFilesToBeHashed() returns immediately
        // (if the latch wasn't counted down, this would block forever)
        hashProgress.waitAllFilesToBeHashed();
    }

    /**
     * Validates: Requirements 10.1, 10.2
     * Verifies that FileReHasher processes multiple FileState objects from the queue.
     */
    @Test
    void run_processesMultipleFiles() throws Exception {
        setUp(HashMode.hashAll);

        // Create multiple files
        String file1Name = "file_one.txt";
        String file2Name = "file_two.txt";
        Path file1 = createFileWithContent(file1Name, "Content of file one");
        Path file2 = createFileWithContent(file2Name, "Content of file two - different");
        long file1Length = Files.size(file1);
        long file2Length = Files.size(file2);

        FileState fileState1 = createFileState(file1Name, file1Length);
        FileState fileState2 = createFileState(file2Name, file2Length);

        BlockingDeque<FileState> queue = new LinkedBlockingDeque<>();
        queue.add(fileState1);
        queue.add(fileState2);

        HashProgress hashProgress = new HashProgress(context);

        FileReHasher reHasher = new FileReHasher(context, hashProgress, queue, rootDir);
        reHasher.run();

        // Both files should have been re-hashed
        assertThat(fileState1.getFileHash().getSmallBlockHash()).isNotEqualTo(NO_HASH);
        assertThat(fileState2.getFileHash().getSmallBlockHash()).isNotEqualTo(NO_HASH);

        // Different content should produce different hashes
        assertThat(fileState1.getFileHash().getFullHash())
                .isNotEqualTo(fileState2.getFileHash().getFullHash());
    }

    /**
     * Validates: Requirements 10.1
     * Verifies that FileReHasher uses the current hash mode to compute new hashes.
     * When using hashSmallBlock mode, medium and full hashes should be NO_HASH.
     */
    @Test
    void run_usesCurrentHashMode() throws Exception {
        setUp(HashMode.hashSmallBlock);

        String fileName = "mode_test_file.txt";
        Path file = createFileWithContent(fileName, "Content for hash mode test");
        long fileLength = Files.size(file);

        FileState fileState = createFileState(fileName, fileLength);

        BlockingDeque<FileState> queue = new LinkedBlockingDeque<>();
        queue.add(fileState);

        HashProgress hashProgress = new HashProgress(context);

        FileReHasher reHasher = new FileReHasher(context, hashProgress, queue, rootDir);
        reHasher.run();

        // In hashSmallBlock mode, only small block hash should be computed
        assertThat(fileState.getFileHash().getSmallBlockHash()).isNotEqualTo(NO_HASH);
        assertThat(fileState.getFileHash().getMediumBlockHash()).isEqualTo(NO_HASH);
        assertThat(fileState.getFileHash().getFullHash()).isEqualTo(NO_HASH);
    }
}
