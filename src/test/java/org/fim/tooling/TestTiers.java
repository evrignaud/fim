/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2026 Etienne Vrignaud
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

package org.fim.tooling;

/**
 * The tags that decide when a test runs.
 *
 * Keeping the expensive scenarios behind a tag is what allows them to be thorough. A suite that everybody
 * waits for on every build gets trimmed until it is quick; one that is asked for explicitly can afford to
 * write hundreds of megabytes and run the whole hash mode matrix.
 *
 * These values are duplicated as literals in build.gradle.kts, because a build script cannot reference the
 * classes it is about to compile.
 */
public final class TestTiers {
    /**
     * Drives the fim command line end to end using small files. Runs on every build.
     */
    public static final String INTEGRATION = "integration";

    /**
     * Needs large files, or the whole hash mode matrix, or both. Left out of the default build and run with
     * {@code ./gradlew slowTest}.
     */
    public static final String SLOW = "slow";

    private TestTiers() {
        // Constants only
    }
}
