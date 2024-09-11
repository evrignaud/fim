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

public class Ignored {
    private boolean attributesIgnored;
    private boolean datesIgnored;
    private boolean renamedIgnored;

    public boolean isAttributesIgnored() {
        return attributesIgnored;
    }

    public void setAttributesIgnored(boolean attributesIgnored) {
        this.attributesIgnored = attributesIgnored;
    }

    public boolean isDatesIgnored() {
        return datesIgnored;
    }

    public void setDatesIgnored(boolean datesIgnored) {
        this.datesIgnored = datesIgnored;
    }

    public boolean isRenamedIgnored() {
        return renamedIgnored;
    }

    public void setRenamedIgnored(boolean renamedIgnored) {
        this.renamedIgnored = renamedIgnored;
    }

    public boolean somethingIgnored() {
        return attributesIgnored || datesIgnored || renamedIgnored;
    }

    @Override
    public Ignored clone() {
        Ignored clonedIgnored = new Ignored();
        clonedIgnored.attributesIgnored = this.attributesIgnored;
        clonedIgnored.datesIgnored = this.datesIgnored;
        clonedIgnored.renamedIgnored = this.renamedIgnored;
        return clonedIgnored;
    }
}
