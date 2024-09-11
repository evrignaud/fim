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

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FilePattern {
    private String fileName;
    private Pattern compiled;

    private FilePattern() {
        // Empty constructor to speed up cloning
    }

    public FilePattern(String fileNamePattern) {
        this.fileName = fileNamePattern.trim();
        try {
            String pattern = this.fileName;

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

            this.compiled = Pattern.compile(pattern);
        } catch (PatternSyntaxException ex) {
            this.compiled = null;
        }
    }

    private String escapeChars(String str, char... charToEscape) {
        String result = str;
        for (char c : charToEscape) {
            result = result.replace("" + c, "\\" + c);
        }

        return result;
    }

    public String getFileName() {
        return fileName;
    }

    public Pattern getCompiled() {
        return compiled;
    }

    public boolean match(String fileNameToMatch) {
        if (compiled != null) {
            Matcher matcher = compiled.matcher(fileNameToMatch);
            return matcher.find();
        } else {
            return fileName.equals(fileNameToMatch);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        FilePattern that = (FilePattern) other;
        return Objects.equals(fileName, that.fileName) &&
               Objects.equals(compiled.toString(), that.compiled.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, compiled.toString());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("fileName", fileName)
                .add("compiled", compiled)
                .toString();
    }

    public static boolean matchPatterns(String fileName, List<FilePattern> patterns, boolean defaultValue) {
        if (patterns != null) {
            for (FilePattern filePattern : patterns) {
                if (filePattern.match(fileName)) {
                    return true;
                }
            }
            return false;
        }
        return defaultValue;
    }

    @Override
    public FilePattern clone() {
        FilePattern cloned = new FilePattern();
        cloned.fileName = this.fileName;
        cloned.compiled = this.compiled;
        return cloned;
    }
}
