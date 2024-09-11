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

import org.junit.jupiter.api.Test;

public class LoggerTest {
    @Test
    public void canLogMessages() {
        Logger.info("Info message");
        Logger.warning("Warning message");
        Logger.alert("Alert message");
        Logger.error("Error message");
    }

    @Test
    public void canLogAnException() {
        try {
            throw new RuntimeException("Exception for test");
        } catch (RuntimeException ex) {
            Logger.error("With a message", ex, false);
            Logger.error("With a stack trace", ex, true);
        }
    }
}
