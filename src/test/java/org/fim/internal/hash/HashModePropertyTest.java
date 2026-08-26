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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.Constants.NO_HASH;

/**
 * Property-based tests for HashMode hasher activation and inactive hasher invariants.
 * Feature: file-hashing
 * <p>
 * The input domain is every hash mode, so it is enumerated rather than sampled.
 */
public class HashModePropertyTest {

    private Context createContext(HashMode hashMode) {
        Context context = new Context();
        context.setHashMode(hashMode);
        context.setUseDynamicScaling(false);
        return context;
    }

    // Feature: file-hashing, Property 1: Hash mode determines hasher activation
    // **Validates: Requirements 1.3, 1.4, 1.5**
    @ParameterizedTest
    @EnumSource(HashMode.class)
    void hashModeDeterminesHasherActivation(HashMode mode) throws NoSuchAlgorithmException {
        Context context = createContext(mode);
        FrontHasher frontHasher = new FrontHasher(context);

        boolean smallActive = frontHasher.getSmallBlockHasher().isActive();
        boolean mediumActive = frontHasher.getMediumBlockHasher().isActive();
        boolean fullActive = frontHasher.getFullHasher().isActive();

        switch (mode) {
            case dontHash:
                assertThat(smallActive).isFalse();
                assertThat(mediumActive).isFalse();
                assertThat(fullActive).isFalse();
                break;
            case hashSmallBlock:
                assertThat(smallActive).isTrue();
                assertThat(mediumActive).isFalse();
                assertThat(fullActive).isFalse();
                break;
            case hashMediumBlock:
                assertThat(smallActive).isTrue();
                assertThat(mediumActive).isTrue();
                assertThat(fullActive).isFalse();
                break;
            case hashAll:
                assertThat(smallActive).isTrue();
                assertThat(mediumActive).isTrue();
                assertThat(fullActive).isTrue();
                break;
        }
    }

    // Feature: file-hashing, Property 2: Inactive hasher invariants
    // **Validates: Requirements 1.6, 3.5, 15.5**
    @ParameterizedTest
    @EnumSource(HashMode.class)
    void inactiveHasherInvariants(HashMode mode) throws NoSuchAlgorithmException {
        Context context = createContext(mode);
        FrontHasher frontHasher = new FrontHasher(context);

        Hasher[] hashers = {
            frontHasher.getSmallBlockHasher(),
            frontHasher.getMediumBlockHasher(),
            frontHasher.getFullHasher()
        };

        for (Hasher hasher : hashers) {
            if (!hasher.isActive()) {
                assertThat(hasher.getHash()).isEqualTo(NO_HASH);
                assertThat(hasher.getBytesHashed()).isEqualTo(0);
                assertThat(hasher.hashComplete()).isTrue();
            }
        }
    }
}
