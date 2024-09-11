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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class CommandUtil {
    /**
     * Execute a command and return all the output.
     */
    public static String executeCommand(List<String> cmdArray) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(cmdArray);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        try (InputStream is = process.getInputStream();
                Scanner scanner = new Scanner(is).useDelimiter("$")) {
            String output = scanner.hasNext() ? scanner.next() : "";

            process.waitFor();
            int exitValue = process.exitValue();
            if (exitValue != 0) {
                throw new IllegalArgumentException(String.format("Command execution failed with status: %d\n%s", exitValue, output));
            }

            return output;
        }
    }

    /**
     * Execute a command and return all the lines of the output.
     */
    public static List<String> executeCommandAndGetLines(List<String> cmdArray) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(cmdArray);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        try (InputStream is = process.getInputStream();
                Scanner scanner = new Scanner(is).useDelimiter("\n")) {
            List<String> lines = new ArrayList<>();
            while (scanner.hasNext()) {
                lines.add(scanner.next());
            }

            process.waitFor();
            int exitValue = process.exitValue();
            if (exitValue != 0) {
                throw new IllegalArgumentException(String.format("""
                        Command execution failed with status: %d
                        %s""", exitValue, lines));
            }

            return lines;
        }
    }
}
