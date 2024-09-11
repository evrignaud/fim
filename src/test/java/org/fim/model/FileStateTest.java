/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2017  Etienne Vrignaud
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

import org.fim.tooling.ObjectAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class FileStateTest {
    private FileState a1;
    private FileState a2;
    private FileState b;

    @BeforeEach
    public void setUp() {
        a1 = new FileState("file_1", 1L, new FileTime(1_000L), new FileHash("1", "11", "111"),
                Arrays.asList(new Attribute("n1", "v1"), new Attribute("n2", "v2"), new Attribute("n3", "v3")));
        a2 = new FileState("file_1", 1L, new FileTime(1_000L), new FileHash("1", "11", "111"),
                Arrays.asList(new Attribute("n3", "v3"), new Attribute("n2", "v2"), new Attribute("n1", "v1")));

        b = new FileState("file_2", 2L, new FileTime(2_000L), new FileHash("2", "22", "222"),
                Arrays.asList(new Attribute("n1", "v1"), new Attribute("n2", "v2"), new Attribute("n3", "v3")));
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
    public void longHashcodeIsWorking() {
        assertThat(a1.longHashCode()).isEqualTo(a2.longHashCode());

        assertThat(a1.longHashCode()).isNotEqualTo(b.longHashCode());
    }

    @Test
    public void allTheFileTimesAreTakenInAccount() {
        a1 = new FileState("file_1", 1L, new FileTime(10_000L, 20_000L), new FileHash("1", "11", "111"), null);
        a2 = new FileState("file_1", 1L, new FileTime(10_000L, 20_000L), new FileHash("1", "11", "111"), null);
        assertThat(a1).isEqualTo(a2);
        assertThat(a1.longHashCode()).isEqualTo(a2.longHashCode());
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());

        a2.getFileTime().setCreationTime(11_000L);
        assertThat(a1).isNotEqualTo(a2);
        assertThat(a1.longHashCode()).isNotEqualTo(a2.longHashCode());
        assertThat(a1.hashCode()).isNotEqualTo(a2.hashCode());

        a2.getFileTime().setCreationTime(10_000L);
        a2.getFileTime().setLastModified(21_000L);
        assertThat(a1).isNotEqualTo(a2);
        assertThat(a1.longHashCode()).isNotEqualTo(a2.longHashCode());
        assertThat(a1.hashCode()).isNotEqualTo(a2.hashCode());
    }

    @Test
    public void millisecondsIgnoredInSomeCases() {
        a1 = new FileState("file_1", 1L, new FileTime(10_000L, 20_000L), new FileHash("1", "11", "111"), null);
        a2 = new FileState("file_1", 1L, new FileTime(10_000L, 20_000L), new FileHash("1", "11", "111"), null);
        assertThat(a1).isEqualTo(a2);
        assertThat(a1.longHashCode()).isEqualTo(a2.longHashCode());
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());

        a2.getFileTime().setCreationTime(10_001L);
        assertThat(a1).isEqualTo(a2);
        assertThat(a1.longHashCode()).isEqualTo(a2.longHashCode());
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());

        a2.getFileTime().setCreationTime(10_000L);
        a2.getFileTime().setLastModified(20_001L);
        assertThat(a1).isEqualTo(a2);
        assertThat(a1.longHashCode()).isEqualTo(a2.longHashCode());
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());
    }
}
