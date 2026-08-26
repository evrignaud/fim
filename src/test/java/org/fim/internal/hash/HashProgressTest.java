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

package org.fim.internal.hash;

import org.fim.tooling.BuildableContext;
import org.fim.tooling.StateAssert;
import org.fim.util.Logger;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

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

    // --- Tests for all decimal character thresholds (Req 11.3) ---

    @Test
    public void getProgressChar_belowAllThresholds_returnsDot() {
        assertThat(cut.getProgressChar(0)).isEqualTo('.');
        assertThat(cut.getProgressChar(1)).isEqualTo('.');
        assertThat(cut.getProgressChar(19_999_999)).isEqualTo('.');
    }

    @Test
    public void getProgressChar_atAndAbove20M_returnsSmallO() {
        assertThat(cut.getProgressChar(20_000_000)).isEqualTo('o');
        assertThat(cut.getProgressChar(20_000_001)).isEqualTo('o');
        assertThat(cut.getProgressChar(49_999_999)).isEqualTo('o');
    }

    @Test
    public void getProgressChar_atAndAbove50M_returnsEight() {
        assertThat(cut.getProgressChar(50_000_000)).isEqualTo('8');
        assertThat(cut.getProgressChar(50_000_001)).isEqualTo('8');
        assertThat(cut.getProgressChar(99_999_999)).isEqualTo('8');
    }

    @Test
    public void getProgressChar_atAndAbove100M_returnsBigO() {
        assertThat(cut.getProgressChar(100_000_000)).isEqualTo('O');
        assertThat(cut.getProgressChar(100_000_001)).isEqualTo('O');
        assertThat(cut.getProgressChar(199_999_999)).isEqualTo('O');
    }

    @Test
    public void getProgressChar_atAndAbove200M_returnsAt() {
        assertThat(cut.getProgressChar(200_000_000)).isEqualTo('@');
        assertThat(cut.getProgressChar(200_000_001)).isEqualTo('@');
        assertThat(cut.getProgressChar(999_999_999)).isEqualTo('@');
    }

    @Test
    public void getProgressChar_atAndAbove1000M_returnsHash() {
        assertThat(cut.getProgressChar(1_000_000_000)).isEqualTo('#');
        assertThat(cut.getProgressChar(1_000_000_001)).isEqualTo('#');
        assertThat(cut.getProgressChar(2_000_000_000L)).isEqualTo('#');
    }

    @Test
    public void getProgressChar_negativeSize_returnsSpace() {
        assertThat(cut.getProgressChar(-1)).isEqualTo(' ');
        assertThat(cut.getProgressChar(-100)).isEqualTo(' ');
    }

    // --- Tests for isProgressDisplayed() (Req 11.1, 11.5) ---

    @Test
    public void isProgressDisplayed_verboseAndHashAll_returnsTrue() {
        BuildableContext ctx = defaultContext(); // verbose=true, hashMode=hashAll by default
        HashProgress hp = new HashProgress(ctx);
        assertThat(hp.isProgressDisplayed()).isTrue();
    }

    @Test
    public void isProgressDisplayed_verboseAndHashSmallBlock_returnsTrue() {
        BuildableContext ctx = defaultContext().hashSmallBlock();
        HashProgress hp = new HashProgress(ctx);
        assertThat(hp.isProgressDisplayed()).isTrue();
    }

    @Test
    public void isProgressDisplayed_verboseAndHashMediumBlock_returnsTrue() {
        BuildableContext ctx = defaultContext().hashMediumBlock();
        HashProgress hp = new HashProgress(ctx);
        assertThat(hp.isProgressDisplayed()).isTrue();
    }

    @Test
    public void isProgressDisplayed_verboseAndDontHash_returnsFalse() {
        BuildableContext ctx = defaultContext().dontHashFiles();
        HashProgress hp = new HashProgress(ctx);
        assertThat(hp.isProgressDisplayed()).isFalse();
    }

    @Test
    public void isProgressDisplayed_notVerboseAndHashAll_returnsFalse() {
        BuildableContext ctx = defaultContext();
        ctx.setVerbose(false);
        HashProgress hp = new HashProgress(ctx);
        assertThat(hp.isProgressDisplayed()).isFalse();
    }

    @Test
    public void isProgressDisplayed_notVerboseAndDontHash_returnsFalse() {
        BuildableContext ctx = defaultContext().dontHashFiles();
        ctx.setVerbose(false);
        HashProgress hp = new HashProgress(ctx);
        assertThat(hp.isProgressDisplayed()).isFalse();
    }

    // --- Tests for progress output frequency (Req 11.2) and line wrapping (Req 11.4) ---

    @Test
    public void updateOutput_printsCharEvery10Files() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = Logger.out;
        try {
            Logger.out = new PrintStream(baos);

            BuildableContext ctx = defaultContext(); // verbose=true, hashAll
            HashProgress hp = new HashProgress(ctx);
            hp.outputInit();

            // Feed 9 files — no output yet
            for (int i = 0; i < 9; i++) {
                hp.updateOutput(1000);
            }
            assertThat(baos.toString()).isEmpty();

            // 10th file triggers a progress char
            hp.updateOutput(1000);
            assertThat(baos.toString()).isEqualTo(".");
        } finally {
            Logger.out = original;
        }
    }

    @Test
    public void updateOutput_resetsAccumulatorAfterPrint() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = Logger.out;
        try {
            Logger.out = new PrintStream(baos);

            BuildableContext ctx = defaultContext();
            HashProgress hp = new HashProgress(ctx);
            hp.outputInit();

            // First batch of 10 files with large sizes -> '#'
            for (int i = 0; i < 10; i++) {
                hp.updateOutput(100_000_000); // 1 billion total
            }
            assertThat(baos.toString()).isEqualTo("#");

            // Second batch of 10 files with small sizes -> '.'
            baos.reset();
            for (int i = 0; i < 10; i++) {
                hp.updateOutput(100);
            }
            assertThat(baos.toString()).isEqualTo(".");
        } finally {
            Logger.out = original;
        }
    }

    @Test
    public void updateOutput_suppressedWhenNotVerbose() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = Logger.out;
        try {
            Logger.out = new PrintStream(baos);

            BuildableContext ctx = defaultContext();
            ctx.setVerbose(false);
            HashProgress hp = new HashProgress(ctx);
            hp.outputInit();

            for (int i = 0; i < 20; i++) {
                hp.updateOutput(50_000_000);
            }
            assertThat(baos.toString()).isEmpty();
        } finally {
            Logger.out = original;
        }
    }

    @Test
    public void updateOutput_suppressedWhenDontHash() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = Logger.out;
        try {
            Logger.out = new PrintStream(baos);

            BuildableContext ctx = defaultContext().dontHashFiles();
            HashProgress hp = new HashProgress(ctx);
            hp.outputInit();

            for (int i = 0; i < 20; i++) {
                hp.updateOutput(50_000_000);
            }
            assertThat(baos.toString()).isEmpty();
        } finally {
            Logger.out = original;
        }
    }

    // --- Tests for hashLegend (Req 11.6) ---

    @Test
    public void hashLegend_containsAllProgressCharacters() {
        String legend = cut.hashLegend();
        assertThat(legend).contains("#", "@", "O", "8", "o", ".");
    }

    // --- Tests for hashStarted and noMoreFileToHash signals ---

    @Test
    public void hashStarted_initiallyFalse() {
        BuildableContext ctx = defaultContext();
        HashProgress hp = new HashProgress(ctx);
        assertThat(hp.isHashStarted()).isFalse();
    }

    @Test
    public void hashStarted_trueAfterSignal() {
        BuildableContext ctx = defaultContext();
        HashProgress hp = new HashProgress(ctx);
        hp.hashStarted();
        assertThat(hp.isHashStarted()).isTrue();
    }
}
