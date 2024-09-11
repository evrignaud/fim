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

package org.fim.util;

public class TimeElapsed {
    private long startTime;
    private long endTime;

    public TimeElapsed() {
        start();
    }

    public long getDuration() {
        stop();
        long duration = endTime - startTime;
        start();
        return duration;
    }

    public void start() {
        startTime = System.currentTimeMillis();
    }

    protected void stop() {
        endTime = System.currentTimeMillis();
    }
}
