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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.Constants.SIZE_1_MB;

/**
 * Property-based tests for FullHasher completion invariant and range size bound.
 * Feature: file-hashing
 */
public class FullHasherPropertyTest {

    private static final long MAX_RANGE_SIZE = 30L * SIZE_1_MB;

    private static final long MAX_FILE_SIZE = 100L * SIZE_1_MB;

    private Context createContext() {
        Context context = new Context();
        context.setHashMode(HashMode.hashAll);
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

    /**
     * File size and file position pairs. The position is drawn inside {@code [0, fileSize)}, which is the
     * precondition the property needs: pairs outside it tell us nothing, so none are generated. The
     * corner pairs (smallest file, largest file, last position of the largest file) come first.
     */
    static Stream<Arguments> fileSizesAndPositions() {
        List<Arguments> args = new ArrayList<>();
        args.add(Arguments.of(1L, 0L));
        args.add(Arguments.of(MAX_FILE_SIZE, 0L));
        args.add(Arguments.of(MAX_FILE_SIZE, MAX_FILE_SIZE - 1));

        Random random = RandomInputs.random();
        for (int i = 0; i < 100; i++) {
            long fileSize = RandomInputs.longBetween(random, 1L, MAX_FILE_SIZE);
            args.add(Arguments.of(fileSize, RandomInputs.longBetween(random, 0L, fileSize - 1)));
        }
        return args.stream();
    }

    // Feature: file-hashing, Property 18: FullHasher completion invariant
    // **Validates: Requirements 3.1, 3.3, 15.3**
    @ParameterizedTest
    @MethodSource("fileSizes")
    void fullHasherCompletionInvariant(long fileSize) throws NoSuchAlgorithmException {
        Context context = createContext();
        FullHasher hasher = new FullHasher(context);
        hasher.reset(fileSize);

        // Before feeding data, hashComplete should be false (file has content)
        assertThat(hasher.hashComplete()).isFalse();
        assertThat(hasher.getBytesHashed()).isNotEqualTo(fileSize);

        // Feed all file bytes in chunks matching getNextRange
        long position = 0;
        while (position < fileSize) {
            Range range = hasher.getNextRange(position);
            int chunkSize = (int) Math.min(range.getTo() - range.getFrom(), fileSize - position);
            if (chunkSize <= 0) {
                break;
            }
            // Cap buffer to a reasonable size for testing (max 1MB per chunk to avoid OOM)
            int bufferSize = (int) Math.min(chunkSize, SIZE_1_MB);
            ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
            hasher.update(position, buffer);
            position += bufferSize;
        }

        // After feeding all bytes, hashComplete should be true iff bytesHashed == fileSize
        assertThat(hasher.hashComplete()).isEqualTo(hasher.getBytesHashed() == fileSize);
    }

    // Feature: file-hashing, Property 20: FullHasher range size is bounded
    // **Validates: Requirements 3.2**
    @ParameterizedTest
    @MethodSource("fileSizesAndPositions")
    void fullHasherRangeSizeIsBounded(long fileSize, long filePosition) throws NoSuchAlgorithmException {
        // filePosition must be within [0, fileSize)
        if (filePosition >= fileSize) {
            return; // skip invalid combinations
        }

        Context context = createContext();
        FullHasher hasher = new FullHasher(context);
        hasher.reset(fileSize);

        Range range = hasher.getNextRange(filePosition);
        long rangeSize = range.getTo() - range.getFrom();

        assertThat(rangeSize).isLessThanOrEqualTo(MAX_RANGE_SIZE);
        assertThat(rangeSize).isGreaterThan(0L);
    }
}
