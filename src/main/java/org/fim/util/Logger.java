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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
    public enum Level {
        error,
        alert,
        warning,
        info
    }

    public static int level = Level.info.ordinal();
    public static PrintStream out = System.out;
    public static boolean debugEnabled = checkDebugEnabled();
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    public static String getCurrentDate() {
        return DATE_FORMAT.format(new Date());
    }

    public static void rawDebug(String message) {
        if (debugEnabled) {
            out.println(message);
        }
    }

    public static void info(String message) {
        if (level >= Level.info.ordinal()) {
            writeLogMessage(getCurrentDate() + " - Info  - " + message);
        }
    }

    public static void warning(String message) {
        if (level >= Level.warning.ordinal()) {
            writeLogMessage(getCurrentDate() + " - Warn  - " + message);
        }
    }

    public static void alert(String message) {
        if (level >= Level.alert.ordinal()) {
            writeLogMessage(getCurrentDate() + " - Alert - " + message);
        }
    }

    public static void error(String message, Exception ex, boolean displayStackTrace) {
        if (level >= Level.error.ordinal()) {
            StringBuilder builder = new StringBuilder().append(message);
            if (displayStackTrace) {
                builder.append("\n").append(exceptionStackTraceToString(ex));
            } else {
                builder.append(": ").append(ex.getClass().getSimpleName());
                if (ex.getMessage() != null) {
                    builder.append(": ").append(ex.getMessage());
                }
            }
            error(builder.toString());
        }
    }

    public static void error(String message) {
        if (level >= Level.error.ordinal()) {
            writeLogMessage(getCurrentDate() + " - Error - " + message);
        }
    }

    public static void newLine() {
        if (level >= Level.info.ordinal()) {
            out.println();
        }
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
        out.println(message);
        out.flush();
    }

    private static boolean checkDebugEnabled() {
        String debug = System.getenv("DEBUG");
        if (debug == null) {
            return false;
        }
        return Boolean.parseBoolean(debug);
    }
}
