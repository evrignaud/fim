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

import org.fim.model.HashMode;
import org.fim.model.Range;
import org.fim.tooling.BuildableContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.tooling.TestConstants.SIZE_12_KB;
import static org.fim.tooling.TestConstants.SIZE_16_KB;
import static org.fim.tooling.TestConstants.SIZE_1_KB;
import static org.fim.tooling.TestConstants.SIZE_1_MB;
import static org.fim.tooling.TestConstants.SIZE_20_KB;
import static org.fim.tooling.TestConstants.SIZE_2_MB;
import static org.fim.tooling.TestConstants.SIZE_3_MB;
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

    // --- Edge-case tests for boundary file sizes (Requirements 4.1-4.5, 5.1-5.5, 6.1-6.5) ---

    @Test
    public void zeroByteFile_selectsBlockIndex0_withEmptyRange() {
        cut.reset(0);
        // fileSize=0 <= 2*blockSize → block [0], clamped to [0, 0)
        assertThat(cut.getRanges()).isEqualTo(new Range[] { new Range(0, 0) });
        assertThat(cut.getSizeToHash()).isEqualTo(0);
    }

    @Test
    public void oneByteFile_selectsBlockIndex0_clampedToFileSize() {
        cut.reset(1);
        // fileSize=1 <= 2*blockSize → block [0], range [0, 1)
        assertThat(cut.getRanges()).isEqualTo(new Range[] { new Range(0, 1) });
        assertThat(cut.getSizeToHash()).isEqualTo(1);
    }

    @Test
    public void fileSizeExactlyOneBlock_selectsBlockIndex0() {
        cut.reset(SIZE_4_KB);
        // fileSize=4096 <= 2*4096 → block [0], range [0, 4096)
        assertThat(cut.getRanges()).isEqualTo(new Range[] { new Range(0, SIZE_4_KB) });
        assertThat(cut.getSizeToHash()).isEqualTo(SIZE_4_KB);
    }

    @Test
    public void fileSizeExactlyTwoBlocks_selectsBlockIndex0() {
        cut.reset(SIZE_8_KB);
        // fileSize=8192 is NOT > 2*4096 → block [0], range [0, 4096)
        assertThat(cut.getRanges()).isEqualTo(new Range[] { new Range(0, SIZE_4_KB) });
        assertThat(cut.getSizeToHash()).isEqualTo(SIZE_4_KB);
    }

    @Test
    public void fileSizeJustAboveTwoBlocks_selectsBlockIndex1() {
        cut.reset(SIZE_8_KB + 1);
        // fileSize=8193 > 2*4096 → block [1], range [4096, 8192)
        assertThat(cut.getRanges()).isEqualTo(new Range[] { new Range(SIZE_4_KB, SIZE_8_KB) });
        assertThat(cut.getSizeToHash()).isEqualTo(SIZE_4_KB);
    }

    @Test
    public void fileSizeExactlyThreeBlocks_selectsOneBlockAtIndex1() {
        cut.reset(SIZE_12_KB);
        // fileSize=12288 is NOT > 3*4096 → block [1], range [4096, 8192)
        assertThat(cut.getRanges()).isEqualTo(new Range[] { new Range(SIZE_4_KB, SIZE_8_KB) });
        assertThat(cut.getSizeToHash()).isEqualTo(SIZE_4_KB);
    }

    @Test
    public void fileSizeJustAboveThreeBlocks_selectsTwoBlocks() {
        cut.reset(SIZE_12_KB + 1);
        // fileSize=12289 > 3*4096 → blocks [1, end], end = (12289/4096)-1 = 3-1 = 2
        // block 1: [4096, 8192), block 2: [8192, 12288)
        assertThat(cut.getRanges()).isEqualTo(new Range[] {
                new Range(SIZE_4_KB, SIZE_8_KB),
                new Range(SIZE_8_KB, SIZE_12_KB)
        });
        assertThat(cut.getSizeToHash()).isEqualTo(SIZE_4_KB * 2);
    }

    @Test
    public void fileSizeExactlyFourBlocks_selectsTwoBlocks() {
        cut.reset(SIZE_16_KB);
        // fileSize=16384 is NOT > 4*4096 → blocks [1, end], end = (16384/4096)-1 = 3
        // block 1: [4096, 8192), block 3: [12288, 16384)
        assertThat(cut.getRanges()).isEqualTo(new Range[] {
                new Range(SIZE_4_KB, SIZE_8_KB),
                new Range(SIZE_12_KB, SIZE_16_KB)
        });
        assertThat(cut.getSizeToHash()).isEqualTo(SIZE_4_KB * 2);
    }

    @Test
    public void fileSizeJustAboveFourBlocks_selectsThreeBlocks() {
        cut.reset(SIZE_16_KB + 1);
        // fileSize=16385 > 4*4096 → blocks [1, middle, end]
        // middle = (16385/4096)/2 = 4/2 = 2, end = (16385/4096)-1 = 3
        // block 1: [4096, 8192), block 2: [8192, 12288), block 3: [12288, 16384)
        assertThat(cut.getRanges()).isEqualTo(new Range[] {
                new Range(SIZE_4_KB, SIZE_8_KB),
                new Range(SIZE_8_KB, SIZE_12_KB),
                new Range(SIZE_12_KB, SIZE_16_KB)
        });
        assertThat(cut.getSizeToHash()).isEqualTo(SIZE_4_KB * 3);
    }

    @Test
    public void rangeClampingForLastBlock_clampsToFileSize() {
        // File size that makes the last block partial
        // fileSize = 16385 → end block index 3, block 3: from=12288, to=min(16385, 16384)=16384
        // Actually 16384 < 16385, so to=16384. Not clamped in this case.
        // Use a file where end block's to would exceed fileSize:
        // fileSize = 16385, end block = 3, from = 3*4096 = 12288, to = min(16385, 12288+4096) = min(16385, 16384) = 16384
        // Let's use a file where the block truly gets clamped:
        // fileSize = 13000 > 3*4096=12288 → blocks [1, end], end = (13000/4096)-1 = 3-1 = 2
        // block 2: from = 2*4096 = 8192, to = min(13000, 8192+4096) = min(13000, 12288) = 12288. Not clamped.
        // For true clamping, we need a file where from + blockSize > fileSize
        // fileSize = 9000 > 2*4096=8192 → block [1], from = 4096, to = min(9000, 4096+4096) = min(9000, 8192) = 8192. Not clamped.
        // fileSize = 5000 <= 2*4096=8192 → block [0], from = 0, to = min(5000, 0+4096) = min(5000, 4096) = 4096. Not clamped.
        // fileSize = 3000 <= 2*4096=8192 → block [0], from = 0, to = min(3000, 0+4096) = min(3000, 4096) = 3000. Clamped!
        cut.reset(3000);
        assertThat(cut.getRanges()).isEqualTo(new Range[] { new Range(0, 3000) });
        assertThat(cut.getSizeToHash()).isEqualTo(3000);

        // Verify all ranges are within [0, fileSize]
        for (Range range : cut.getRanges()) {
            assertThat(range.getFrom()).isGreaterThanOrEqualTo(0);
            assertThat(range.getTo()).isLessThanOrEqualTo(3000);
        }
    }

    @Test
    public void allRangesClampedToFileSize_forVariousSizes() {
        long[] fileSizes = { 0, 1, 100, SIZE_4_KB - 1, SIZE_4_KB, SIZE_8_KB, SIZE_8_KB + 1,
                SIZE_12_KB, SIZE_12_KB + 1, SIZE_16_KB, SIZE_16_KB + 1, SIZE_20_KB + 509 };
        for (long fileSize : fileSizes) {
            cut.reset(fileSize);
            for (Range range : cut.getRanges()) {
                assertThat(range.getFrom())
                        .as("Range from for fileSize=%d", fileSize)
                        .isGreaterThanOrEqualTo(0);
                assertThat(range.getTo())
                        .as("Range to for fileSize=%d", fileSize)
                        .isLessThanOrEqualTo(fileSize);
            }
        }
    }

    @Test
    public void sizeToHashEqualsSum_ofAllRangeSizes() {
        long[] fileSizes = { 0, 1, 100, SIZE_4_KB, SIZE_8_KB, SIZE_8_KB + 1,
                SIZE_12_KB + 1, SIZE_16_KB, SIZE_16_KB + 1, SIZE_20_KB + 509 };
        for (long fileSize : fileSizes) {
            cut.reset(fileSize);
            long expectedSizeToHash = 0;
            for (Range range : cut.getRanges()) {
                expectedSizeToHash += range.getTo() - range.getFrom();
            }
            assertThat(cut.getSizeToHash())
                    .as("sizeToHash for fileSize=%d", fileSize)
                    .isEqualTo(expectedSizeToHash);
        }
    }

    @Nested
    class SmallBlockHasherBlockSizeTest {
        private SmallBlockHasher smallBlockHasher;

        @BeforeEach
        void setUp() throws NoSuchAlgorithmException {
            BuildableContext context = new BuildableContext().hashSmallBlock();
            smallBlockHasher = new SmallBlockHasher(context);
        }

        @Test
        void blockSizeIs4096() {
            assertThat(smallBlockHasher.getBlockSize()).isEqualTo(4096);
        }
    }

    @Nested
    class MediumBlockHasherBlockSizeTest {
        private MediumBlockHasher mediumBlockHasher;

        @BeforeEach
        void setUp() throws NoSuchAlgorithmException {
            BuildableContext context = new BuildableContext().hashMediumBlock();
            mediumBlockHasher = new MediumBlockHasher(context);
        }

        @Test
        void blockSizeIs1MB() {
            assertThat(mediumBlockHasher.getBlockSize()).isEqualTo(1_048_576);
        }

        @Test
        void boundaryFileSizes_selectCorrectBlocks() {
            // fileSize <= 2*1MB → block [0]
            mediumBlockHasher.reset(SIZE_2_MB);
            assertThat(mediumBlockHasher.getRanges()).isEqualTo(new Range[] { new Range(0, SIZE_1_MB) });

            // fileSize > 2*1MB → block [1]
            mediumBlockHasher.reset(SIZE_2_MB + 1);
            assertThat(mediumBlockHasher.getRanges()).isEqualTo(new Range[] { new Range(SIZE_1_MB, SIZE_2_MB) });

            // fileSize > 3*1MB → blocks [1, end]
            mediumBlockHasher.reset(SIZE_3_MB + 1);
            // end = (3145729/1048576)-1 = 3-1 = 2
            assertThat(mediumBlockHasher.getRanges()).isEqualTo(new Range[] {
                    new Range(SIZE_1_MB, SIZE_2_MB),
                    new Range(SIZE_2_MB, SIZE_3_MB)
            });

            // fileSize > 4*1MB → blocks [1, middle, end]
            long fourMBPlusOne = (long) SIZE_1_MB * 4 + 1;
            mediumBlockHasher.reset(fourMBPlusOne);
            // middle = (4194305/1048576)/2 = 4/2 = 2, end = 4-1 = 3
            assertThat(mediumBlockHasher.getRanges()).isEqualTo(new Range[] {
                    new Range(SIZE_1_MB, SIZE_2_MB),
                    new Range(SIZE_2_MB, SIZE_3_MB),
                    new Range(SIZE_3_MB, (long) SIZE_1_MB * 4)
            });
        }

        @Test
        void allRangesClampedToFileSize() {
            long[] fileSizes = { 0, 1, 500_000, SIZE_1_MB, SIZE_2_MB, SIZE_2_MB + 1,
                    SIZE_3_MB, SIZE_3_MB + 1, (long) SIZE_1_MB * 4, (long) SIZE_1_MB * 4 + 1 };
            for (long fileSize : fileSizes) {
                mediumBlockHasher.reset(fileSize);
                for (Range range : mediumBlockHasher.getRanges()) {
                    assertThat(range.getFrom())
                            .as("Range from for fileSize=%d", fileSize)
                            .isGreaterThanOrEqualTo(0);
                    assertThat(range.getTo())
                            .as("Range to for fileSize=%d", fileSize)
                            .isLessThanOrEqualTo(fileSize);
                }
            }
        }
    }
}
