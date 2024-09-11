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

import org.fim.model.HashMode;
import org.fim.model.Range;
import org.fim.tooling.BuildableContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.tooling.TestConstants.SIZE_12_KB;
import static org.fim.tooling.TestConstants.SIZE_16_KB;
import static org.fim.tooling.TestConstants.SIZE_1_KB;
import static org.fim.tooling.TestConstants.SIZE_20_KB;
import static org.fim.tooling.TestConstants.SIZE_4_KB;
import static org.fim.tooling.TestConstants.SIZE_5_KB;
import static org.fim.tooling.TestConstants.SIZE_8_KB;

public class BlockHasherTest {
    private BlockHasher cut;

    @BeforeEach
    public void setUp() throws NoSuchAlgorithmException {
        BuildableContext context = new BuildableContext().hashSmallBlock();
        cut = new BlockHasher(context) {
            @Override
            protected int getBlockSize() {
                return SIZE_4_KB;
            }

            @Override
            protected boolean isCompatible(HashMode hashMode) {
                return true;
            }
        };
    }

    @Test
    public void calculateBlockIndex() {
        cut.reset(31);
        assertThat(cut.getMiddleBlockIndex()).isEqualTo(0);

        cut.reset(SIZE_4_KB + 109);
        assertThat(cut.getMiddleBlockIndex()).isEqualTo(0);

        cut.reset(SIZE_8_KB + 205);
        assertThat(cut.getMiddleBlockIndex()).isEqualTo(1);

        cut.reset(SIZE_12_KB + 301);
        assertThat(cut.getMiddleBlockIndex()).isEqualTo(1);
        assertThat(cut.getEndBlockIndex()).isEqualTo(2);

        cut.reset(SIZE_16_KB + 407);
        assertThat(cut.getMiddleBlockIndex()).isEqualTo(2);
        assertThat(cut.getEndBlockIndex()).isEqualTo(3);

        cut.reset(SIZE_20_KB + 509);
        assertThat(cut.getMiddleBlockIndex()).isEqualTo(2);
        assertThat(cut.getEndBlockIndex()).isEqualTo(4);
    }

    @Test
    public void rangesAreCalculatedCorrectly() {
        cut.reset(31);
        assertThat(cut.getRanges()).isEqualTo(new Range[] { new Range(0, 31) });

        cut.reset(SIZE_4_KB + 109);
        assertThat(cut.getRanges()).isEqualTo(new Range[] { new Range(0, SIZE_4_KB) });

        cut.reset(SIZE_8_KB + 205);
        assertThat(cut.getRanges()).isEqualTo(new Range[] { new Range(SIZE_4_KB, SIZE_8_KB) });

        cut.reset(SIZE_12_KB + 301);
        assertThat(cut.getRanges()).isEqualTo(new Range[] { new Range(SIZE_4_KB, SIZE_8_KB), new Range(SIZE_8_KB, SIZE_12_KB) });

        cut.reset(SIZE_16_KB + 407);
        assertThat(cut.getRanges()).isEqualTo(new Range[] { new Range(SIZE_4_KB, SIZE_8_KB), new Range(SIZE_8_KB, SIZE_12_KB), new Range(SIZE_12_KB,
                SIZE_16_KB) });

        cut.reset(SIZE_20_KB + 509);
        assertThat(cut.getRanges()).isEqualTo(new Range[] { new Range(SIZE_4_KB, SIZE_8_KB), new Range(SIZE_8_KB, SIZE_12_KB), new Range(SIZE_16_KB,
                SIZE_20_KB) });
    }

    @Test
    public void canRetrieveTheNextRange() {
        cut.reset(SIZE_20_KB + 509);
        assertThat(cut.getNextRange(0)).isEqualTo(new Range(SIZE_4_KB, SIZE_8_KB));

        assertThat(cut.getNextRange(SIZE_1_KB)).isEqualTo(new Range(SIZE_4_KB, SIZE_8_KB));

        assertThat(cut.getNextRange(SIZE_4_KB)).isEqualTo(new Range(SIZE_4_KB, SIZE_8_KB));

        assertThat(cut.getNextRange(SIZE_4_KB + 1)).isEqualTo(new Range(SIZE_8_KB, SIZE_12_KB));

        assertThat(cut.getNextRange(SIZE_5_KB)).isEqualTo(new Range(SIZE_8_KB, SIZE_12_KB));

        assertThat(cut.getNextRange(SIZE_12_KB)).isEqualTo(new Range(SIZE_16_KB, SIZE_20_KB));

        assertThat(cut.getNextRange(SIZE_20_KB)).isEqualTo(null);
    }
}
