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

package org.fim.command;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.fim.command.exception.BadFimUsageException;
import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.model.Difference;
import org.fim.model.FileState;
import static org.fim.model.HashMode.dontHash;
import static org.fim.model.HashMode.hashAll;
import static org.fim.model.HashMode.hashMediumBlock;
import static org.fim.model.HashMode.hashSmallBlock;
import org.fim.model.State;
import org.fim.tooling.RepositoryTool;
import org.fim.util.Logger;
import org.fim.util.TimeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public class DetectCorruptionCommandTest {
    private static final String SUBDIR = "subdir";
    private static final String SUBDIR_SUB_FILE01 = "subdir/sub_file01";
    private static final String FILE_01 = "file01";

    private InitCommand initCommand;
    private StatusCommand statusCommand;
    private DetectCorruptionCommand detectCorruptionCommand;

    private RepositoryTool tool;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        initCommand = new InitCommand();
        statusCommand = new StatusCommand();
        detectCorruptionCommand = new DetectCorruptionCommand();

        tool = new RepositoryTool(testInfo);
    }

    @Test
    public void dontHash_NotAllowed() throws Exception {
        Context context = tool.createContext(dontHash, false);
        assertThatThrownBy(() -> detectCorruptionCommand.execute(context))
                .isInstanceOf(BadFimUsageException.class);
    }

    @Test
    public void hashSmallBlock_NotAllowed() throws Exception {
        Context context = tool.createContext(hashSmallBlock, false);
        assertThatThrownBy(() -> detectCorruptionCommand.execute(context))
                .isInstanceOf(BadFimUsageException.class);
    }

    @Test
    public void hashMediumBlock_NotAllowed() throws Exception {
        Context context = tool.createContext(hashMediumBlock, false);
        assertThatThrownBy(() -> detectCorruptionCommand.execute(context))
                .isInstanceOf(BadFimUsageException.class);
    }

    @Test
    public void canDetectHardwareCorruption() throws Exception {
        Context context = tool.createContext(hashAll, true);

        tool.createASetOfFiles(5);

        State state = (State) initCommand.execute(context);
        assertThat(state.getModificationCounts().getAdded()).isEqualTo(5);

        CompareResult compareResult = (CompareResult) detectCorruptionCommand.execute(context);
        assertThat(compareResult.getCorrupted().size()).isEqualTo(0);

        doSomeModifications();

        compareResult = (CompareResult) statusCommand.execute(context);
        assertThat(compareResult.modifiedCount()).isEqualTo(3);

        compareResult = (CompareResult) detectCorruptionCommand.execute(context);
        assertThat(compareResult.getCorrupted().size()).isEqualTo(1);
        FileState fileState = compareResult.getCorrupted().get(0).getFileState();
        assertThat(fileState.getFileName()).isEqualTo("file03");
    }

    // --- Task 1.1: "Nothing corrupted" message when no corruption found (Req 1.7) ---

    @Test
    public void nothingCorruptedMessageDisplayedWhenNoCorruption() throws Exception {
        Context context = tool.createContext(hashAll, true);
        tool.createASetOfFiles(3);
        initCommand.execute(context);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream oldOut = Logger.out;
        try (PrintStream captureStream = new PrintStream(baos)) {
            Logger.out = captureStream;
            CompareResult compareResult = (CompareResult) detectCorruptionCommand.execute(context);
            assertThat(compareResult.getCorrupted()).isEmpty();
        } finally {
            Logger.out = oldOut;
        }

        String output = baos.toString();
        assertThat(output).contains("Nothing corrupted");
    }

    // --- Task 1.1: hashAll mode proceeds and returns CompareResult (Req 1.1, 1.3) ---

    @Test
    public void hashAllModeProceeds() throws Exception {
        Context context = tool.createContext(hashAll, true);
        tool.createASetOfFiles(2);
        initCommand.execute(context);

        Object result = detectCorruptionCommand.execute(context);
        assertThat(result).isInstanceOf(CompareResult.class);
    }

    // --- Task 2.1: Subdirectory invocation filters last state to subtree (Req 2.1, 2.3) ---

    @Test
    public void subdirectoryInvocationFiltersToSubtree() throws Exception {
        Context context = tool.createContext(hashAll, true);

        // Create files in root and in a subdirectory
        tool.createASetOfFiles(2);
        Path subdir = tool.getRootDir().resolve(SUBDIR);
        Files.createDirectories(subdir);
        tool.createFile(subdir.resolve("sub_file01"));
        tool.createFile(subdir.resolve("sub_file02"));

        initCommand.execute(context);

        // Simulate hardware corruption on a file in the subdirectory
        simulateHardwareCorruption(SUBDIR_SUB_FILE01);

        // Also simulate corruption on a root file
        simulateHardwareCorruption(FILE_01);

        // Run from subdirectory — should only detect corruption in subdir
        Context subDirContext = tool.createInvokedFromSubDirContext(hashAll, SUBDIR, true);
        CompareResult result = (CompareResult) detectCorruptionCommand.execute(subDirContext);

        // Only the subdirectory file should be detected as corrupted
        assertThat(result.getCorrupted()).hasSize(1);
        assertThat(result.getCorrupted().get(0).getFileState().getFileName()).isEqualTo(SUBDIR_SUB_FILE01);
    }

    // --- Task 2.1: Root invocation uses full state without filtering (Req 2.2) ---

    @Test
    public void rootInvocationUsesFullState() throws Exception {
        Context context = tool.createContext(hashAll, true);

        tool.createASetOfFiles(2);
        Path subdir = tool.getRootDir().resolve(SUBDIR);
        Files.createDirectories(subdir);
        tool.createFile(subdir.resolve("sub_file01"));

        initCommand.execute(context);

        // Simulate corruption on both root and subdir files
        simulateHardwareCorruption(FILE_01);
        simulateHardwareCorruption(SUBDIR_SUB_FILE01);

        // Run from root — should detect all corruptions
        CompareResult result = (CompareResult) detectCorruptionCommand.execute(context);
        assertThat(result.getCorrupted()).hasSize(2);
    }

    // --- Task 7.1: End-to-end corruption detection invariants (Req 6.1-6.6) ---

    @Test
    public void corruptionDetectionInvariants() throws Exception {
        Context context = tool.createContext(hashAll, true);
        tool.createASetOfFiles(5);
        initCommand.execute(context);

        doSomeModifications();

        CompareResult compareResult = (CompareResult) detectCorruptionCommand.execute(context);

        // Req 6.1: corrupted Difference has same fileName
        assertThat(compareResult.getCorrupted()).hasSize(1);
        Difference corruptedDiff = compareResult.getCorrupted().get(0);
        assertThat(corruptedDiff.getFileState().getFileName())
                .isEqualTo(corruptedDiff.getPreviousFileState().getFileName()); // Req 6.1

        // Req 6.2: same FileTime
        assertThat(corruptedDiff.getFileState().getFileTime())
                .isEqualTo(corruptedDiff.getPreviousFileState().getFileTime()); // Req 6.2

        // Req 6.3: different FileHash
        assertThat(corruptedDiff.getFileState().getFileHash())
                .isNotEqualTo(corruptedDiff.getPreviousFileState().getFileHash()); // Req 6.3

        // Req 6.4: unchanged-hash files not in corrupted list
        // file04 and file05 are unchanged — verify they're not in corrupted list
        for (Difference diff : compareResult.getCorrupted()) {
            assertThat(diff.getFileState().getFileName()).isNotIn("file04", "file05");
        }

        // Req 6.5: timestamp-changed files not in corrupted list
        // file01 had its timestamp changed, file02 had content changed (with new timestamp)
        for (Difference diff : compareResult.getCorrupted()) {
            assertThat(diff.getFileState().getFileName()).isNotIn(FILE_01, "file02");
        }

        // Req 6.6: non-corruption lists empty in corruption mode result
        assertThat(compareResult.getAdded()).isEmpty();
        assertThat(compareResult.getDeleted()).isEmpty();
        assertThat(compareResult.getRenamed()).isEmpty();
        assertThat(compareResult.getCopied()).isEmpty();
        assertThat(compareResult.getDuplicated()).isEmpty();
    }

    private void doSomeModifications() throws IOException {
        TimeUtil.sleepSafely(1_000); // Ensure to increase lastModified at least of 1 second

        tool.touchLastModified(FILE_01);

        tool.setFileContent("file02", "file02 new content");

        simulateHardwareCorruption("file03");

        // Do nothing on file04 and file05
    }

    /**
     * Hardware corruption is a byte changing under Fim's feet, with the timestamps untouched, which is what
     * lets {@code dcor} tell it apart from an ordinary edit. The test has to reproduce exactly that: same
     * name, same times, different content.
     * <p>
     * The bytes are overwritten in place rather than by deleting and recreating the file. A delete followed
     * by a write makes a new file, and on Linux the creation time of a new file cannot be set back:
     * {@code BasicFileAttributeView.setTimes} silently ignores the creation-time argument on ext4. The
     * restored {@code FileTime} then differed from the committed one, the change was read as a content
     * modification rather than corruption, and the whole thing was invisible on Linux while passing on
     * Windows and macOS. Writing into the existing file keeps its inode, so its creation time never moves,
     * and only the last-modified time has to be restored.
     */
    private void simulateHardwareCorruption(String fileName) throws IOException {
        Path file = tool.getRootDir().resolve(fileName);
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);

        // A zero byte appears in the middle of the file, written into the file that is already there.
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length / 2] = 0;
        Files.write(file, bytes, WRITE, TRUNCATE_EXISTING);

        // Writing moved the last-modified time. Put it back, so the only thing that changed is the content,
        // which is the definition of the corruption being detected.
        Files.getFileAttributeView(file, BasicFileAttributeView.class)
                .setTimes(attributes.lastModifiedTime(), attributes.lastAccessTime(), attributes.creationTime());
    }
}
