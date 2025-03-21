/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2025 Etienne Vrignaud
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

import com.blackducksoftware.tools.commonframework.core.encoding.Ascii85Encoder;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class Ascii85Util {
    private Ascii85Util() {
        // Utility class, no instantiation
    }

    public static final Charset UTF8 = StandardCharsets.UTF_8;

    public static String encode(byte[] bytesToBeEncoded) {
        return new String(Ascii85Encoder.encode(bytesToBeEncoded), UTF8);
    }
}
