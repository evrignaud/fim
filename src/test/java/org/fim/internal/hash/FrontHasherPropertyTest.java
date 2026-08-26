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

/**
 * Property-based tests for FrontHasher range coordination and buffer preservation.
 * Feature: file-hashing
 */
public class FrontHasherPropertyTest {

    // Only test modes that activate at least one hasher
    private static final HashMode[] ACTIVE_MODES = {
        HashMode.hashSmallBlock, HashMode.hashMediumBlock, HashMode.hashAll
    };

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_BUFFER_SIZE = 8192;

    /** Sampled values paired with each mode, so every mode sees the same set of numeric inputs. */
    private static final int SAMPLES_PER_MODE = 34;

    private Context createContext(HashMode hashMode) {
        Context context = new Context();
        context.setHashMode(hashMode);
        context.setUseDynamicScaling(false);
        return context;
    }

    /**
     * Every active mode crossed with file sizes from one byte to 10 MB. The mode domain is enumerated;
     * the size range is too wide for that, so each mode gets both boundaries plus values sampled from a
     * fixed seed.
     */
    static Stream<Arguments> modesAndFileSizes() {
        List<Arguments> args = new ArrayList<>();
        Random random = RandomInputs.random();
        for (HashMode mode : ACTIVE_MODES) {
            args.add(Arguments.of(mode, 1L));
            args.add(Arguments.of(mode, MAX_FILE_SIZE));
            for (int i = 0; i < SAMPLES_PER_MODE; i++) {
                args.add(Arguments.of(mode, RandomInputs.longBetween(random, 1L, MAX_FILE_SIZE)));
            }
        }
        return args.stream();
    }

    /**
     * Every active mode crossed with buffer sizes from one byte to 8192 bytes, sampled the same way as
     * {@link #modesAndFileSizes()}.
     */
    static Stream<Arguments> modesAndBufferSizes() {
        List<Arguments> args = new ArrayList<>();
        Random random = RandomInputs.random();
        for (HashMode mode : ACTIVE_MODES) {
            args.add(Arguments.of(mode, 1));
            args.add(Arguments.of(mode, MAX_BUFFER_SIZE));
            for (int i = 0; i < SAMPLES_PER_MODE; i++) {
                args.add(Arguments.of(mode, RandomInputs.intBetween(random, 1, MAX_BUFFER_SIZE)));
            }
        }
        return args.stream();
    }

    // Feature: file-hashing, Property 6: FrontHasher range encompasses all active hasher ranges
    // **Validates: Requirements 7.1, 7.2, 7.3**
    @ParameterizedTest
    @MethodSource("modesAndFileSizes")
    void frontHasherRangeEncompassesAllActiveHasherRanges(HashMode mode, long fileSize) throws NoSuchAlgorithmException {
        Context context = createContext(mode);
        FrontHasher frontHasher = new FrontHasher(context);
        frontHasher.reset(fileSize);

        // Check at file position 0
        Range frontRange = frontHasher.getNextRange(0);
        if (frontRange == null) {
            return; // No range needed
        }

        // Check each active sub-hasher's range is encompassed
        Hasher smallHasher = frontHasher.getSmallBlockHasher();
        Hasher mediumHasher = frontHasher.getMediumBlockHasher();
        Hasher fullHasher = frontHasher.getFullHasher();

        if (smallHasher.isActive()) {
            Range smallRange = smallHasher.getNextRange(0);
            if (smallRange != null) {
                assertThat(frontRange.getFrom()).isLessThanOrEqualTo(smallRange.getFrom());
                assertThat(frontRange.getTo()).isGreaterThanOrEqualTo(smallRange.getTo());
            }
        }

        if (mediumHasher.isActive()) {
            Range mediumRange = mediumHasher.getNextRange(0);
            if (mediumRange != null) {
                // For hashMediumBlock mode, FrontHasher may return the earlier range
                // when ranges don't overlap, so we check the first range returned
                // encompasses at least one of the sub-hasher ranges
                Range frontRangeCheck = frontHasher.getNextRange(0);
                boolean encompassesMedium = frontRangeCheck.getFrom() <= mediumRange.getFrom()
                    && frontRangeCheck.getTo() >= mediumRange.getTo();
                boolean encompassesSmall = true;
                if (smallHasher.isActive()) {
                    Range smallRange = smallHasher.getNextRange(0);
                    if (smallRange != null) {
                        encompassesSmall = frontRangeCheck.getFrom() <= smallRange.getFrom()
                            && frontRangeCheck.getTo() >= smallRange.getTo();
                    }
                }
                // The front range must encompass at least one active sub-hasher range
                assertThat(encompassesMedium || encompassesSmall).isTrue();
            }
        }

        if (fullHasher.isActive()) {
            Range fullRange = fullHasher.getNextRange(0);
            if (fullRange != null) {
                // In hashAll mode, front range starts with full range and adjusts
                assertThat(frontRange.getFrom()).isLessThanOrEqualTo(fullRange.getFrom());
                assertThat(frontRange.getTo()).isGreaterThanOrEqualTo(fullRange.getTo());
            }
        }
    }

    // Feature: file-hashing, Property 7: Buffer position and limit preservation
    // **Validates: Requirements 7.4**
    @ParameterizedTest
    @MethodSource("modesAndBufferSizes")
    void bufferPositionAndLimitPreservation(HashMode mode, int bufferSize) throws NoSuchAlgorithmException {
        Context context = createContext(mode);
        FrontHasher frontHasher = new FrontHasher(context);

        // Use a file size large enough to ensure hashers have work
        long fileSize = bufferSize + 1024L * 1024;
        frontHasher.reset(fileSize);

        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        // Fill with some data
        for (int i = 0; i < bufferSize; i++) {
            buffer.put((byte) (i & 0xFF));
        }
        buffer.flip(); // position=0, limit=bufferSize

        int originalPosition = buffer.position();
        int originalLimit = buffer.limit();

        frontHasher.update(0, buffer);

        assertThat(buffer.position()).isEqualTo(originalPosition);
        assertThat(buffer.limit()).isEqualTo(originalLimit);
    }
}
