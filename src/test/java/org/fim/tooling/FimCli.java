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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import org.fim.Fim;
import org.fim.util.Logger;

/**
 * Runs the real Fim command line against a directory, and keeps what it printed and the status it exited with.
 *
 * This goes through {@link Fim#main(String[])} rather than through a command object, so that a test covers
 * what a user actually reaches: argument parsing, command lookup by name or by short name, the repository
 * checks, and the mapping from an exception to an exit status. A test that calls a command directly skips all
 * of that, which is where most of the behaviour of a command line lives.
 *
 * The directory is passed with {@code -d}, so nothing depends on the working directory of the test JVM and
 * every run is independent.
 */
public class FimCli {
    private final Path repositoryDir;

    private int exitStatus;
    private String output;
    private String input;

    public FimCli(Path repositoryDir) {
        this.repositoryDir = repositoryDir;
        Fim.setCalledFromTest(true);
    }

    /**
     * What the next run should find on its standard input, for a command that asks a question.
     *
     * The confirmation prompt reads from System.in directly, so answering it is the only way to cover what
     * happens when a user says no. Answers are consumed by the run that follows and then forgotten, so one
     * test cannot leave an answer behind for the next.
     *
     * @param answers one per prompt, for example "y", "n", or "A" to accept this one and every later one
     */
    public FimCli answering(String... answers) {
        this.input = String.join("\n", answers) + "\n";
        return this;
    }

    /**
     * Run fim with these arguments, plus {@code -d} pointing at the repository.
     *
     * @return the status fim exited with
     */
    public int run(String... arguments) throws Exception {
        String[] fullArguments = new String[arguments.length + 2];
        System.arraycopy(arguments, 0, fullArguments, 0, arguments.length);
        fullArguments[arguments.length] = "-d";
        fullArguments[arguments.length + 1] = repositoryDir.toString();

        // Logger.out and Logger.level are static, and a run asking for csv or json output lowers the level on
        // purpose. Restoring both keeps one test from changing what the next one is able to observe.
        PrintStream originalOut = Logger.out;
        int originalLevel = Logger.level;
        InputStream originalIn = System.in;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Logger.out = new PrintStream(captured, true, StandardCharsets.UTF_8);
        if (input != null) {
            System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
        }
        try {
            Fim.main(fullArguments);
        } finally {
            Logger.out.flush();
            Logger.out = originalOut;
            Logger.level = originalLevel;
            System.setIn(originalIn);
            input = null;
        }

        output = captured.toString(StandardCharsets.UTF_8);
        exitStatus = Fim.getExitStatus();
        return exitStatus;
    }

    public int exitStatus() {
        return exitStatus;
    }

    public String output() {
        return output;
    }

    /**
     * Run and require it to have worked. The message on failure carries the output, because a bare
     * "expected 0 but was -1" says nothing about what fim was complaining about.
     */
    public FimCli succeeds(String... arguments) throws Exception {
        run(arguments);
        assertThat(exitStatus)
                .withFailMessage("fim %s exited with %d instead of 0. Output:%n%s",
                        String.join(" ", arguments), exitStatus, output)
                .isZero();
        return this;
    }

    /** Run and require it to have been refused as a misuse, which fim reports as -1. */
    public FimCli failsAsBadUsage(String... arguments) throws Exception {
        run(arguments);
        assertThat(exitStatus)
                .withFailMessage("fim %s exited with %d instead of -1. Output:%n%s",
                        String.join(" ", arguments), exitStatus, output)
                .isEqualTo(-1);
        return this;
    }

    /** Run and require it to have failed on the repository itself, which fim reports as -2. */
    public FimCli failsOnRepository(String... arguments) throws Exception {
        run(arguments);
        assertThat(exitStatus)
                .withFailMessage("fim %s exited with %d instead of -2. Output:%n%s",
                        String.join(" ", arguments), exitStatus, output)
                .isEqualTo(-2);
        return this;
    }

    public FimCli outputContains(String... expected) {
        for (String fragment : expected) {
            assertThat(output)
                    .withFailMessage("expected the output to contain '%s'. Output:%n%s", fragment, output)
                    .contains(fragment);
        }
        return this;
    }

    public FimCli outputDoesNotContain(String... unexpected) {
        for (String fragment : unexpected) {
            assertThat(output)
                    .withFailMessage("expected the output not to contain '%s'. Output:%n%s", fragment, output)
                    .doesNotContain(fragment);
        }
        return this;
    }
}
