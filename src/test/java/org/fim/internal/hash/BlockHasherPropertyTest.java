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
import org.fim.model.HashMode;
import org.fim.model.Range;
import org.fim.tooling.RandomInputs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.Constants.SIZE_1_MB;
import static org.fim.model.Constants.SIZE_4_KB;

/**
 * Property-based tests for BlockHasher block selection strategy, range clamping, and completion.
 * Feature: file-hashing
 */
public class BlockHasherPropertyTest {

    private static final long MAX_FILE_SIZE = 100L * SIZE_1_MB;

    private Context createContext(HashMode hashMode) {
        Context context = new Context();
        context.setHashMode(hashMode);
        context.setUseDynamicScaling(false);
        return context;
    }

    /**
     * File sizes from one byte to 100 MB. The range is far too wide to enumerate, so both boundaries are
     * listed explicitly and the rest is sampled from a fixed seed.
     */
    static Stream<Long> fileSizes() {
        List<Long> values = new ArrayList<>();
        values.add(1L);
        values.add(MAX_FILE_SIZE);

        Random random = RandomInputs.random();
        for (int i = 0; i < 100; i++) {
            values.add(RandomInputs.longBetween(random, 1L, MAX_FILE_SIZE));
        }
        return values.stream();
    }

    // Feature: file-hashing, Property 4: Block selection strategy based on file size
    // **Validates: Requirements 4.2, 4.3, 4.4, 4.5, 5.2, 5.3, 5.4, 5.5, 6.1, 6.2, 6.3, 6.4**
    @ParameterizedTest
    @MethodSource("fileSizes")
    void blockSelectionStrategyBasedOnFileSize(long fileSize) throws NoSuchAlgorithmException {
        // Test both SmallBlockHasher (4KB) and MediumBlockHasher (1MB)
        verifyBlockSelection(fileSize, SIZE_4_KB, HashMode.hashSmallBlock);
        verifyBlockSelection(fileSize, SIZE_1_MB, HashMode.hashMediumBlock);
    }

    private void verifyBlockSelection(long fileSize, int blockSize, HashMode mode) throws NoSuchAlgorithmException {
        Context context = createContext(mode);
        BlockHasher hasher;
        if (blockSize == SIZE_4_KB) {
            hasher = new SmallBlockHasher(context);
        } else {
            hasher = new MediumBlockHasher(context);
        }
        hasher.reset(fileSize);

        Range[] ranges = hasher.getRanges();
        long middleBlockIndex = (fileSize / blockSize) / 2;
        long endBlockIndex = (fileSize / blockSize) - 1;

        long longBlockSize = blockSize;
        if (fileSize > longBlockSize * 4) {
            // 3 blocks: index 1, middle, end
            assertThat(ranges).hasSize(3);
            assertThat(ranges[0].getFrom()).isEqualTo(Math.min(fileSize, longBlockSize));
            assertThat(ranges[1].getFrom()).isEqualTo(Math.min(fileSize, middleBlockIndex * blockSize));
            assertThat(ranges[2].getFrom()).isEqualTo(Math.min(fileSize, endBlockIndex * blockSize));
        } else if (fileSize > longBlockSize * 3) {
            // 2 blocks: index 1, end
            assertThat(ranges).hasSize(2);
            assertThat(ranges[0].getFrom()).isEqualTo(Math.min(fileSize, longBlockSize));
            assertThat(ranges[1].getFrom()).isEqualTo(Math.min(fileSize, endBlockIndex * blockSize));
        } else if (fileSize > longBlockSize * 2) {
            // 1 block: index 1
            assertThat(ranges).hasSize(1);
            assertThat(ranges[0].getFrom()).isEqualTo(Math.min(fileSize, longBlockSize));
        } else {
            // 1 block: index 0
            assertThat(ranges).hasSize(1);
            assertThat(ranges[0].getFrom()).isEqualTo(0L);
        }
    }

    // Feature: file-hashing, Property 5: Block ranges are clamped to file size
    // **Validates: Requirements 6.5**
    @ParameterizedTest
    @MethodSource("fileSizes")
    void blockRangesAreClampedToFileSize(long fileSize) throws NoSuchAlgorithmException {
        // Test SmallBlockHasher
        Context smallCtx = createContext(HashMode.hashSmallBlock);
        SmallBlockHasher smallHasher = new SmallBlockHasher(smallCtx);
        smallHasher.reset(fileSize);
        for (Range range : smallHasher.getRanges()) {
            assertThat(range.getFrom()).isGreaterThanOrEqualTo(0L);
            assertThat(range.getTo()).isLessThanOrEqualTo(fileSize);
        }

        // Test MediumBlockHasher
        Context medCtx = createContext(HashMode.hashMediumBlock);
        MediumBlockHasher medHasher = new MediumBlockHasher(medCtx);
        medHasher.reset(fileSize);
        for (Range range : medHasher.getRanges()) {
            assertThat(range.getFrom()).isGreaterThanOrEqualTo(0L);
            assertThat(range.getTo()).isLessThanOrEqualTo(fileSize);
        }
    }

    // Feature: file-hashing, Property 19: BlockHasher completion invariant
    // **Validates: Requirements 4.7, 5.7, 15.4**
    @ParameterizedTest
    @MethodSource("fileSizes")
    void blockHasherCompletionInvariant(long fileSize) throws NoSuchAlgorithmException {
        // Test with SmallBlockHasher
        verifyBlockHasherCompletion(fileSize, HashMode.hashSmallBlock);
        // Test with MediumBlockHasher
        verifyBlockHasherCompletion(fileSize, HashMode.hashMediumBlock);
    }

    private void verifyBlockHasherCompletion(long fileSize, HashMode mode) throws NoSuchAlgorithmException {
        Context context = createContext(mode);
        BlockHasher hasher;
        if (mode == HashMode.hashSmallBlock) {
            hasher = new SmallBlockHasher(context);
        } else {
            hasher = new MediumBlockHasher(context);
        }
        hasher.reset(fileSize);

        // Compute expected sizeToHash from ranges
        Range[] ranges = hasher.getRanges();
        long expectedSizeToHash = 0;
        for (Range range : ranges) {
            expectedSizeToHash += range.getTo() - range.getFrom();
        }

        // Before feeding any data, hashComplete should be false (unless sizeToHash is 0)
        if (expectedSizeToHash > 0) {
            assertThat(hasher.hashComplete()).isFalse();
        }

        // Feed all block data
        for (Range range : ranges) {
            int rangeSize = (int) (range.getTo() - range.getFrom());
            if (rangeSize > 0) {
                ByteBuffer buffer = ByteBuffer.allocate(rangeSize);
                buffer.position(0);
                buffer.limit(rangeSize);
                hasher.update(range.getFrom(), buffer);
            }
        }

        // After feeding all data, hashComplete should be true
        assertThat(hasher.hashComplete()).isTrue();
        assertThat(hasher.getBytesHashed()).isEqualTo(expectedSizeToHash);
    }
}
