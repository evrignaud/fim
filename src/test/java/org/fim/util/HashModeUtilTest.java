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

package org.fim.util;

import org.fim.model.HashMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.HashMode.dontHash;
import static org.fim.model.HashMode.hashAll;
import static org.fim.model.HashMode.hashMediumBlock;
import static org.fim.model.HashMode.hashSmallBlock;

public class HashModeUtilTest {
    @Test
    public void checkHashModeCompatibility() {
        assertCompatible(hashAll, hashAll);
        assertCompatible(hashAll, hashMediumBlock);
        assertCompatible(hashAll, hashSmallBlock);
        assertCompatible(hashAll, dontHash);

        assertNotCompatible(hashMediumBlock, hashAll);
        assertCompatible(hashMediumBlock, hashMediumBlock);
        assertCompatible(hashMediumBlock, hashSmallBlock);
        assertCompatible(hashMediumBlock, dontHash);

        assertNotCompatible(hashSmallBlock, hashAll);
        assertNotCompatible(hashSmallBlock, hashMediumBlock);
        assertCompatible(hashSmallBlock, hashSmallBlock);
        assertCompatible(hashSmallBlock, dontHash);

        assertNotCompatible(dontHash, hashAll);
        assertNotCompatible(dontHash, hashMediumBlock);
        assertNotCompatible(dontHash, hashSmallBlock);
        assertCompatible(dontHash, dontHash);
    }

    // Requirement 2.1: hashAll is compatible with hashSmallBlock, hashMediumBlock, and hashAll
    @Test
    public void hashAllIsCompatibleWithAllModes() {
        for (HashMode mode : HashMode.values()) {
            assertCompatible(hashAll, mode);
        }
    }

    // Requirement 2.2: hashMediumBlock is compatible with hashSmallBlock and hashMediumBlock, incompatible with hashAll
    @Test
    public void hashMediumBlockCompatibility() {
        assertCompatible(hashMediumBlock, hashSmallBlock);
        assertCompatible(hashMediumBlock, hashMediumBlock);
        assertCompatible(hashMediumBlock, dontHash);
        assertNotCompatible(hashMediumBlock, hashAll);
    }

    // Requirement 2.3: hashSmallBlock is compatible with hashSmallBlock only (and dontHash), incompatible with hashMediumBlock and hashAll
    @Test
    public void hashSmallBlockCompatibility() {
        assertCompatible(hashSmallBlock, hashSmallBlock);
        assertCompatible(hashSmallBlock, dontHash);
        assertNotCompatible(hashSmallBlock, hashMediumBlock);
        assertNotCompatible(hashSmallBlock, hashAll);
    }

    // Requirement 2.4: dontHash is compatible with dontHash only
    @Test
    public void dontHashCompatibility() {
        assertCompatible(dontHash, dontHash);
        assertNotCompatible(dontHash, hashSmallBlock);
        assertNotCompatible(dontHash, hashMediumBlock);
        assertNotCompatible(dontHash, hashAll);
    }

    // Requirement 2.5: Compatibility is transitive downward —
    // for any A, B where A.ordinal() >= B.ordinal(), if isCompatible(B, X) then isCompatible(A, X)
    @Test
    public void compatibilityIsTransitiveDownward() {
        HashMode[] modes = HashMode.values();
        for (HashMode a : modes) {
            for (HashMode b : modes) {
                if (a.ordinal() >= b.ordinal()) {
                    for (HashMode x : modes) {
                        if (HashModeUtil.isCompatible(b, x)) {
                            assertThat(HashModeUtil.isCompatible(a, x))
                                .as("Transitivity violated: isCompatible(%s, %s) should be true because isCompatible(%s, %s) is true and %s.ordinal() >= %s.ordinal()",
                                    a, x, b, x, a, b)
                                .isTrue();
                        }
                    }
                }
            }
        }
    }

    // Verify that every mode is self-compatible
    @Test
    public void everyModeIsCompatibleWithItself() {
        for (HashMode mode : HashMode.values()) {
            assertCompatible(mode, mode);
        }
    }

    private void assertCompatible(HashMode hashMode, HashMode toCheck) {
        assertThat(HashModeUtil.isCompatible(hashMode, toCheck)).isTrue();
    }

    private void assertNotCompatible(HashMode hashMode, HashMode toCheck) {
        assertThat(HashModeUtil.isCompatible(hashMode, toCheck)).isFalse();
    }
}
