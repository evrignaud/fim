/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2016  Etienne Vrignaud
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
 * along with Fim.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.fim;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
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
import org.fim.command.VersionCommand;
import org.fim.command.exception.BadFimUsageException;
import org.fim.command.exception.DontWantToContinueException;
import org.fim.command.exception.RepositoryException;
import org.fim.internal.SettingsManager;
import org.fim.model.Command;
import org.fim.model.Command.FimReposConstraint;
import org.fim.model.Context;
import org.fim.util.Console;
import org.fim.util.Logger;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.fim.model.HashMode.dontHash;
import static org.fim.model.HashMode.hashAll;
import static org.fim.model.HashMode.hashMediumBlock;
import static org.fim.model.HashMode.hashSmallBlock;

public class Fim {
    private List<AbstractCommand> commands = buildCommands();
    private Options options = buildOptions();

    public static void main(String[] args) throws Exception {
        try {
            Fim fim = new Fim();
            Context context = new Context();
            fim.run(args, context);
        } catch (DontWantToContinueException ex) {
            System.exit(0);
        } catch (BadFimUsageException ex) {
            System.exit(-1);
        } catch (RepositoryException ex) {
            System.exit(-2);
        }

        System.exit(0);
    }

    private List<AbstractCommand> buildCommands() {
        return Arrays.asList(
            new InitCommand(),
            new CommitCommand(),
            new DiffCommand(),
            new ResetFileAttributesCommand(),
            new DetectCorruptionCommand(),
            new FindDuplicatesCommand(),
            new RemoveDuplicatesCommand(this),
            new LogCommand(),
            new DisplayIgnoredFilesCommand(),
            new RollbackCommand(),
            new PurgeStatesCommand(),
            new HelpCommand(this),
            new VersionCommand());
    }

    private Options buildOptions() {
        Options options = new Options();
        options.addOption(createOption("d", "directory", true, "Run Fim into the specified directory", false));
        options.addOption(createOption("e", "errors", false, "Display execution error details", false));
        options.addOption(createOption("m", "master-fim-repository", true, "Fim repository directory that you want to use as remote master.\nOnly for the remove duplicated files command", false));
        options.addOption(createOption("n", "do-not-hash", false, "Do not hash file content. Uses only file names and modification dates", false));
        options.addOption(createOption("s", "super-fast-mode", false, "Use super-fast mode. Hash only 3 small blocks.\nOne at the beginning, one in the middle and one at the end", false));
        options.addOption(createOption("f", "fast-mode", false, "Use fast mode. Hash only 3 medium blocks.\nOne at the beginning, one in the middle and one at the end", false));
        options.addOption(createOption("h", "help", false, "Prints the Fim help", false));
        options.addOption(createOption("l", "use-last-state", false, "Use the last committed State.\nOnly for the find local duplicated files command", false));
        options.addOption(createOption("c", "comment", true, "Comment to set during init and commit", false));
        options.addOption(createOption("o", "output-max-lines", true, "Change the maximum number lines displayed for the same kind of modification. Default value is 200 lines", false));
        options.addOption(createOption("p", "purge-states", false, "Purge previous States if the commit succeed", false));
        options.addOption(createOption("q", "quiet", false, "Do not display details", false));
        options.addOption(createOption("t", "thread-count", true, "Number of thread used to hash file contents in parallel", false));
        options.addOption(createOption("v", "version", false, "Prints the Fim version", false));
        options.addOption(createOption("y", "always-yes", false, "Always yes to every questions", false));
        return options;
    }

    protected void run(String[] args, Context context) throws Exception {
        String[] filteredArgs = filterEmptyArgs(args);
        if (filteredArgs.length < 1) {
            youMustSpecifyACommandToRun(null);
        }

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

        try {
            CommandLine commandLine = cmdLineGnuParser.parse(options, optionArgs);

            context.setVerbose(!commandLine.hasOption('q'));
            context.setComment(commandLine.getOptionValue('c', context.getComment()));
            context.setUseLastState(commandLine.hasOption('l'));
            context.setMasterFimRepositoryDir(commandLine.getOptionValue('m'));
            context.setPurgeStates(commandLine.hasOption('p'));
            context.setAlwaysYes(commandLine.hasOption('y'));
            context.setDisplayStackTrace(commandLine.hasOption('e'));

            if (commandLine.hasOption('d')) {
                context.setCurrentDirectory(Paths.get(commandLine.getOptionValue('d')));
            }

            if (commandLine.hasOption('t')) {
                context.setThreadCount(Integer.parseInt(commandLine.getOptionValue('t', "1")));
                context.setThreadCountSpecified(true);
            }

            context.setTruncateOutput(Integer.parseInt(commandLine.getOptionValue('o', "200")));
            if (context.getTruncateOutput() < 0) {
                context.setTruncateOutput(0);
            }

            if (commandLine.hasOption('n')) {
                context.setHashMode(dontHash);
            } else if (commandLine.hasOption('s')) {
                context.setHashMode(hashSmallBlock);
            } else if (commandLine.hasOption('f')) {
                context.setHashMode(hashMediumBlock);
            } else {
                context.setHashMode(hashAll);
            }

            if (commandLine.hasOption('h')) {
                command = new HelpCommand(this);
            } else if (commandLine.hasOption('v')) {
                command = new VersionCommand();
            }

        } catch (Exception ex) {
            Logger.error("Exception parsing command line", ex, context.isDisplayStackTrace());
            printUsage();
            throw new BadFimUsageException();
        }

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
                Logger.error(String.format("Not allowed to create the '%s' directory that holds the Fim repository", context.getRepositoryDotFimDir()));
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

            if (command.getShortCmdName().length() > 0 && command.getShortCmdName().equals(cmdName)) {
                return command;
            }
        }
        return null;
    }

    private Option createOption(String opt, String longOpt, boolean hasArg, String description, boolean required) {
        Option option = new Option(opt, longOpt, hasArg, description);
        option.setRequired(required);
        return option;
    }

    private String[] filterEmptyArgs(String[] args) {
        List<String> filteredArgs = new ArrayList<>();
        for (String arg : args) {
            if (arg.length() > 0) {
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
            if (command.getShortCmdName() != null && command.getShortCmdName().length() > 0) {
                cmdName = command.getShortCmdName() + " / " + command.getCmdName();
            } else {
                cmdName = command.getCmdName();
            }
            usage.append(String.format("     %-26s %s\n", cmdName, command.getDescription()));
        }

        usage.append("\n");
        usage.append("Available options:\n");

        PrintWriter writer = new PrintWriter(System.out);
        HelpFormatter helpFormatter = new HelpFormatter();

        Console.newLine();
        helpFormatter.printHelp(writer, 120, "fim <command>", usage.toString(), options, 5, 3, "", true);
        writer.flush();
        Console.newLine();
    }
}
