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

import com.blackducksoftware.tools.commonframework.core.encoding.Ascii85Encoder;
import org.fim.model.Context;
import org.fim.model.HashMode;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.Constants.NO_HASH;

/**
 * Unit tests for AbstractHasher verifying SHA-512 algorithm usage, Ascii85 encoding,
 * and reset behavior (digest reset, per-file counter reset, cumulative total preservation).
 *
 * Uses FullHasher as a concrete subclass since AbstractHasher is abstract.
 *
 * Validates: Requirements 14.1, 14.2, 14.3, 14.4
 */
class AbstractHasherTest {

    // --- Requirement 14.1: SHA-512 algorithm constant ---

    @Test
    void hashAlgorithm_isSHA512() {
        assertThat(AbstractHasher.HASH_ALGORITHM).isEqualTo("SHA-512");
    }

    // --- Requirement 14.2: Hash output matches independent SHA-512/Ascii85 computation ---

    @Test
    void hashOutput_matchesKnownSHA512Ascii85_emptyInput() throws Exception {
        FullHasher hasher = createActiveHasher();
        hasher.reset(0);

        // Feed zero bytes — getHash() calls digest.digest() which returns SHA-512 of empty input
        String actualHash = hasher.getHash();
        String expectedHash = computeExpectedHash(new byte[0]);

        assertThat(actualHash).isEqualTo(expectedHash);
    }

    @Test
    void hashOutput_matchesKnownSHA512Ascii85_singleByte() throws Exception {
        byte[] data = new byte[]{0x42};
        FullHasher hasher = createActiveHasher();
        hasher.reset(data.length);

        ByteBuffer buffer = ByteBuffer.wrap(data);
        hasher.update(0, buffer);

        String actualHash = hasher.getHash();
        String expectedHash = computeExpectedHash(data);

        assertThat(actualHash).isEqualTo(expectedHash);
    }

    @Test
    void hashOutput_matchesKnownSHA512Ascii85_helloWorld() throws Exception {
        byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        FullHasher hasher = createActiveHasher();
        hasher.reset(data.length);

        ByteBuffer buffer = ByteBuffer.wrap(data);
        hasher.update(0, buffer);

        String actualHash = hasher.getHash();
        String expectedHash = computeExpectedHash(data);

        assertThat(actualHash).isEqualTo(expectedHash);
    }

    @Test
    void hashOutput_matchesKnownSHA512Ascii85_allZeros() throws Exception {
        byte[] data = new byte[256];
        FullHasher hasher = createActiveHasher();
        hasher.reset(data.length);

        ByteBuffer buffer = ByteBuffer.wrap(data);
        hasher.update(0, buffer);

        String actualHash = hasher.getHash();
        String expectedHash = computeExpectedHash(data);

        assertThat(actualHash).isEqualTo(expectedHash);
    }

    @Test
    void hashOutput_matchesKnownSHA512Ascii85_sequentialBytes() throws Exception {
        byte[] data = new byte[64];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        FullHasher hasher = createActiveHasher();
        hasher.reset(data.length);

        ByteBuffer buffer = ByteBuffer.wrap(data);
        hasher.update(0, buffer);

        String actualHash = hasher.getHash();
        String expectedHash = computeExpectedHash(data);

        assertThat(actualHash).isEqualTo(expectedHash);
    }

    @Test
    void hashOutput_multipleUpdates_matchesConcatenatedInput() throws Exception {
        byte[] part1 = "Hello, ".getBytes(StandardCharsets.UTF_8);
        byte[] part2 = "World!".getBytes(StandardCharsets.UTF_8);
        byte[] combined = "Hello, World!".getBytes(StandardCharsets.UTF_8);

        FullHasher hasher = createActiveHasher();
        hasher.reset(combined.length);

        hasher.update(0, ByteBuffer.wrap(part1));
        hasher.update(part1.length, ByteBuffer.wrap(part2));

        String actualHash = hasher.getHash();
        String expectedHash = computeExpectedHash(combined);

        assertThat(actualHash).isEqualTo(expectedHash);
    }

    // --- Requirement 14.3: reset() resets digest and per-file counter, preserves cumulative total ---

    @Test
    void reset_resetsDigestState_producesCorrectHashForNewFile() throws Exception {
        byte[] file1Data = "first file".getBytes(StandardCharsets.UTF_8);
        byte[] file2Data = "second file".getBytes(StandardCharsets.UTF_8);

        FullHasher hasher = createActiveHasher();

        // Hash first file
        hasher.reset(file1Data.length);
        hasher.update(0, ByteBuffer.wrap(file1Data));
        hasher.getHash(); // finalize first file

        // Hash second file — digest should be reset
        hasher.reset(file2Data.length);
        hasher.update(0, ByteBuffer.wrap(file2Data));

        String actualHash = hasher.getHash();
        String expectedHash = computeExpectedHash(file2Data);

        assertThat(actualHash)
                .as("After reset, hash should reflect only the second file's content")
                .isEqualTo(expectedHash);
    }

    @Test
    void reset_resetsPerFileCounter() throws Exception {
        byte[] data = new byte[100];
        FullHasher hasher = createActiveHasher();

        hasher.reset(data.length);
        hasher.update(0, ByteBuffer.wrap(data));
        assertThat(hasher.getBytesHashed()).isEqualTo(100);

        hasher.reset(50);
        assertThat(hasher.getBytesHashed()).isEqualTo(0);
    }

    // --- Requirement 14.4: cumulative total bytes hashed across files ---

    @Test
    void reset_preservesCumulativeTotal() throws Exception {
        FullHasher hasher = createActiveHasher();

        // First file: 100 bytes
        hasher.reset(100);
        hasher.update(0, ByteBuffer.allocate(100));
        assertThat(hasher.getTotalBytesHashed()).isEqualTo(100);

        // Second file: 200 bytes
        hasher.reset(200);
        hasher.update(0, ByteBuffer.allocate(200));
        assertThat(hasher.getTotalBytesHashed()).isEqualTo(300);

        // Third file: 50 bytes
        hasher.reset(50);
        hasher.update(0, ByteBuffer.allocate(50));
        assertThat(hasher.getTotalBytesHashed()).isEqualTo(350);
    }

    @Test
    void reset_perFileCounterResets_whileCumulativeGrows() throws Exception {
        FullHasher hasher = createActiveHasher();

        hasher.reset(80);
        hasher.update(0, ByteBuffer.allocate(80));
        assertThat(hasher.getBytesHashed()).isEqualTo(80);
        assertThat(hasher.getTotalBytesHashed()).isEqualTo(80);

        hasher.reset(120);
        assertThat(hasher.getBytesHashed()).isEqualTo(0);
        assertThat(hasher.getTotalBytesHashed()).isEqualTo(80);

        hasher.update(0, ByteBuffer.allocate(120));
        assertThat(hasher.getBytesHashed()).isEqualTo(120);
        assertThat(hasher.getTotalBytesHashed()).isEqualTo(200);
    }

    // --- Inactive hasher returns NO_HASH ---

    @Test
    void inactiveHasher_returnsNoHash() throws Exception {
        FullHasher hasher = createInactiveHasher();

        assertThat(hasher.isActive()).isFalse();
        assertThat(hasher.getHash()).isEqualTo(NO_HASH);
        assertThat(hasher.getBytesHashed()).isEqualTo(0);
    }

    @Test
    void inactiveHasher_updateDoesNothing() throws Exception {
        FullHasher hasher = createInactiveHasher();

        hasher.reset(100);
        hasher.update(0, ByteBuffer.allocate(100));

        assertThat(hasher.getBytesHashed()).isEqualTo(0);
        assertThat(hasher.getTotalBytesHashed()).isEqualTo(0);
        assertThat(hasher.getHash()).isEqualTo(NO_HASH);
    }

    // --- Helpers ---

    private FullHasher createActiveHasher() throws NoSuchAlgorithmException {
        Context context = new Context();
        context.setHashMode(HashMode.hashAll);
        context.setUseDynamicScaling(false);
        return new FullHasher(context);
    }

    private FullHasher createInactiveHasher() throws NoSuchAlgorithmException {
        Context context = new Context();
        context.setHashMode(HashMode.dontHash);
        context.setUseDynamicScaling(false);
        return new FullHasher(context);
    }

    /**
     * Independently computes SHA-512 hash and Ascii85-encodes it,
     * matching the expected behavior of AbstractHasher.
     */
    private String computeExpectedHash(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        digest.update(data);
        byte[] hashBytes = digest.digest();
        return new String(Ascii85Encoder.encode(hashBytes), StandardCharsets.UTF_8);
    }
}
