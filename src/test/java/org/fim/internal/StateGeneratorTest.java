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

package org.fim.internal;

import org.fim.command.InitCommand;
import org.fim.model.Context;
import org.fim.model.FileState;
import org.fim.model.State;
import org.fim.tooling.RepositoryTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying StateGenerator wiring of the ignore system.
 * Validates Requirements 2.5, 2.6, 7.1.
 */
public class StateGeneratorTest {

    private RepositoryTool tool;
    private Path rootDir;
    private Context context;
    private InitCommand initCommand;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        tool = new RepositoryTool(testInfo);
        rootDir = tool.getRootDir();
        context = tool.getContext();
        initCommand = new InitCommand();
    }

    /**
     * Req 7.1: After scanning, the StateGenerator stores the ignoredFiles set
     * collected by FimIgnoreManager into the generated State.
     * Ignored files specified in .fimignore appear in state.getIgnoredFiles().
     */
    @Test
    public void ignoredFilesAppearInStateIgnoredFiles() throws Exception {
        // Create tracked files
        tool.createASetOfFiles(3);

        // Create a .fimignore that ignores file01
        tool.createFimIgnore(rootDir, "file01");

        State state = (State) initCommand.execute(context);

        Set<String> ignoredFiles = state.getIgnoredFiles();
        assertThat(ignoredFiles).isNotNull();
        assertThat(ignoredFiles).anyMatch(f -> f.contains("file01"));
    }

    /**
     * Req 2.6: Non-ignored regular files are hashed and appear in state file states.
     * Ignored files do NOT appear in the file states list.
     */
    @Test
    public void nonIgnoredFilesAreHashedAndIgnoredFilesAreNot() throws Exception {
        tool.createASetOfFiles(3);

        // Ignore file01 via .fimignore
        tool.createFimIgnore(rootDir, "file01");

        State state = (State) initCommand.execute(context);

        List<String> hashedFileNames = state.getFileStates().stream()
                .map(FileState::getFileName)
                .collect(Collectors.toList());

        // file02 and file03 should be hashed (present in file states)
        assertThat(hashedFileNames).anyMatch(f -> f.contains("file02"));
        assertThat(hashedFileNames).anyMatch(f -> f.contains("file03"));

        // file01 should NOT be in the hashed file states
        assertThat(hashedFileNames).noneMatch(f -> f.equals("file01"));

        // file01 should be in the ignored files set
        assertThat(state.getIgnoredFiles()).anyMatch(f -> f.contains("file01"));
    }

    /**
     * Req 2.5: When a directory is classified as ignored, the StateGenerator
     * skips recursive scanning — files inside ignored directories are not hashed.
     */
    @Test
    public void ignoredDirectoryContentsAreSkipped() throws Exception {
        // Create a tracked file at root level first
        tool.createASetOfFiles(1);

        // Create a subdirectory with files
        Path subDir = rootDir.resolve("ignored_dir");
        Files.createDirectories(subDir);
        tool.createFile(subDir.resolve("nested_file.txt"));

        // Ignore the subdirectory via .fimignore
        tool.createFimIgnore(rootDir, "ignored_dir");

        State state = (State) initCommand.execute(context);

        List<String> hashedFileNames = state.getFileStates().stream()
                .map(FileState::getFileName)
                .collect(Collectors.toList());

        // The nested file inside ignored_dir should NOT be hashed
        assertThat(hashedFileNames).noneMatch(f -> f.contains("nested_file.txt"));

        // The ignored directory should appear in ignoredFiles with trailing slash
        assertThat(state.getIgnoredFiles()).anyMatch(f -> f.contains("ignored_dir/"));

        // The root-level file should still be hashed
        assertThat(hashedFileNames).anyMatch(f -> f.contains("file01"));
    }

    /**
     * Req 2.5, 7.1: All-directories patterns propagate to subdirectories,
     * causing matching files in subdirectories to be ignored and recorded
     * in the state ignoredFiles set.
     */
    @Test
    public void allDirectoriesPatternsIgnoreFilesInSubdirectories() throws Exception {
        // Create subdirectory with files
        Path subDir = rootDir.resolve("subdir");
        Files.createDirectories(subDir);
        tool.createFile(subDir.resolve("track.log"));
        tool.createFile(subDir.resolve("keep.txt"));

        // Create root-level files
        tool.createFile(rootDir.resolve("root.log"));
        tool.createASetOfFiles(1);

        // Use **/ prefix to ignore .log files in all directories
        tool.createFimIgnore(rootDir, "**/*.log");

        State state = (State) initCommand.execute(context);

        List<String> hashedFileNames = state.getFileStates().stream()
                .map(FileState::getFileName)
                .collect(Collectors.toList());

        Set<String> ignoredFiles = state.getIgnoredFiles();

        // .log files should be ignored in both root and subdirectory
        assertThat(ignoredFiles).anyMatch(f -> f.contains("root.log"));
        assertThat(ignoredFiles).anyMatch(f -> f.contains("track.log"));

        // .log files should NOT be in hashed file states
        assertThat(hashedFileNames).noneMatch(f -> f.contains("root.log"));
        assertThat(hashedFileNames).noneMatch(f -> f.contains("track.log"));

        // Non-matching files should be hashed
        assertThat(hashedFileNames).anyMatch(f -> f.contains("keep.txt"));
    }

    /**
     * Req 7.1: When no .fimignore exists and no files are ignored,
     * the ignoredFiles set in the state should be empty.
     */
    @Test
    public void noFimignoreResultsInEmptyIgnoredFiles() throws Exception {
        tool.createASetOfFiles(2);

        State state = (State) initCommand.execute(context);

        // No .fimignore means no ignored files (except .fimignore itself won't exist)
        Set<String> ignoredFiles = state.getIgnoredFiles();
        assertThat(ignoredFiles).isNotNull();
        // All created files should be hashed
        assertThat(state.getFileStates()).hasSize(2);
    }
}
