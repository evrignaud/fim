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
import org.fim.tooling.RandomInputs;
import org.fim.util.Ascii85Util;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for SHA-512/Ascii85 hash oracle and hasher reset cumulative totals.
 * Feature: file-hashing
 */
public class AbstractHasherPropertyTest {

    private static final int MAX_DATA_LENGTH = 4096;

    private Context createContext() {
        Context context = new Context();
        context.setHashMode(HashMode.hashAll);
        context.setUseDynamicScaling(false);
        return context;
    }

    /**
     * Data blocks of 1 to 4096 bytes. Neither the length nor the content can be enumerated, so the two
     * boundary lengths come first and the rest is sampled from a fixed seed.
     * <p>
     * The blocks are kept out of the test display name: dumping a 4 KB array into the report helps
     * nobody, and with a fixed seed the case index identifies the input on its own.
     */
    static Stream<byte[]> byteArrays() {
        List<byte[]> values = new ArrayList<>();
        Random random = RandomInputs.random();
        values.add(randomBytes(random, 1));
        values.add(randomBytes(random, MAX_DATA_LENGTH));
        for (int i = 0; i < 100; i++) {
            values.add(randomBytes(random, RandomInputs.intBetween(random, 1, MAX_DATA_LENGTH)));
        }
        return values.stream();
    }

    private static byte[] randomBytes(Random random, int length) {
        byte[] data = new byte[length];
        random.nextBytes(data);
        return data;
    }

    /** Small enough to enumerate: every file count of the original range. */
    static IntStream fileCounts() {
        return IntStream.rangeClosed(2, 5);
    }

    // Feature: file-hashing, Property 8: Hash output matches SHA-512/Ascii85 oracle
    // **Validates: Requirements 3.4, 4.6, 5.6, 14.2**
    @ParameterizedTest(name = "[{index}]")
    @MethodSource("byteArrays")
    void hashOutputMatchesSha512Ascii85Oracle(byte[] data) throws NoSuchAlgorithmException {
        // Compute expected hash using independent SHA-512 + Ascii85
        MessageDigest oracle = MessageDigest.getInstance("SHA-512");
        oracle.update(data);
        byte[] expectedDigest = oracle.digest();
        String expectedHash = Ascii85Util.encode(expectedDigest);

        // Use FullHasher to hash the same data (it hashes the entire content)
        Context context = createContext();
        FullHasher hasher = new FullHasher(context);
        hasher.reset(data.length);

        ByteBuffer buffer = ByteBuffer.wrap(data);
        hasher.update(0, buffer);

        String actualHash = hasher.getHash();
        assertThat(actualHash).isEqualTo(expectedHash);
    }

    // Feature: file-hashing, Property 17: Hasher reset preserves cumulative totals
    // **Validates: Requirements 14.3, 14.4**
    @ParameterizedTest
    @MethodSource("fileCounts")
    void hasherResetPreservesCumulativeTotals(int fileCount) throws NoSuchAlgorithmException {
        Context context = createContext();
        FullHasher hasher = new FullHasher(context);

        long cumulativeTotal = 0;

        for (int i = 0; i < fileCount; i++) {
            int fileSize = 1024 + (i * 512); // varying file sizes
            hasher.reset(fileSize);

            // After reset, per-file bytes hashed should be 0
            assertThat(hasher.getBytesHashed()).isEqualTo(0);

            // Verify cumulative total equals sum of previous files
            assertThat(hasher.getTotalBytesHashed()).isEqualTo(cumulativeTotal);

            // Feed data for this file
            ByteBuffer buffer = ByteBuffer.allocate(fileSize);
            hasher.update(0, buffer);

            cumulativeTotal += fileSize;
        }

        // After all files, total should equal cumulative
        assertThat(hasher.getTotalBytesHashed()).isEqualTo(cumulativeTotal);
    }
}
