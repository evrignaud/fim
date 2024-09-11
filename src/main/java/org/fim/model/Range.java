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

import com.google.common.base.MoreObjects;
import org.fim.command.exception.FimInternalError;

import java.util.Objects;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class Range implements Comparable<Range> {
    private final long from;
    private final long to;

    /**
     * @param from the initial index of the range, inclusive
     * @param to   the final index of the range, exclusive
     */
    public Range(long from, long to) {
        if (from > to) {
            throw new FimInternalError("'to' must be greater than 'from'");
        }

        this.from = from;
        this.to = to;
    }

    /**
     * Return the initial index of the range, inclusive
     */
    public long getFrom() {
        return from;
    }

    /**
     * Return the final index of the range, exclusive
     */
    public long getTo() {
        return to;
    }

    /**
     * Return the union of this Range and the specified Range.
     */
    public Range union(Range range) {
        if (range == null) {
            return new Range(from, to);
        }

        long unionFrom = min(from, range.getFrom());
        long unionTo = max(to, range.getTo());
        return new Range(unionFrom, unionTo);
    }

    /**
     * If the specified Range starts before the end of this Range and finish after, then return a bigger Range.
     * The specified Range must begin inside the current Range.
     */
    public Range adjustToRange(Range range) {
        if (range != null) {
            if (range.getFrom() < from) {
                throw new FimInternalError("The Range must begin inside the current Range");
            }

            if (range.getFrom() < to && range.getTo() > to) {
                return new Range(from, range.getTo());
            }
        }

        return new Range(from, to);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof Range range)) {
            return false;
        }

        return Objects.equals(this.from, range.from)
               && Objects.equals(this.to, range.to);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("from", from)
                .add("to", to)
                .add("length", to - from)
                .toString();
    }

    @Override
    public int compareTo(Range other) {
        int value = Long.compare(from, other.from);
        if (value != 0) {
            return value;
        }

        return Long.compare(to, other.to);
    }
}
