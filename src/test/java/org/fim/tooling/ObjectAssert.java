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

package org.fim.tooling;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectAssert {
    public static void equalsIsWorking(Object a1, Object a2, Object b) {
        assertThat(a1).isNotEqualTo(null);

        assertThat(a1).isNotEqualTo("dummy_string");

        assertThat(a1).isEqualTo(a2);
        assertThat(a2).isEqualTo(a1);

        assertThat(a1).isNotEqualTo(b);
        assertThat(b).isNotEqualTo(a1);
    }

    public static void hashcodeIsWorking(Object a1, Object a2, Object b) {
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());

        assertThat(a1.hashCode()).isNotEqualTo(b.hashCode());
    }

    public static <T extends Comparable<T>> void compareIsWorking(T a1, T a2, T b, T c) {
        assertThat(a1.compareTo(a2)).isEqualTo(0);
        assertThat(a2.compareTo(a1)).isEqualTo(0);

        assertThat(a1.compareTo(b)).isLessThan(0);
        assertThat(b.compareTo(c)).isLessThan(0);
        assertThat(a1.compareTo(c)).isLessThan(0);

        assertThat(c.compareTo(b)).isGreaterThan(0);
        assertThat(b.compareTo(a1)).isGreaterThan(0);
        assertThat(c.compareTo(a1)).isGreaterThan(0);
    }
}
