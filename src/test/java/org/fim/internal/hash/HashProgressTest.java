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

package org.fim.internal.hash;

import org.fim.tooling.StateAssert;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HashProgressTest extends StateAssert {
    private final HashProgress cut = new HashProgress(defaultContext());

    @Test
    public void canGetProgressChar() {
        assertThat(cut.getProgressChar(-1)).isEqualTo(' ');

        assertThat(cut.getProgressChar(0)).isEqualTo('.');

        assertThat(cut.getProgressChar(10 * 1024 * 1024)).isEqualTo('.');

        assertThat(cut.getProgressChar(20 * 1024 * 1024)).isEqualTo('o');

        assertThat(cut.getProgressChar(30 * 1024 * 1024)).isEqualTo('o');
    }
}
