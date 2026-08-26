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

package org.fim.command;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.fim.command.exception.BadFimUsageException;
import org.fim.model.HashMode;
import org.fim.tooling.BuildableContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Property-based tests for DetectCorruptionCommand.
 * Feature: corruption-detection
 */
public class DetectCorruptionCommandPropertyTest {

    // Feature: corruption-detection, Property 1: Non-hashAll mode rejection
    // **Validates: Requirements 1.1, 1.2**
    @ParameterizedTest
    @MethodSource("nonHashAllModes")
    void nonHashAllModeThrowsBadFimUsageException(HashMode hashMode) {
        BuildableContext context = new BuildableContext();
        context.setHashMode(hashMode);

        DetectCorruptionCommand command = new DetectCorruptionCommand();

        assertThatThrownBy(() -> command.execute(context))
                .isInstanceOf(BadFimUsageException.class);
    }

    /** The three modes that are not hashAll. The whole domain, so it is enumerated. */
    static Stream<HashMode> nonHashAllModes() {
        return Stream.of(HashMode.dontHash, HashMode.hashSmallBlock, HashMode.hashMediumBlock);
    }
}
