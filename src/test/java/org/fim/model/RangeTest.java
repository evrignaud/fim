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

import org.fim.command.exception.FimInternalError;
import org.fim.tooling.ObjectAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RangeTest {
    private Range a1;
    private Range a2;
    private Range b;
    private Range c;

    @BeforeEach
    public void setUp() {
        a1 = new Range(1, 2);
        a2 = new Range(1, 2);

        b = new Range(1, 3);

        c = new Range(4, 5);
    }

    @Test
    public void toMustBeGreaterThanFrom() {
        assertThrows(FimInternalError.class, () -> {
            new Range(4, 3);
        });
    }

    @Test
    public void emptyRangeIsAllowed() {
        Range range = new Range(4, 4);
        assertThat(range.getFrom()).isEqualTo(range.getTo());
    }

    @Test
    public void unionOfTwoRange() {
        Range range1 = new Range(4, 10);
        Range range2 = new Range(7, 15);

        assertThat(range1.union(null)).isEqualTo(new Range(4, 10));

        assertThat(range1.union(range2)).isEqualTo(new Range(4, 15));
        assertThat(range2.union(range1)).isEqualTo(new Range(4, 15));

        range1 = new Range(4, 9);
        range2 = new Range(12, 15);
        assertThat(range1.union(range2)).isEqualTo(new Range(4, 15));
    }

    @Test
    public void adjustingRanges() {
        Range range1 = new Range(4, 10);
        Range range2 = new Range(7, 15);
        assertThat(range1.adjustToRange(range2)).isEqualTo(new Range(4, 15));

        range1 = new Range(4, 9);
        range2 = new Range(12, 15);
        assertThat(range1.adjustToRange(range2)).isEqualTo(new Range(4, 9));
    }

    @Test
    public void otherRangeCannotStartBefore() {
        Range range1 = new Range(4, 10);
        Range range2 = new Range(3, 15);
        assertThrows(FimInternalError.class, () -> {
            range1.adjustToRange(range2);
        });
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
    public void toStringIsWorking() {
        assertThat(a1.toString().contains("from")).isTrue();
    }
}
