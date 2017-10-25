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

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;

public class JsonIO {
    private ObjectMapper objectMapper;
    private ObjectWriter objectWriter;

    public JsonIO() {
        JsonFactory jsonFactory = new JsonFactory();

        // All field names will be intern()ed
        jsonFactory.enable(JsonFactory.Feature.CANONICALIZE_FIELD_NAMES);
        jsonFactory.enable(JsonFactory.Feature.INTERN_FIELD_NAMES);
        jsonFactory.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        objectMapper = new ObjectMapper(jsonFactory);

        // Use setters and getters to be able use String.intern(). This reduces the amount of memory needed to load a State file.
        objectMapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
        objectMapper.setVisibility(PropertyAccessor.FIELD, Visibility.NONE);
        objectMapper.setVisibility(PropertyAccessor.CREATOR, Visibility.NONE);
        objectMapper.setVisibility(PropertyAccessor.GETTER, Visibility.ANY);
        objectMapper.setVisibility(PropertyAccessor.SETTER, Visibility.ANY);

        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.setSerializationInclusion(Include.NON_NULL);

        JsonPrettyPrinter prettyPrinter = new JsonPrettyPrinter();
        objectWriter = objectMapper.writer(prettyPrinter);
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public ObjectWriter getObjectWriter() {
        return objectWriter;
    }
}
