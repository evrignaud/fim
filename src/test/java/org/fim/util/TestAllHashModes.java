/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2025 Etienne Vrignaud
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

import org.fim.model.HashMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@ParameterizedTest(name = "Hash mode: {0}")
@EnumSource(HashMode.class)
@Retention(RetentionPolicy.RUNTIME)
public @interface TestAllHashModes {
}
