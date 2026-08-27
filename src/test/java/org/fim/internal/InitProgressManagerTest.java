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

package org.fim.internal;

import org.fim.model.Context;
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.FileTime;
import org.fim.model.State;
import org.fim.tooling.RepositoryTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.internal.InitProgressManager.INIT_PROGRESS_FILE;
import static org.fim.model.HashMode.hashAll;
import static org.fim.model.HashMode.hashSmallBlock;

/**
 * Tests for the progress an interrupted 'fim init' leaves behind, which is what allows the next 'fim init'
 * to carry on instead of hashing everything again.
 */
public class InitProgressManagerTest {
    private RepositoryTool tool;
    private Context context;
    private InitProgressManager initProgressManager;

    @BeforeEach
    public void setUp(TestInfo testInfo) throws IOException {
        tool = new RepositoryTool(testInfo);
        context = tool.getContext();
        Files.createDirectories(context.getRepositoryDotFimDir());
        initProgressManager = new InitProgressManager(context);
    }

    @Test
    public void aRepositoryWithoutProgressHasNothingToResume() {
        assertThat(initProgressManager.exists()).isFalse();
        assertThat(InitProgressManager.holdsInitProgress(context.getRepositoryDotFimDir())).isFalse();
        assertThat(initProgressManager.load()).isZero();
        assertThat(initProgressManager.hasSomethingToReuse()).isFalse();
    }

    @Test
    public void savedProgressIsReadBackAndReused() throws IOException {
        FileState firstFile = fileState("file01", 100, 5_000);
        FileState secondFile = fileState("dir/file02", 200, 6_000);

        initProgressManager.save(List.of(firstFile, secondFile));

        assertThat(initProgressManager.exists()).isTrue();
        assertThat(InitProgressManager.holdsInitProgress(context.getRepositoryDotFimDir())).isTrue();

        InitProgressManager resumed = new InitProgressManager(context);
        assertThat(resumed.load()).isEqualTo(2);
        assertThat(resumed.hasSomethingToReuse()).isTrue();

        assertThat(resumed.findReusableFileState("file01", 100, new FileTime(5_000))).isNotNull();
        assertThat(resumed.findReusableFileState("dir/file02", 200, new FileTime(6_000))).isNotNull();
        assertThat(resumed.getReusedFileCount()).isEqualTo(2);
    }

    @Test
    public void savingProgressLeavesNoTemporaryFile() throws IOException {
        initProgressManager.save(List.of(fileState("file01", 100, 5_000)));

        try (var dotFimDirContent = Files.list(context.getRepositoryDotFimDir())) {
            assertThat(dotFimDirContent.map(path -> path.getFileName().toString()))
                    .containsExactly(INIT_PROGRESS_FILE);
        }
    }

    /**
     * The hash of a file that is no longer the one that was hashed has to be computed again. That is the same
     * evidence 'fim status' uses to call a file unmodified, so nothing is taken on trust here that Fim would
     * not take on trust anywhere else.
     */
    @Test
    public void aFileThatChangedSinceItWasHashedIsNotReused() throws IOException {
        initProgressManager.save(List.of(fileState("file01", 100, 5_000)));

        InitProgressManager resumed = new InitProgressManager(context);
        assertThat(resumed.load()).isEqualTo(1);

        assertThat(resumed.findReusableFileState("file01", 101, new FileTime(5_000))).isNull();
        assertThat(resumed.findReusableFileState("file01", 100, new FileTime(9_000))).isNull();
        assertThat(resumed.findReusableFileState("otherFile", 100, new FileTime(5_000))).isNull();
        assertThat(resumed.getReusedFileCount()).isZero();

        assertThat(resumed.findReusableFileState("file01", 100, new FileTime(5_000))).isNotNull();
        assertThat(resumed.getReusedFileCount()).isEqualTo(1);
    }

    /**
     * Reusing hashes computed with another hash mode would describe some files of the same State more
     * thoroughly than others, so the progress is dropped rather than mixed in.
     */
    @Test
    public void progressComputedUsingAnotherHashModeIsDiscarded() throws IOException {
        assertThat(context.getHashMode()).isEqualTo(hashAll);
        initProgressManager.save(List.of(fileState("file01", 100, 5_000)));

        Context otherHashModeContext = context.clone();
        otherHashModeContext.setHashMode(hashSmallBlock);

        InitProgressManager resumed = new InitProgressManager(otherHashModeContext);
        assertThat(resumed.load()).isZero();
        assertThat(resumed.hasSomethingToReuse()).isFalse();
    }

    @Test
    public void progressThatIsNotReadableIsDiscarded() throws IOException {
        Files.write(initProgressManager.getProgressFile(), "this is not a gzipped State".getBytes(StandardCharsets.UTF_8));

        assertThat(initProgressManager.exists()).isTrue();
        assertThat(initProgressManager.load()).isZero();
    }

    /**
     * The progress goes through the same integrity check as any other State, so hashes that were altered
     * after being written are not handed out.
     */
    @Test
    public void progressWhoseContentDoesNotMatchItsHashIsDiscarded() throws IOException {
        initProgressManager.save(List.of(fileState("file01", 100, 5_000)));
        alterProgressContent();

        InitProgressManager resumed = new InitProgressManager(context);
        assertThat(resumed.load()).isZero();
    }

    @Test
    public void deletingTheProgressMarksTheInitAsFinished() throws IOException {
        initProgressManager.save(List.of(fileState("file01", 100, 5_000)));
        assertThat(initProgressManager.exists()).isTrue();

        initProgressManager.delete();
        assertThat(initProgressManager.exists()).isFalse();

        // A save racing with the end of the init must not bring the progress file back
        initProgressManager.save(List.of(fileState("file02", 100, 5_000)));
        assertThat(initProgressManager.exists()).isFalse();
    }

    @Test
    public void interruptionSavesWhatIsKnownAndDoesNothingOnceTheInitIsFinished() throws IOException {
        initProgressManager.startAutoSave(() -> List.of(fileState("file01", 100, 5_000)));
        initProgressManager.saveOnInterruption();
        assertThat(initProgressManager.exists()).isTrue();

        initProgressManager.delete();
        initProgressManager.saveOnInterruption();
        assertThat(initProgressManager.exists()).isFalse();
    }

    /**
     * The progress is saved from another thread while the hashers are still filling their results, so the
     * lists being read have to tolerate it. Saving as often as possible here makes that overlap certain.
     */
    @Test
    public void progressIsSavedWhileTheHashingGoesOn() throws IOException, NoSuchAlgorithmException {
        tool.createASetOfFiles(4);

        initProgressManager.setSaveIntervalMs(1);
        StateGenerator stateGenerator = new StateGenerator(context);
        stateGenerator.setInitProgressManager(initProgressManager);

        State state = stateGenerator.generateState("Interrupted init", context.getCurrentDirectory(), context.getCurrentDirectory());

        assertThat(state.getFileStates()).hasSize(4);
        // The directory holding the repository is Fim's own content, not something the user chose to leave out
        assertThat(state.getIgnoredFiles()).isEmpty();

        // Whatever the saves captured, the last one has to be a State that can be read back
        InitProgressManager resumed = new InitProgressManager(context);
        assertThat(resumed.load()).isPositive();
        assertThat(Files.exists(context.getRepositoryDotFimDir().resolve(INIT_PROGRESS_FILE + ".tmp"))).isFalse();
    }

    private FileState fileState(String fileName, long fileLength, long timestamp) {
        return new FileState(fileName, fileLength, new FileTime(timestamp), new FileHash("small", "medium", "full"), null);
    }

    /**
     * Change the content of the progress file without touching the hash that vouches for it.
     */
    private void alterProgressContent() throws IOException {
        Path progressFile = initProgressManager.getProgressFile();

        String content;
        try (InputStream input = new GZIPInputStream(Files.newInputStream(progressFile))) {
            content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        String alteredContent = content.replace("\"fileLength\": 100", "\"fileLength\": 999");
        assertThat(alteredContent).isNotEqualTo(content);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (OutputStream output = new GZIPOutputStream(buffer)) {
            output.write(alteredContent.getBytes(StandardCharsets.UTF_8));
        }
        Files.write(progressFile, buffer.toByteArray());
    }
}
