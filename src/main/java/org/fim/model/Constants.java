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

public class Constants {
    public static final int SIZE_1_KB = 1_024;
    public static final int SIZE_4_KB = 4 * SIZE_1_KB;

    public static final int SIZE_1_MB = 1_024 * SIZE_1_KB;
    public static final int SIZE_10_MB = 10 * SIZE_1_MB;
    public static final int SIZE_20_MB = 20 * SIZE_1_MB;
    public static final int SIZE_50_MB = 50 * SIZE_1_MB;
    public static final int SIZE_100_MB = 100 * SIZE_1_MB;
    public static final int SIZE_200_MB = 200 * SIZE_1_MB;

    public static final int SIZE_1_GB = 1_024 * SIZE_1_MB;

    public static final String NO_HASH = "no_hash";
}
