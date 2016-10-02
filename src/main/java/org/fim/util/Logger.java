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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
    public static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    public static String getCurrentDate() {
        return dateFormat.format(new Date());
    }

    public static void info(String message) {
        writeLogMessage(getCurrentDate() + " - Info  - " + message);
    }

    public static void warning(String message) {
        writeLogMessage(getCurrentDate() + " - Warn  - " + message);
    }

    public static void alert(String message) {
        writeLogMessage(getCurrentDate() + " - Alert - " + message);
    }

    public static void error(String message, Exception ex, boolean displayStackTrace) {
        StringBuilder builder = new StringBuilder().append(message).append("\n");
        if (displayStackTrace) {
            builder.append(exceptionStackTraceToString(ex));
        } else {
            builder.append(ex.getClass().getSimpleName());
            if (ex.getMessage() != null) {
                builder.append(": ").append(ex.getMessage());
            }
        }
        error(builder.toString());
    }

    public static void error(String message) {
        writeLogMessage(getCurrentDate() + " - Error - " + message);
    }

    private static String exceptionStackTraceToString(Exception ex) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PrintStream ps = new PrintStream(baos);
            ex.printStackTrace(ps);
            return baos.toString();
        } catch (IOException e) {
            return ex.getMessage();
        }
    }

    private static void writeLogMessage(String message) {
        System.out.println(message);
        System.out.flush();
    }
}
