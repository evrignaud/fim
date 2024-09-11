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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;

import java.io.IOException;

public class JsonPrettyPrinter extends DefaultPrettyPrinter {
    public JsonPrettyPrinter() {
        super();
        setupIndenters();
    }

    public JsonPrettyPrinter(JsonPrettyPrinter base) {
        super(base);
        setupIndenters();
    }

    private void setupIndenters() {
        Indenter indenter = new DefaultIndenter();
        indentObjectsWith(indenter);
        indentArraysWith(indenter);
    }

    @Override
    public JsonPrettyPrinter createInstance() {
        return new JsonPrettyPrinter(this);
    }

    @Override
    public void writeObjectFieldValueSeparator(JsonGenerator jg) throws IOException {
        jg.writeRaw(": ");
    }
}
