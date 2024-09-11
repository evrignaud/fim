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

import org.fim.model.Context;
import org.fim.model.HashMode;
import org.fim.util.HashModeUtil;

import java.security.NoSuchAlgorithmException;

import static org.fim.model.Constants.SIZE_4_KB;
import static org.fim.model.HashMode.hashSmallBlock;

public class SmallBlockHasher extends BlockHasher {
    public SmallBlockHasher(Context context) throws NoSuchAlgorithmException {
        super(context);
    }

    @Override
    protected int getBlockSize() {
        return SIZE_4_KB;
    }

    @Override
    protected boolean isCompatible(HashMode hashMode) {
        return HashModeUtil.isCompatible(hashMode, hashSmallBlock);
    }
}
