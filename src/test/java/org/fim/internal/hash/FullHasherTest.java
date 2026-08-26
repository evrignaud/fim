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
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.Constants.NO_HASH;
import static org.fim.model.Constants.SIZE_1_MB;

/**
 * Unit tests for FullHasher verifying chunk-based hashing, completion logic,
 * and inactive behavior across various file sizes.
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5
 */
class FullHasherTest {

    private static final int FULL_HASHER_BLOCK_SIZE = 30 * SIZE_1_MB;

    // --- Requirement 3.2: FullHasher BLOCK_SIZE is 30 MB ---

    @Test
    void blockSize_is30MB() {
        assertThat(FullHasher.BLOCK_SIZE).isEqualTo(30 * SIZE_1_MB);
    }

    // --- Requirement 3.5: Inactive FullHasher returns NO_HASH, 0 bytes, hashComplete=true ---

    @Test
    void inactive_returnsNoHash_zeroBytes_hashCompleteTrue() throws NoSuchAlgorithmException {
        FullHasher hasher = createFullHasher(HashMode.dontHash);

        assertThat(hasher.isActive()).isFalse();
        assertThat(hasher.getHash()).isEqualTo(NO_HASH);
        assertThat(hasher.getBytesHashed()).isEqualTo(0);
        assertThat(hasher.hashComplete()).isTrue();
    }

    @Test
    void inactive_afterReset_stillReturnsNoHash() throws NoSuchAlgorithmException {
        FullHasher hasher = createFullHasher(HashMode.hashSmallBlock);

        assertThat(hasher.isActive()).isFalse();
        hasher.reset(1000);
        assertThat(hasher.getHash()).isEqualTo(NO_HASH);
        assertThat(hasher.getBytesHashed()).isEqualTo(0);
        assertThat(hasher.hashComplete()).isTrue();
    }

    @Test
    void inactive_inHashMediumBlockMode() throws NoSuchAlgorithmException {
        FullHasher hasher = createFullHasher(HashMode.hashMediumBlock);

        assertThat(hasher.isActive()).isFalse();
        assertThat(hasher.getHash()).isEqualTo(NO_HASH);
        assertThat(hasher.getBytesHashed()).isEqualTo(0);
        assertThat(hasher.hashComplete()).isTrue();
    }

    // --- Requirement 3.1, 3.3: Active FullHasher activation and completion ---

    @Test
    void active_inHashAllMode() throws NoSuchAlgorithmException {
        FullHasher hasher = createFullHasher(HashMode.hashAll);
        assertThat(hasher.isActive()).isTrue();
    }

    // --- File size: 0 bytes ---

    @Test
    void zeroByteFile_hashCompleteImmediately() throws NoSuchAlgorithmException {
        FullHasher hasher = createFullHasher(HashMode.hashAll);
        hasher.reset(0);

        assertThat(hasher.getBytesHashed()).isEqualTo(0);
        assertThat(hasher.hashComplete()).isTrue();
    }

    @Test
    void zeroByteFile_getNextRange_returnsEmptyRange() throws NoSuchAlgorithmException {
        FullHasher hasher = createFullHasher(HashMode.hashAll);
        hasher.reset(0);

        Range range = hasher.getNextRange(0);
        assertThat(range.getFrom()).isEqualTo(0);
        assertThat(range.getTo()).isEqualTo(0);
    }

    // --- File size: < 30 MB (e.g. 1 MB) ---

    @Test
    void smallFile_singleChunk_rangeCoversEntireFile() throws NoSuchAlgorithmException {
        long fileSize = SIZE_1_MB;
        FullHasher hasher = createFullHasher(HashMode.hashAll);
        hasher.reset(fileSize);

        Range range = hasher.getNextRange(0);
        assertThat(range.getFrom()).isEqualTo(0);
        assertThat(range.getTo()).isEqualTo(fileSize);
        assertThat(range.getTo() - range.getFrom()).isEqualTo(fileSize);
    }

    @Test
    void smallFile_hashComplete_afterAllBytesHashed() throws NoSuchAlgorithmException {
        long fileSize = SIZE_1_MB;
        FullHasher hasher = createFullHasher(HashMode.hashAll);
        hasher.reset(fileSize);

        assertThat(hasher.hashComplete()).isFalse();

        // Feed all bytes
        ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
        hasher.update(0, buffer);

        assertThat(hasher.getBytesHashed()).isEqualTo(fileSize);
        assertThat(hasher.hashComplete()).isTrue();
    }

    // --- File size: exactly 30 MB ---

    @Test
    void exactly30MB_singleChunk_rangeCoversEntireFile() throws NoSuchAlgorithmException {
        long fileSize = FULL_HASHER_BLOCK_SIZE;
        FullHasher hasher = createFullHasher(HashMode.hashAll);
        hasher.reset(fileSize);

        Range range = hasher.getNextRange(0);
        assertThat(range.getFrom()).isEqualTo(0);
        assertThat(range.getTo()).isEqualTo(fileSize);
        assertThat(range.getTo() - range.getFrom()).isEqualTo(fileSize);
    }

    // --- File size: > 30 MB (e.g. 60 MB) ---

    @Test
    void largeFile_multipleChunks_firstChunkIs30MB() throws NoSuchAlgorithmException {
        long fileSize = 2L * FULL_HASHER_BLOCK_SIZE; // 60 MB
        FullHasher hasher = createFullHasher(HashMode.hashAll);
        hasher.reset(fileSize);

        Range firstRange = hasher.getNextRange(0);
        assertThat(firstRange.getFrom()).isEqualTo(0);
        assertThat(firstRange.getTo()).isEqualTo(FULL_HASHER_BLOCK_SIZE);
        assertThat(firstRange.getTo() - firstRange.getFrom()).isEqualTo(FULL_HASHER_BLOCK_SIZE);
    }

    @Test
    void largeFile_multipleChunks_secondChunkIs30MB() throws NoSuchAlgorithmException {
        long fileSize = 2L * FULL_HASHER_BLOCK_SIZE; // 60 MB
        FullHasher hasher = createFullHasher(HashMode.hashAll);
        hasher.reset(fileSize);

        Range secondRange = hasher.getNextRange(FULL_HASHER_BLOCK_SIZE);
        assertThat(secondRange.getFrom()).isEqualTo(FULL_HASHER_BLOCK_SIZE);
        assertThat(secondRange.getTo()).isEqualTo(fileSize);
        assertThat(secondRange.getTo() - secondRange.getFrom()).isEqualTo(FULL_HASHER_BLOCK_SIZE);
    }

    @Test
    void largeFile_rangeNeverExceeds30MB() throws NoSuchAlgorithmException {
        long fileSize = 3L * FULL_HASHER_BLOCK_SIZE + SIZE_1_MB; // 91 MB
        FullHasher hasher = createFullHasher(HashMode.hashAll);
        hasher.reset(fileSize);

        long position = 0;
        while (position < fileSize) {
            Range range = hasher.getNextRange(position);
            long rangeSize = range.getTo() - range.getFrom();
            assertThat(rangeSize).isLessThanOrEqualTo(FULL_HASHER_BLOCK_SIZE);
            assertThat(range.getFrom()).isEqualTo(position);
            assertThat(range.getTo()).isLessThanOrEqualTo(fileSize);
            position = range.getTo();
        }
    }

    // --- Requirement 3.3: hashComplete only when bytesHashed == fileSize ---

    @Test
    void hashComplete_falseWhenPartiallyHashed() throws NoSuchAlgorithmException {
        long fileSize = 2L * FULL_HASHER_BLOCK_SIZE; // 60 MB
        FullHasher hasher = createFullHasher(HashMode.hashAll);
        hasher.reset(fileSize);

        // Feed only first chunk (30 MB)
        ByteBuffer buffer = ByteBuffer.allocate(FULL_HASHER_BLOCK_SIZE);
        hasher.update(0, buffer);

        assertThat(hasher.getBytesHashed()).isEqualTo(FULL_HASHER_BLOCK_SIZE);
        assertThat(hasher.hashComplete()).isFalse();
    }

    @Test
    void hashComplete_trueWhenAllBytesHashed() throws NoSuchAlgorithmException {
        long fileSize = 2L * FULL_HASHER_BLOCK_SIZE; // 60 MB
        FullHasher hasher = createFullHasher(HashMode.hashAll);
        hasher.reset(fileSize);

        // Feed first chunk
        ByteBuffer buffer1 = ByteBuffer.allocate(FULL_HASHER_BLOCK_SIZE);
        hasher.update(0, buffer1);

        // Feed second chunk
        ByteBuffer buffer2 = ByteBuffer.allocate(FULL_HASHER_BLOCK_SIZE);
        hasher.update(FULL_HASHER_BLOCK_SIZE, buffer2);

        assertThat(hasher.getBytesHashed()).isEqualTo(fileSize);
        assertThat(hasher.hashComplete()).isTrue();
    }

    // --- Requirement 3.4: SHA-512 + Ascii85 produces non-empty hash ---

    @Test
    void activeHasher_producesNonEmptyHash() throws NoSuchAlgorithmException {
        FullHasher hasher = createFullHasher(HashMode.hashAll);
        hasher.reset(10);

        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });
        buffer.flip();
        hasher.update(0, buffer);

        String hash = hasher.getHash();
        assertThat(hash).isNotNull();
        assertThat(hash).isNotEmpty();
        assertThat(hash).isNotEqualTo(NO_HASH);
    }

    // --- Requirement 3.1: reset clears per-file state ---

    @Test
    void reset_clearsBytesHashed() throws NoSuchAlgorithmException {
        FullHasher hasher = createFullHasher(HashMode.hashAll);
        hasher.reset(100);

        ByteBuffer buffer = ByteBuffer.allocate(100);
        hasher.update(0, buffer);
        assertThat(hasher.getBytesHashed()).isEqualTo(100);

        hasher.reset(200);
        assertThat(hasher.getBytesHashed()).isEqualTo(0);
        assertThat(hasher.hashComplete()).isFalse();
    }

    @Test
    void reset_preservesTotalBytesHashed() throws NoSuchAlgorithmException {
        FullHasher hasher = createFullHasher(HashMode.hashAll);

        // Hash first file (100 bytes)
        hasher.reset(100);
        ByteBuffer buffer1 = ByteBuffer.allocate(100);
        hasher.update(0, buffer1);
        assertThat(hasher.getTotalBytesHashed()).isEqualTo(100);

        // Hash second file (200 bytes)
        hasher.reset(200);
        ByteBuffer buffer2 = ByteBuffer.allocate(200);
        hasher.update(0, buffer2);
        assertThat(hasher.getTotalBytesHashed()).isEqualTo(300);
    }

    // --- Helper ---

    private FullHasher createFullHasher(HashMode hashMode) throws NoSuchAlgorithmException {
        Context context = new Context();
        context.setHashMode(hashMode);
        context.setUseDynamicScaling(false);
        return new FullHasher(context);
    }
}
