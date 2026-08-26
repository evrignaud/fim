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

package org.fim.model;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import org.fim.tooling.ObjectAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FileHashTest {
    private FileHash a1;
    private FileHash a2;
    private FileHash b;
    private FileHash c;

    @BeforeEach
    public void setUp() {
        a1 = new FileHash("hash_1", "hash_2", "hash_3");
        a2 = new FileHash("hash_1", "hash_2", "hash_3");

        b = new FileHash("hash_1", "hash_2", "hash_4");

        c = new FileHash("hash_3", "hash_4", "hash_5");
    }

    @Test
    public void equalsIsWorking() {
        ObjectAssert.equalsIsWorking(a1, a2, b);
    }

    @Test
    public void hashcodeIsWorking() {
        ObjectAssert.hashcodeIsWorking(a1, a2, b);
    }

    @Test
    public void compareIsWorking() {
        ObjectAssert.compareIsWorking(a1, a2, b, c);
    }

    @Test
    public void hasExactlyThreeFields() {
        FileHash fh = new FileHash("s", "m", "f");
        assertThat(fh.getSmallBlockHash()).isEqualTo("s");
        assertThat(fh.getMediumBlockHash()).isEqualTo("m");
        assertThat(fh.getFullHash()).isEqualTo("f");
    }

    @Test
    public void lexicographicOrderingBySmallBlockHashFirst() {
        FileHash lower = new FileHash("aaa", "zzz", "zzz");
        FileHash higher = new FileHash("bbb", "aaa", "aaa");
        assertThat(lower.compareTo(higher)).isLessThan(0);
        assertThat(higher.compareTo(lower)).isGreaterThan(0);
    }

    @Test
    public void lexicographicOrderingByMediumBlockHashSecond() {
        FileHash lower = new FileHash("same", "aaa", "zzz");
        FileHash higher = new FileHash("same", "bbb", "aaa");
        assertThat(lower.compareTo(higher)).isLessThan(0);
        assertThat(higher.compareTo(lower)).isGreaterThan(0);
    }

    @Test
    public void lexicographicOrderingByFullHashThird() {
        FileHash lower = new FileHash("same", "same", "aaa");
        FileHash higher = new FileHash("same", "same", "bbb");
        assertThat(lower.compareTo(higher)).isLessThan(0);
        assertThat(higher.compareTo(lower)).isGreaterThan(0);
    }

    @Test
    public void compareToReturnsZeroForEqualHashes() {
        assertThat(a1.compareTo(a2)).isEqualTo(0);
    }

    @Test
    public void cloneProducesEqualButDistinctObject() {
        FileHash original = new FileHash("s1", "m1", "f1");
        FileHash cloned = original.clone();

        assertThat(cloned).isEqualTo(original);
        assertThat(cloned.hashCode()).isEqualTo(original.hashCode());
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    public void clonePreservesAllFields() {
        FileHash original = new FileHash("small", "medium", "full");
        FileHash cloned = original.clone();

        assertThat(cloned.getSmallBlockHash()).isEqualTo("small");
        assertThat(cloned.getMediumBlockHash()).isEqualTo("medium");
        assertThat(cloned.getFullHash()).isEqualTo("full");
    }

    @Test
    public void stringInterningOnConstruction() {
        // Create a new string that is NOT already interned
        String s = new String("intern_test_small");
        String m = new String("intern_test_medium");
        String f = new String("intern_test_full");

        FileHash fh = new FileHash(s, m, f);

        // Stored strings should be reference-equal to their intern() result
        assertThat(fh.getSmallBlockHash()).isSameAs(fh.getSmallBlockHash().intern());
        assertThat(fh.getMediumBlockHash()).isSameAs(fh.getMediumBlockHash().intern());
        assertThat(fh.getFullHash()).isSameAs(fh.getFullHash().intern());
    }

    @Test
    public void stringInterningOnSetters() {
        FileHash fh = new FileHash("a", "b", "c");

        String newSmall = new String("setter_small");
        String newMedium = new String("setter_medium");
        String newFull = new String("setter_full");

        fh.setSmallBlockHash(newSmall);
        fh.setMediumBlockHash(newMedium);
        fh.setFullHash(newFull);

        assertThat(fh.getSmallBlockHash()).isSameAs(fh.getSmallBlockHash().intern());
        assertThat(fh.getMediumBlockHash()).isSameAs(fh.getMediumBlockHash().intern());
        assertThat(fh.getFullHash()).isSameAs(fh.getFullHash().intern());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void hashableImplementationContributesAllThreeFields() {
        FileHash fh = new FileHash("s_hash", "m_hash", "f_hash");
        com.google.common.hash.Hasher hasher = Hashing.sha256().newHasher();
        fh.hashObject(hasher);

        // Build expected hash with the same sequence
        com.google.common.hash.Hasher expected = Hashing.sha256().newHasher();
        expected.putString("FileHash", Charsets.UTF_8)
                .putChar(Hashable.HASH_FIELD_SEPARATOR)
                .putString("s_hash", Charsets.UTF_8)
                .putChar(Hashable.HASH_FIELD_SEPARATOR)
                .putString("m_hash", Charsets.UTF_8)
                .putChar(Hashable.HASH_FIELD_SEPARATOR)
                .putString("f_hash", Charsets.UTF_8);

        assertThat(hasher.hash()).isEqualTo(expected.hash());
    }

    @Test
    public void copyConstructorProducesEqualObject() {
        FileHash original = new FileHash("x", "y", "z");
        FileHash copy = new FileHash(original);

        assertThat(copy).isEqualTo(original);
        assertThat(copy).isNotSameAs(original);
    }
}
