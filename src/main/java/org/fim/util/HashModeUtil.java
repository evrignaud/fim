/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2016  Etienne Vrignaud
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
 * along with Fim.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.fim.util;

import org.fim.model.HashMode;

import static org.fim.model.HashMode.*;

public class HashModeUtil {
    public static String hashModeToString(HashMode hashMode) {
        switch (hashMode) {
            case dontHash:
                return "do not hash";

            case hashSmallBlock:
                return "super-fast";

            case hashMediumBlock:
                return "fast";

            case hashAll:
                return "full";
        }

        throw new IllegalArgumentException("Invalid hash mode " + hashMode);
    }

    public static boolean isCompatible(HashMode hashMode, HashMode toCheck) {
        switch (hashMode) {
            case hashAll:
                return true;

            case hashMediumBlock:
                return toCheck != hashAll;

            case hashSmallBlock:
                return toCheck != hashAll && toCheck != hashMediumBlock;

            case dontHash:
                return toCheck == dontHash;
        }

        return false;
    }
}
