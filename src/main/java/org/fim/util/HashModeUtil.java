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

import org.fim.model.HashMode;

import static org.fim.model.HashMode.dontHash;
import static org.fim.model.HashMode.hashAll;
import static org.fim.model.HashMode.hashMediumBlock;

public class HashModeUtil {
    public static String hashModeToString(HashMode hashMode) {
        return switch (hashMode) {
            case dontHash -> "do not hash";
            case hashSmallBlock -> "super-fast";
            case hashMediumBlock -> "fast";
            case hashAll -> "full";
        };
    }

    public static boolean isCompatible(HashMode hashMode, HashMode toCheck) {
        return switch (hashMode) {
            case hashAll -> true;
            case hashMediumBlock -> toCheck != hashAll;
            case hashSmallBlock -> toCheck != hashAll && toCheck != hashMediumBlock;
            case dontHash -> toCheck == dontHash;
        };
    }
}
