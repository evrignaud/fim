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

package org.fim;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Option.Builder;
import org.apache.commons.cli.Options;
import org.fim.command.AbstractCommand;
import org.fim.command.CommitCommand;
import org.fim.command.DetectCorruptionCommand;
import org.fim.command.DiffCommand;
import org.fim.command.DisplayIgnoredFilesCommand;
import org.fim.command.FindDuplicatesCommand;
import org.fim.command.HelpCommand;
import org.fim.command.InitCommand;
import org.fim.command.LogCommand;
import org.fim.command.PurgeStatesCommand;
import org.fim.command.RemoveDuplicatesCommand;
import org.fim.command.ResetFileAttributesCommand;
import org.fim.command.RollbackCommand;
import org.fim.command.StatusCommand;
import org.fim.command.VersionCommand;
import org.fim.command.exception.BadFimUsageException;
import org.fim.command.exception.DontWantToContinueException;
import org.fim.command.exception.RepositoryException;
import org.fim.internal.SettingsManager;
import org.fim.model.Command;
import org.fim.model.Command.FimReposConstraint;
import org.fim.model.Context;
import org.fim.model.FilePattern;
import org.fim.model.Ignored;
import org.fim.model.OutputType;
import org.fim.model.SortMethod;
import org.fim.util.Logger;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import static org.fim.model.HashMode.dontHash;
import static org.fim.model.HashMode.hashAll;
import static org.fim.model.HashMode.hashMediumBlock;
import static org.fim.model.HashMode.hashSmallBlock;

public class Fim {
    private static boolean calledFromTest = false;
    private static int exitStatus = 0;

    private final List<AbstractCommand> commands = buildCommands();
    private final Options options = buildOptions();

    public static void main(String[] args) throws Exception {
        try {
            Fim fim = new Fim();
            Context context = new Context();
            fim.run(args, context);
            exitWithStatus(0);
        } catch (DontWantToContinueException ex) {
            exitWithStatus(0);
        } catch (BadFimUsageException ex) {
            exitWithStatus(-1);
        } catch (RepositoryException ex) {
            exitWithStatus(-2);
        }
    }

    public static void setCalledFromTest(boolean calledFromTest) {
        Fim.calledFromTest = calledFromTest;
    }

    public static void exitWithStatus(int exitStatus) {
        Fim.exitStatus = exitStatus;
        if (!calledFromTest) {
            System.exit(exitStatus);
        }
    }

    public static int getExitStatus() {
        return Fim.exitStatus;
    }

    private List<AbstractCommand> buildCommands() {
        return Arrays.asList(
                new InitCommand(),
                new CommitCommand(),
                new StatusCommand(),
                new DiffCommand(),
                new ResetFileAttributesCommand(),
                new DetectCorruptionCommand(),
                new FindDuplicatesCommand(),
                new RemoveDuplicatesCommand(),
                new LogCommand(),
                new DisplayIgnoredFilesCommand(),
                new RollbackCommand(),
                new PurgeStatesCommand(),
                new HelpCommand(this),
                new VersionCommand());
    }

    private Options buildOptions() {
        Options opts = new Options();
        opts.addOption(buildOption("d", "directory", "Run Fim into the specified directory").hasArg().build());
        opts.addOption(buildOption("e", "errors", "Display execution error details").build());
        opts.addOption(buildOption("M", "master-fim-repository", """
                Fim repository directory that you want to use as remote master.
                Only for the 'remove-duplicates' command""").hasArg().build());
        opts.addOption(buildOption("n", "do-not-hash", "Do not hash file content. Uses only file names and modification dates").build());
        opts.addOption(buildOption("s", "super-fast-mode", """
                Use super-fast mode. Hash only 3 small blocks.
                One at the beginning, one in the middle and one at the end""").build());
        opts.addOption(buildOption("f", "fast-mode", """
                Use fast mode. Hash only 3 medium blocks.
                One at the beginning, one in the middle and one at the end""").build());
        opts.addOption(buildOption("h", "help", "Prints the Fim help").build());
        opts.addOption(buildOption("i", "ignore", """
                Ignore some difference during State comparison. You can ignore:
                - attrs: File attributes
                - dates: Modification and creation dates
                - renamed: Renamed files
                - all: All of the above
                You can specify multiple kind of difference to ignore separated by a comma.
                For example: -i attrs,dates,renamed""").hasArg().valueSeparator(',').build());
        opts.addOption(buildOption("l", "use-last-state", """
                Use the last committed State.
                Both for the 'find-duplicates' and 'remove-duplicates' commands""").build());
        opts.addOption(buildOption("m", "comment", "Comment to set during init and commit").hasArg().build());
        opts.addOption(buildOption("c", "", "Deprecated option used to set the init or commit comment. Use '-m' instead").hasArg().build());
        opts.addOption(buildOption("o", "output-max-lines", """
                Change the maximum number lines displayed for the same kind of modification.
                Default value is 200 lines""").hasArg().build());
        opts.addOption(buildOption("p", "purge-states", "Purge previous States if the commit succeed").build());
        opts.addOption(buildOption("q", "quiet", "Do not display details").build());
        opts.addOption(buildOption("t", "thread-count", """
                Number of thread used to hash file contents in parallel.
                By default, this number is dynamic and depends on the disk throughput""").hasArg().build());
        opts.addOption(buildOption("v", "version", "Prints the Fim version").build());
        opts.addOption(buildOption("y", "always-yes", "Always yes to every questions").build());
        opts.addOption(buildOption(null, "sort", """
                How to sort duplicate results.
                You can sort on:
                - wasted: wasted size (default)
                - number: number of files in the duplicated set
                - size: size of duplicated file""").hasArg().build());
        opts.addOption(buildOption(null, "order", "Sort order of duplicate results. Default is 'desc'. Can be 'asc' or 'desc'").hasArg().build());
        opts.addOption(
                buildOption(null, "include", "Include some directories/filetype while searching for duplicates. Separated by ':'").hasArg().build());
        opts.addOption(
                buildOption(null, "exclude", "Exclude some directories/filetype while searching for duplicates. Separated by ':'").hasArg().build());
        opts.addOption(buildOption(null, "output-type", """
                Output type used by 'fdup' to display duplicates. Supported types are:
                - human: display duplicates in human readable messages (default)
                - csv: display duplicates in CSV format
                - json: display duplicates in JSON format""").hasArg().build());
        return opts;
    }

    protected void run(String[] args, Context context) throws Exception {
        String[] filteredArgs = filterEmptyArgs(args);
        if (filteredArgs.length < 1) {
            youMustSpecifyACommandToRun(null);
        }

        context.setCalledFromTest(Fim.calledFromTest);

        Command command = null;
        String[] optionArgs = filteredArgs;
        String firstArg = filteredArgs[0];
        if (firstArg.startsWith("-")) {
            firstArg = null;
        } else {
            optionArgs = Arrays.copyOfRange(filteredArgs, 1, filteredArgs.length);
            command = findCommand(firstArg);
        }

        CommandLineParser cmdLineGnuParser = new DefaultParser();

        command = buildCommand(context, cmdLineGnuParser, optionArgs, command);
        if (command == null) {
            youMustSpecifyACommandToRun(firstArg);
        }

        FimReposConstraint constraint = command.getFimReposConstraint();
        if (constraint == FimReposConstraint.MUST_NOT_EXIST) {
            setRepositoryRootDir(context, context.getAbsoluteCurrentDirectory(), false);

            if (Files.exists(context.getRepositoryDotFimDir()) || Files.exists(context.getRepositoryStatesDir())) {
                Logger.error("Fim repository already exist");
                throw new BadFimUsageException();
            }

            if (!Files.isWritable(context.getRepositoryRootDir())) {
                Logger.error(
                        String.format("Not allowed to create the '%s' directory that holds the Fim repository", context.getRepositoryDotFimDir()));
                throw new RepositoryException();
            }
        } else if (constraint == FimReposConstraint.MUST_EXIST) {
            findRepositoryRootDir(context);

            if (!Files.exists(context.getRepositoryStatesDir())) {
                Logger.error("Fim repository does not exist. Please run 'fim init' before.");
                throw new BadFimUsageException();
            }

            if (!Files.isWritable(context.getRepositoryStatesDir())) {
                Logger.error(String.format("Not allowed to modify States into the '%s' directory", context.getRepositoryStatesDir()));
                throw new RepositoryException();
            }
            SettingsManager settingsManager = new SettingsManager(context);
            if (!Files.isWritable(settingsManager.getSettingsFile())) {
                Logger.error(String.format("Not allowed to save settings into the '%s' directory", context.getRepositoryDotFimDir()));
                throw new RepositoryException();
            }
        }

        command.execute(context.clone());
    }

    private Command buildCommand(Context context, CommandLineParser cmdLineGnuParser, String[] optionArgs, Command command) {
        try {
            CommandLine cmd = cmdLineGnuParser.parse(options, optionArgs);

            String ignoredKinds = cmd.getOptionValue('i');
            if (ignoredKinds != null) {
                parseIgnored(context, ignoredKinds);
            }
            if (cmd.hasOption('c')) {
                Logger.out.println("The '-c' option is deprecated and will be removed in the future. use '-m' instead\n");
                context.setComment(cmd.getOptionValue('c', context.getComment()));
            }

            context.setVerbose(!cmd.hasOption('q'));
            context.setComment(cmd.getOptionValue('m', context.getComment()));
            context.setUseLastState(cmd.hasOption('l'));
            context.setPurgeStates(cmd.hasOption('p'));
            context.setAlwaysYes(cmd.hasOption('y'));
            context.setDisplayStackTrace(cmd.hasOption('e'));

            manageMasterFimOption(context, cmd);

            if (cmd.hasOption('d')) {
                context.setCurrentDirectory(Paths.get(cmd.getOptionValue('d')));
            }

            if (cmd.hasOption('t')) {
                context.setThreadCount(Integer.parseInt(cmd.getOptionValue('t', "-1")));
                context.setThreadCountSpecified(true);
            }

            context.setDynamicScaling(context.getThreadCount() <= 0);

            context.setTruncateOutput(Integer.parseInt(cmd.getOptionValue('o', "200")));
            if (context.getTruncateOutput() < 0) {
                context.setTruncateOutput(0);
            }

            manageHashMode(context, cmd);

            manageSortOption(context, cmd);

            manageOrderOption(context, cmd);

            if (cmd.hasOption("include")) {
                context.setIncludePatterns(parseFilePatterns(cmd.getOptionValue("include")));
            }

            if (cmd.hasOption("exclude")) {
                context.setExcludePatterns(parseFilePatterns(cmd.getOptionValue("exclude")));
            }

            manageOutputTypeOption(context, cmd);

            if (cmd.hasOption('h')) {
                command = new HelpCommand(this);
            } else if (cmd.hasOption('v')) {
                command = new VersionCommand();
            }

        } catch (Exception ex) {
            Logger.error("Exception parsing command line", ex, context.isDisplayStackTrace());
            throw new BadFimUsageException();
        }
        return command;
    }

    private static void manageOutputTypeOption(Context context, CommandLine cmd) {
        if (cmd.hasOption("output-type")) {
            String outputType = cmd.getOptionValue("output-type");
            try {
                context.setOutputType(OutputType.valueOf(outputType.toLowerCase()));
            } catch (IllegalArgumentException ex) {
                Logger.error(String.format("Unsupported output type '%s'", outputType));
                throw new BadFimUsageException();
            }
            if (context.getOutputType() != OutputType.human) {
                context.setVerbose(false);
                Logger.level = Logger.Level.warning.ordinal();
            }
        }
    }

    private static void manageOrderOption(Context context, CommandLine cmd) {
        if (cmd.hasOption("order")) {
            String order = cmd.getOptionValue("order");
            switch (order.toLowerCase()) {
                case "asc" -> context.setSortAscending(true);
                case "desc" -> context.setSortAscending(false);
                default -> {
                    Logger.error(String.format("Unsupported sort order '%s'", order));
                    throw new BadFimUsageException();
                }
            }
        }
    }

    private static void manageSortOption(Context context, CommandLine cmd) {
        if (cmd.hasOption("sort")) {
            String sort = cmd.getOptionValue("sort");
            try {
                context.setSortMethod(SortMethod.valueOf(sort.toLowerCase()));
            } catch (IllegalArgumentException ex) {
                Logger.error(String.format("Unsupported sort method '%s'", sort));
                throw new BadFimUsageException();
            }
        }
    }

    private static void manageHashMode(Context context, CommandLine cmd) {
        if (cmd.hasOption('n')) {
            context.setHashMode(dontHash);
        } else if (cmd.hasOption('s')) {
            context.setHashMode(hashSmallBlock);
        } else if (cmd.hasOption('f')) {
            context.setHashMode(hashMediumBlock);
        } else {
            context.setHashMode(hashAll);
        }
    }

    private static void manageMasterFimOption(Context context, CommandLine cmd) {
        if (cmd.hasOption('M')) {
            String masterFimRepositoryDir = cmd.getOptionValue('M');
            if (!Files.exists(Paths.get(masterFimRepositoryDir))) {
                Logger.error(String.format("Master Fim repository directory '%s' does not exist", masterFimRepositoryDir));
                throw new BadFimUsageException();
            }
            context.setMasterFimRepositoryDir(masterFimRepositoryDir);
        }
    }

    private List<FilePattern> parseFilePatterns(String patterns) {
        List<FilePattern> filePatterns = new ArrayList<>();
        for (String pattern : patterns.split(":")) {
            filePatterns.add(new FilePattern(pattern));
        }
        return filePatterns;
    }

    private void parseIgnored(Context context, String ignoredKinds) {
        Ignored ignored = context.getIgnored();
        try (Scanner scanner = new Scanner(ignoredKinds)) {
            scanner.useDelimiter(",");
            while (scanner.hasNext()) {
                String token = scanner.next();
                if (token.isEmpty()) {
                    continue;
                }

                switch (token) {
                    case "attrs" -> ignored.setAttributesIgnored(true);
                    case "dates" -> ignored.setDatesIgnored(true);
                    case "renamed" -> ignored.setRenamedIgnored(true);
                    case "all" -> {
                        ignored.setAttributesIgnored(true);
                        ignored.setDatesIgnored(true);
                        ignored.setRenamedIgnored(true);
                    }
                    default -> {
                        Logger.error(String.format("'%s' unknown as difference kind to ignore.", token));
                        throw new BadFimUsageException();
                    }
                }
            }
        }
    }

    private void findRepositoryRootDir(Context context) {
        boolean invokedFromSubDirectory = false;
        Path directory = context.getAbsoluteCurrentDirectory();
        while (directory != null) {
            Path dotFimDir = directory.resolve(Context.DOT_FIM_DIR);
            if (Files.exists(dotFimDir)) {
                setRepositoryRootDir(context, directory, invokedFromSubDirectory);
                return;
            }

            directory = directory.getParent();
            invokedFromSubDirectory = true;
        }
    }

    private void setRepositoryRootDir(Context context, Path directory, boolean invokedFromSubDirectory) {
        context.setRepositoryRootDir(directory);
        context.setInvokedFromSubDirectory(invokedFromSubDirectory);
    }

    private Command findCommand(final String cmdName) {
        for (final Command command : commands) {
            if (command.getCmdName().equals(cmdName)) {
                return command;
            }

            if (!command.getShortCmdName().isEmpty() && command.getShortCmdName().equals(cmdName)) {
                return command;
            }
        }
        return null;
    }

    private Builder buildOption(String opt, String longOpt, String description) {
        Builder builder = Option.builder(opt);
        builder.longOpt(longOpt);
        builder.desc(description);
        return builder;
    }

    protected String[] filterEmptyArgs(String[] args) {
        List<String> filteredArgs = new ArrayList<>();
        for (String arg : args) {
            if (!arg.isEmpty()) {
                filteredArgs.add(arg);
            }
        }
        return filteredArgs.toArray(new String[0]);
    }

    private void youMustSpecifyACommandToRun(String firstArg) {
        if (firstArg != null) {
            Logger.error(String.format("'%s' is not a fim command. See 'fim --help'.", firstArg));
        } else {
            Logger.error("You must specify the command to run. See 'fim --help'");
        }
        throw new BadFimUsageException();
    }

    public void printUsage() {
        StringBuilder usage = new StringBuilder();
        usage.append("\n");
        usage.append("File Integrity Checker\n");
        usage.append("\n");
        usage.append("Available commands:\n");
        for (final Command command : commands) {
            String cmdName;
            if (command.getShortCmdName() != null && !command.getShortCmdName().isEmpty()) {
                cmdName = command.getShortCmdName() + " / " + command.getCmdName();
            } else {
                cmdName = command.getCmdName();
            }
            usage.append(String.format("     %-26s %s%n", cmdName, command.getDescription()));
        }

        usage.append("\n");
        usage.append("Available options:\n");

        PrintWriter writer = new PrintWriter(Logger.out);
        HelpFormatter helpFormatter = new HelpFormatter();

        Logger.newLine();
        helpFormatter.printHelp(writer, 120, "fim <command>", usage.toString(), options, 5, 3, "", true);
        writer.flush();
        Logger.newLine();
    }
}
