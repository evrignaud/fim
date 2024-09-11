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
}
