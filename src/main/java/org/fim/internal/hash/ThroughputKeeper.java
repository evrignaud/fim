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

import static java.lang.Math.max;
import static org.fim.model.Constants.SIZE_1_MB;

public class ThroughputKeeper {
    public static final int BLOCK_SIZE = 500 * SIZE_1_MB;
    public static final int HALF_BLOCK_SIZE = BLOCK_SIZE / 2;

    // Maintain a sliding window to compute throughput
    private Position blockBegin;
    private Position halfBlock;

    public ThroughputKeeper() {
        reset();
    }

    private void reset() {
        this.blockBegin = new Position(0);
        this.halfBlock = null;
    }

    public void update(long size) {
        blockBegin.incSize(size);

        if (halfBlock != null) {
            halfBlock.incSize(size);
        } else if (blockBegin.getSize() > HALF_BLOCK_SIZE) {
            halfBlock = new Position(0);
        }

        if (blockBegin.getSize() > BLOCK_SIZE) {
            blockBegin = halfBlock;
            halfBlock = null;
        }
    }

    public long getInstantThroughput() {
        long durationMillis = blockBegin.getDurationMillis();
        long durationSeconds;
        long size = blockBegin.getSize();
        if (durationMillis < 1_000) {
            durationSeconds = 1;
            size = (blockBegin.getSize() * 1_000) / durationMillis;
        } else {
            durationSeconds = durationMillis / 1_000;
        }
        return size / durationSeconds;
    }

    private static class Position {
        private long size;
        private final long time;

        Position(long size) {
            this.size = size;
            this.time = System.currentTimeMillis();
        }

        public long getDurationMillis() {
            return max(System.currentTimeMillis() - getTime(), 1);
        }

        public long getSize() {
            return size;
        }

        public long getTime() {
            return time;
        }

        public void incSize(long size) {
            this.size += size;
        }
    }
}
