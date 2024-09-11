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


package org.fim.command;

import org.fim.model.Context;
import org.fim.tooling.BuildableContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Scanner;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractCommandTest {
    private BuildableContext context;
    private final MyCommand cut = new MyCommand();

    @BeforeEach
    public void setUp() {
        context = new BuildableContext();
    }

    @Test
    public void weConfirmAnAction() {
        assertThat(confirmAction("y")).isEqualTo(true);
        assertThat(context.isAlwaysYes()).isEqualTo(false);

        assertThat(confirmAction("Y")).isEqualTo(true);
        assertThat(context.isAlwaysYes()).isEqualTo(false);

        assertThat(confirmAction("A")).isEqualTo(true);
        assertThat(context.isAlwaysYes()).isEqualTo(true);
        context.setAlwaysYes(false);

        assertThat(confirmAction("n")).isEqualTo(false);
        assertThat(context.isAlwaysYes()).isEqualTo(false);

        assertThat(confirmAction("2")).isEqualTo(false);
        assertThat(context.isAlwaysYes()).isEqualTo(false);
    }

    private boolean confirmAction(String input) {
        return cut.callConfirmAction(context, new Scanner(input + "\n"), "action");
    }

    private static class MyCommand extends AbstractCommand {
        @Override
        public String getCmdName() {
            return null;
        }

        @Override
        public String getShortCmdName() {
            return null;
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public Object execute(Context context) throws Exception {
            return null;
        }

        boolean callConfirmAction(Context context, Scanner scanner, String action) {
            return confirmAction(context, scanner, action);
        }
    }
}
