/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2016  Etienne Vrignaud
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
 * along with Fim.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.fim.model;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FileTimeTest {
    private FileTime a1;
    private FileTime a2;
    private FileTime b;
    private FileTime c;

    @Before
    public void setup() {
        a1 = new FileTime(1L, 2L);
        a2 = new FileTime(1L, 2L);

        b = new FileTime(2L, 3L);

        c = new FileTime(4L, 5L);
    }

    @Test
    public void equalsIsWorking() {
        assertThat(a1).isNotEqualTo(null);

        assertThat(a1).isNotEqualTo("dummy_string");

        assertThat(a1).isEqualTo(a2);
        assertThat(a2).isEqualTo(a1);

        assertThat(a1).isNotEqualTo(b);
        assertThat(b).isNotEqualTo(a1);
    }

    @Test
    public void hashcodeIsWorking() {
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());

        assertThat(a1.hashCode()).isNotEqualTo(b.hashCode());
    }

    @Test
    public void compareIsWorking() {
        assertThat(a1.compareTo(a2)).isEqualTo(0);
        assertThat(a2.compareTo(a1)).isEqualTo(0);

        assertThat(a1.compareTo(b)).isEqualTo(-1);
        assertThat(b.compareTo(c)).isEqualTo(-1);
        assertThat(a1.compareTo(c)).isEqualTo(-1);

        assertThat(c.compareTo(b)).isEqualTo(1);
        assertThat(b.compareTo(a1)).isEqualTo(1);
        assertThat(c.compareTo(a1)).isEqualTo(1);
    }
}
