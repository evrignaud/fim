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
import org.fim.model.HashMode;
import org.fim.tooling.RepositoryTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.Constants.NO_HASH;
import static org.fim.model.HashMode.dontHash;
import static org.fim.model.HashMode.hashAll;
import static org.fim.model.HashMode.hashMediumBlock;
import static org.fim.model.HashMode.hashSmallBlock;
import static org.mockito.Mockito.mock;

/**
 * Tests verifying HashMode enum structure, FrontHasher hasher activation per mode,
 * and inactive hasher invariants (NO_HASH, 0 bytes, hashComplete=true).
 *
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6
 */
class HashModeActivationTest {

    private TestInfo testInfo;

    @BeforeEach
    void init(TestInfo testInfo) {
        this.testInfo = testInfo;
    }

    // Requirement 1.1: HashMode enum defines exactly 4 values in order
    @Test
    void hashMode_hasExactlyFourValues_inCorrectOrder() {
        HashMode[] values = HashMode.values();
        assertThat(values).hasSize(4);
        assertThat(values[0]).isEqualTo(dontHash);
        assertThat(values[1]).isEqualTo(hashSmallBlock);
        assertThat(values[2]).isEqualTo(hashMediumBlock);
        assertThat(values[3]).isEqualTo(hashAll);
    }

    @Test
    void hashMode_ordinalsAreIncreasing() {
        assertThat(dontHash.ordinal()).isLessThan(hashSmallBlock.ordinal());
        assertThat(hashSmallBlock.ordinal()).isLessThan(hashMediumBlock.ordinal());
        assertThat(hashMediumBlock.ordinal()).isLessThan(hashAll.ordinal());
    }

    // Requirement 1.3: hashSmallBlock activates only SmallBlockHasher
    @Test
    void hashSmallBlock_activatesOnlySmallBlockHasher() throws NoSuchAlgorithmException {
        Context context = createContext(hashSmallBlock);
        FrontHasher frontHasher = new FrontHasher(context);

        assertThat(frontHasher.getSmallBlockHasher().isActive()).isTrue();
        assertThat(frontHasher.getMediumBlockHasher().isActive()).isFalse();
        assertThat(frontHasher.getFullHasher().isActive()).isFalse();
    }

    // Requirement 1.4: hashMediumBlock activates SmallBlockHasher and MediumBlockHasher
    @Test
    void hashMediumBlock_activatesSmallAndMediumBlockHashers() throws NoSuchAlgorithmException {
        Context context = createContext(hashMediumBlock);
        FrontHasher frontHasher = new FrontHasher(context);

        assertThat(frontHasher.getSmallBlockHasher().isActive()).isTrue();
        assertThat(frontHasher.getMediumBlockHasher().isActive()).isTrue();
        assertThat(frontHasher.getFullHasher().isActive()).isFalse();
    }

    // Requirement 1.5: hashAll activates all three hashers
    @Test
    void hashAll_activatesAllThreeHashers() throws NoSuchAlgorithmException {
        Context context = createContext(hashAll);
        FrontHasher frontHasher = new FrontHasher(context);

        assertThat(frontHasher.getSmallBlockHasher().isActive()).isTrue();
        assertThat(frontHasher.getMediumBlockHasher().isActive()).isTrue();
        assertThat(frontHasher.getFullHasher().isActive()).isTrue();
    }

    // Requirement 1.6 + dontHash: no hashers active
    @Test
    void dontHash_activatesNoHashers() throws NoSuchAlgorithmException {
        Context context = createContext(dontHash);
        FrontHasher frontHasher = new FrontHasher(context);

        assertThat(frontHasher.getSmallBlockHasher().isActive()).isFalse();
        assertThat(frontHasher.getMediumBlockHasher().isActive()).isFalse();
        assertThat(frontHasher.getFullHasher().isActive()).isFalse();
    }

    // Requirement 1.6: Inactive hashers return NO_HASH, 0 bytes, hashComplete=true
    @Test
    void inactiveHashers_returnNoHash_zeroBytes_hashCompleteTrue() throws NoSuchAlgorithmException {
        Context context = createContext(dontHash);
        FrontHasher frontHasher = new FrontHasher(context);

        // All hashers are inactive in dontHash mode
        Hasher smallHasher = frontHasher.getSmallBlockHasher();
        Hasher mediumHasher = frontHasher.getMediumBlockHasher();
        Hasher fullHasher = frontHasher.getFullHasher();

        for (Hasher hasher : new Hasher[] { smallHasher, mediumHasher, fullHasher }) {
            assertThat(hasher.getHash()).isEqualTo(NO_HASH);
            assertThat(hasher.getBytesHashed()).isEqualTo(0);
            assertThat(hasher.hashComplete()).isTrue();
        }
    }

    // Requirement 1.6: Inactive hashers in hashSmallBlock mode
    @Test
    void hashSmallBlock_inactiveHashersReturnNoHash() throws NoSuchAlgorithmException {
        Context context = createContext(hashSmallBlock);
        FrontHasher frontHasher = new FrontHasher(context);

        Hasher mediumHasher = frontHasher.getMediumBlockHasher();
        Hasher fullHasher = frontHasher.getFullHasher();

        assertThat(mediumHasher.getHash()).isEqualTo(NO_HASH);
        assertThat(mediumHasher.getBytesHashed()).isEqualTo(0);
        assertThat(mediumHasher.hashComplete()).isTrue();

        assertThat(fullHasher.getHash()).isEqualTo(NO_HASH);
        assertThat(fullHasher.getBytesHashed()).isEqualTo(0);
        assertThat(fullHasher.hashComplete()).isTrue();
    }

    // Requirement 1.6: Inactive FullHasher in hashMediumBlock mode
    @Test
    void hashMediumBlock_inactiveFullHasherReturnsNoHash() throws NoSuchAlgorithmException {
        Context context = createContext(hashMediumBlock);
        FrontHasher frontHasher = new FrontHasher(context);

        Hasher fullHasher = frontHasher.getFullHasher();

        assertThat(fullHasher.getHash()).isEqualTo(NO_HASH);
        assertThat(fullHasher.getBytesHashed()).isEqualTo(0);
        assertThat(fullHasher.hashComplete()).isTrue();
    }

    // Requirement 1.2: dontHash returns FileHash with all NO_HASH without reading file content
    @Test
    void dontHash_returnsAllNoHash_withoutReadingFileContent() throws IOException, NoSuchAlgorithmException {
        RepositoryTool tool = new RepositoryTool(testInfo, dontHash);
        Path rootDir = tool.getRootDir();
        Context context = tool.getContext();

        HashProgress hashProgress = mock(HashProgress.class);
        FileHasher fileHasher = new FileHasher(context, null, hashProgress, null, rootDir.toString());

        // Create a test file
        Path testFile = rootDir.resolve("test_file.txt");
        Files.write(testFile, "some content to hash".getBytes());
        long fileSize = Files.size(testFile);

        FileHash result = fileHasher.hashFile(testFile, fileSize);

        assertThat(result.getSmallBlockHash()).isEqualTo(NO_HASH);
        assertThat(result.getMediumBlockHash()).isEqualTo(NO_HASH);
        assertThat(result.getFullHash()).isEqualTo(NO_HASH);

        // Verify no bytes were hashed
        FrontHasher frontHasher = fileHasher.getFrontHasher();
        assertThat(frontHasher.getBytesHashed()).isEqualTo(0);
        assertThat(frontHasher.getTotalBytesHashed()).isEqualTo(0);
    }

    // Requirement 1.2: dontHash returns all NO_HASH for empty file too
    @Test
    void dontHash_returnsAllNoHash_forEmptyFile() throws IOException, NoSuchAlgorithmException {
        RepositoryTool tool = new RepositoryTool(testInfo, dontHash);
        Path rootDir = tool.getRootDir();
        Context context = tool.getContext();

        HashProgress hashProgress = mock(HashProgress.class);
        FileHasher fileHasher = new FileHasher(context, null, hashProgress, null, rootDir.toString());

        Path testFile = rootDir.resolve("empty_file.txt");
        Files.createFile(testFile);

        FileHash result = fileHasher.hashFile(testFile, 0);

        assertThat(result.getSmallBlockHash()).isEqualTo(NO_HASH);
        assertThat(result.getMediumBlockHash()).isEqualTo(NO_HASH);
        assertThat(result.getFullHash()).isEqualTo(NO_HASH);
    }

    private Context createContext(HashMode hashMode) {
        Context context = new Context();
        context.setHashMode(hashMode);
        context.setUseDynamicScaling(false);
        return context;
    }
}
