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

public class DuplicatedFilesTest {
    private DuplicatedFiles a1;
    private DuplicatedFiles a2;
    private DuplicatedFiles b;

    @BeforeEach
    public void setUp() {
        a1 = new DuplicatedFiles(Arrays.asList("file_1", "file_2"));
        a2 = new DuplicatedFiles(Arrays.asList("file_1", "file_2"));

        b = new DuplicatedFiles(Arrays.asList("file_1", "file_3"));
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
    public void toStringIsWorking() {
        assertThat(a1.toString().contains("duplicates")).isTrue();
    }
}
