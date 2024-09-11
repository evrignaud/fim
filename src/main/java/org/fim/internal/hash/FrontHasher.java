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

import org.fim.command.exception.FimInternalError;
import org.fim.model.Context;
import org.fim.model.FileHash;
import org.fim.model.Range;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;

import static java.lang.Math.max;

public class FrontHasher implements Hasher {
    private final BlockHasher smallBlockHasher;
    private final BlockHasher mediumBlockHasher;
    private final Hasher fullHasher;

    public FrontHasher(Context context) throws NoSuchAlgorithmException {
        this.smallBlockHasher = new SmallBlockHasher(context);
        this.mediumBlockHasher = new MediumBlockHasher(context);
        this.fullHasher = new FullHasher(context);
    }

    @Override
    public boolean isActive() {
        return smallBlockHasher.isActive() && mediumBlockHasher.isActive() && fullHasher.isActive();
    }

    @Override
    public void reset(long fileSize) {
        smallBlockHasher.reset(fileSize);
        mediumBlockHasher.reset(fileSize);
        fullHasher.reset(fileSize);
    }

    @Override
    public Range getNextRange(long filePosition) {
        Range nextSmallRange;
        Range nextMediumRange;
        Range nextFullRange;

        if (fullHasher.isActive()) {
            nextSmallRange = smallBlockHasher.getNextRange(filePosition);
            nextMediumRange = mediumBlockHasher.getNextRange(filePosition);
            nextFullRange = fullHasher.getNextRange(filePosition);

            Range nextRange = nextFullRange.adjustToRange(nextSmallRange);
            nextRange = nextRange.adjustToRange(nextMediumRange);

            return nextRange;
        } else if (smallBlockHasher.isActive() && mediumBlockHasher.isActive()) {
            nextSmallRange = smallBlockHasher.getNextRange(filePosition);
            nextMediumRange = mediumBlockHasher.getNextRange(filePosition);

            if (nextSmallRange == null && nextMediumRange == null) {
                return null;
            }

            if (nextSmallRange == null) {
                return nextMediumRange;
            }

            if (nextMediumRange == null) {
                return nextSmallRange;
            }

            if (nextSmallRange.getTo() <= nextMediumRange.getFrom()) {
                // Next small block is before the next medium block
                return nextSmallRange;
            }

            if (nextMediumRange.getTo() <= nextSmallRange.getFrom()) {
                // Next medium block is before the next small block
                return nextMediumRange;
            }

            return nextSmallRange.union(nextMediumRange);
        } else if (smallBlockHasher.isActive()) {
            nextSmallRange = smallBlockHasher.getNextRange(filePosition);
            return nextSmallRange;
        }
        throw new FimInternalError(String.format("Fim is not working correctly. Unable to getNextRange for filePosition %d", filePosition));
    }

    @Override
    public void update(long filePosition, ByteBuffer buffer) {
        update(smallBlockHasher, filePosition, buffer);
        update(mediumBlockHasher, filePosition, buffer);
        update(fullHasher, filePosition, buffer);
    }

    private void update(Hasher hasher, long filePosition, ByteBuffer buffer) {
        if (hasher.isActive()) {
            int bufferPosition = buffer.position();
            int bufferLimit = buffer.limit();
            try {
                hasher.update(filePosition, buffer);
            } finally {
                buffer.limit(bufferLimit);
                buffer.position(bufferPosition);
            }
        }
    }

    @Override
    public String getHash() {
        throw new FimInternalError("Not implemented");
    }

    @Override
    public long getBytesHashed() {
        long bytesHashed =
                max(smallBlockHasher.getBytesHashed(),
                        max(mediumBlockHasher.getBytesHashed(), fullHasher.getBytesHashed()));
        return bytesHashed;
    }

    @Override
    public long getTotalBytesHashed() {
        long totalBytesHashed =
                max(smallBlockHasher.getTotalBytesHashed(),
                        max(mediumBlockHasher.getTotalBytesHashed(), fullHasher.getTotalBytesHashed()));
        return totalBytesHashed;
    }

    @Override
    public long getInstantThroughput() {
        long instantThroughput =
                max(smallBlockHasher.getInstantThroughput(),
                        max(mediumBlockHasher.getInstantThroughput(), fullHasher.getInstantThroughput()));
        return instantThroughput;
    }

    @Override
    public boolean hashComplete() {
        return smallBlockHasher.hashComplete() && mediumBlockHasher.hashComplete() && fullHasher.hashComplete();
    }

    public FileHash getFileHash() {
        return new FileHash(smallBlockHasher.getHash(), mediumBlockHasher.getHash(), fullHasher.getHash());
    }

    protected Hasher getSmallBlockHasher() {
        return smallBlockHasher;
    }

    protected Hasher getMediumBlockHasher() {
        return mediumBlockHasher;
    }

    protected Hasher getFullHasher() {
        return fullHasher;
    }
}
