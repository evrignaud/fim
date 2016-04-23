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
package org.fim.model;

import com.google.common.base.MoreObjects;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FileToIgnore {
    private String fileNamePattern;
    private Pattern compiledPattern;

    public FileToIgnore(String fileNamePattern) {
        this.fileNamePattern = fileNamePattern.trim();
        try {
            String pattern = this.fileNamePattern;

            // Only * is allowed in the fileName. So escape the other regexp special chars
            pattern = escapeChars(pattern, '\\', '^', '$', '{', '}', '[', ']', '(', ')', '.', '|', '?');

            // Many * char are converted to only one
            while (pattern.contains("**")) {
                pattern = pattern.replace("**", "*");
            }

            // * is converted to .* in order to match zero or many chars
            pattern = pattern.replace("*", ".*");

            // Add start and end line anchors
            pattern = "^" + pattern + "$";

            this.compiledPattern = Pattern.compile(pattern);
        } catch (PatternSyntaxException ex) {
            this.compiledPattern = null;
        }
    }

    private String escapeChars(String str, char... charToEscape) {
        String result = str;
        for (char c : charToEscape) {
            result = result.replace("" + c, "\\" + c);
        }

        return result;
    }

    public String getFileNamePattern() {
        return fileNamePattern;
    }

    public Pattern getCompiledPattern() {
        return compiledPattern;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        FileToIgnore that = (FileToIgnore) other;
        return Objects.equals(fileNamePattern, that.fileNamePattern) &&
            Objects.equals(compiledPattern.toString(), that.compiledPattern.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileNamePattern, compiledPattern.toString());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("fileNamePattern", fileNamePattern)
            .add("compiledPattern", compiledPattern)
            .toString();
    }
}
