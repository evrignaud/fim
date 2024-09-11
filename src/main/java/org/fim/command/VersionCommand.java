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
import org.fim.util.Logger;

public class VersionCommand extends AbstractCommand {
    @Override
    public String getCmdName() {
        return "version";
    }

    @Override
    public String getShortCmdName() {
        return "";
    }

    @Override
    public String getDescription() {
        return "Prints the Fim version";
    }

    @Override
    public FimReposConstraint getFimReposConstraint() {
        return FimReposConstraint.DONT_CARE;
    }

    @Override
    public Object execute(Context context) throws Exception {
        String implementationVersion = this.getClass().getPackage().getImplementationVersion();
        if (implementationVersion == null) {
            implementationVersion = "dev";
        }

        Logger.out.println("Fim version " + implementationVersion);
        return null;
    }
}
