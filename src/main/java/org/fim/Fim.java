/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2015  Etienne Vrignaud
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

import static org.fim.model.HashMode.dontHash;
import static org.fim.model.HashMode.hashAll;
import static org.fim.model.HashMode.hashMediumBlock;
import static org.fim.model.HashMode.hashSmallBlock;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.fim.command.AbstractCommand;
import org.fim.command.CommitCommand;
import org.fim.command.CorruptCommand;
import org.fim.command.DiffCommand;
import org.fim.command.DisplayIgnoredFilesCommand;
import org.fim.command.FindDuplicatesCommand;
import org.fim.command.HelpCommand;
import org.fim.command.InitCommand;
import org.fim.command.LogCommand;
import org.fim.command.PurgeStatesCommand;
import org.fim.command.RemoveDuplicatesCommand;
import org.fim.command.ResetDateCommand;
import org.fim.command.RollbackCommand;
import org.fim.command.VersionCommand;
import org.fim.command.exception.BadFimUsageException;
import org.fim.command.exception.DontWantToContinueException;
import org.fim.command.exception.RepositoryCreationException;
import org.fim.model.Command;
import org.fim.model.Command.FimReposConstraint;
import org.fim.model.Context;
import org.fim.util.Console;
import org.fim.util.Logger;

public class Fim
{
	private static List<AbstractCommand> commands = buildCommands();
	private static Options options = buildOptions();

	private static List<AbstractCommand> buildCommands()
	{
		return Arrays.asList(
				new InitCommand(),
				new CommitCommand(),
				new DiffCommand(),
				new ResetDateCommand(),
				new CorruptCommand(),
				new FindDuplicatesCommand(),
				new RemoveDuplicatesCommand(),
				new LogCommand(),
				new DisplayIgnoredFilesCommand(),
				new RollbackCommand(),
				new PurgeStatesCommand(),
				new HelpCommand(),
				new VersionCommand());
	}

	private static Options buildOptions()
	{
		Options options = new Options();
		options.addOption(createOption("a", "master-fim-repository", true, "Fim repository directory that you want to use as remote master.\nOnly for the remove duplicated files command", false));
		options.addOption(createOption("n", "do-not-hash", false, "Do not hash file content. Use only file names and modification dates", false));
		options.addOption(createOption("s", "super-fast-mode", false, "Hash only 3 small blocks.\nOne at the beginning, one in the middle and one at the end", false));
		options.addOption(createOption("f", "fast-mode", false, "Hash only 3 medium blocks.\nOne at the beginning, one in the middle and one at the end", false));
		options.addOption(createOption("h", "help", false, "Prints the Fim help", false));
		options.addOption(createOption("l", "use-last-state", false, "Use the last committed State.\nOnly for the find local duplicated files command", false));
		options.addOption(createOption("c", "comment", true, "Sets that State comment during init and commit", false));
		options.addOption(createOption("q", "quiet", false, "Do not display details", false));
		options.addOption(createOption("t", "thread-count", true, "Number of thread used to hash files content in parallel", false));
		options.addOption(createOption("v", "version", false, "Prints the Fim version", false));
		options.addOption(createOption("y", "always-yes", false, "Always yes to every questions", false));
		return options;
	}

	public static void main(String[] args) throws Exception
	{
		String[] filteredArgs = filterEmptyArgs(args);
		if (filteredArgs.length < 1)
		{
			youMustSpecifyACommandToRun();
		}

		Command command = null;
		String[] optionArgs = filteredArgs;
		String firstArg = filteredArgs[0];
		if (!firstArg.startsWith("-"))
		{
			optionArgs = Arrays.copyOfRange(filteredArgs, 1, filteredArgs.length);
			command = findCommand(firstArg);
		}

		CommandLineParser cmdLineGnuParser = new DefaultParser();
		Context context = new Context();

		try
		{
			CommandLine commandLine = cmdLineGnuParser.parse(options, optionArgs);

			context.setVerbose(!commandLine.hasOption('q'));
			context.setComment(commandLine.getOptionValue('c', context.getComment()));
			context.setThreadCount(Integer.parseInt(commandLine.getOptionValue('t', "" + context.getThreadCount())));
			context.setUseLastState(commandLine.hasOption('l'));
			context.setMasterFimRepositoryDir(commandLine.getOptionValue('a'));
			context.setAlwaysYes(commandLine.hasOption('y'));

			if (commandLine.hasOption('n'))
			{
				context.setHashMode(dontHash);
			}
			else if (commandLine.hasOption('s'))
			{
				context.setHashMode(hashSmallBlock);
			}
			else if (commandLine.hasOption('f'))
			{
				context.setHashMode(hashMediumBlock);
			}
			else
			{
				context.setHashMode(hashAll);
			}

			if (commandLine.hasOption('h'))
			{
				command = new HelpCommand();
			}
			else if (commandLine.hasOption('v'))
			{
				command = new VersionCommand();
			}

		}
		catch (Exception ex)
		{
			Logger.error(ex.getMessage());
			printUsage();
			System.exit(-1);
		}

		if (command == null)
		{
			youMustSpecifyACommandToRun();
		}

		if (context.getThreadCount() < 1)
		{
			Logger.error("Thread count must be at least one");
			System.exit(-1);
		}

		if (context.getThreadCount() != 1 && context.getHashMode() == dontHash)
		{
			context.setThreadCount(1);
			Logger.info("Not hashing file content so thread count forced to 1");
		}

		FimReposConstraint constraint = command.getFimReposConstraint();
		if (constraint == FimReposConstraint.MUST_NOT_EXIST)
		{
			setRepositoryRootDir(context, getAbsoluteCurrentDirectory(), false);

			if (Files.exists(context.getRepositoryStatesDir()))
			{
				Logger.error("Fim repository already exist");
				System.exit(0);
			}
		}
		else if (constraint == FimReposConstraint.MUST_EXIST)
		{
			findRepositoryRootDir(context);

			if (!Files.exists(context.getRepositoryStatesDir()))
			{
				Logger.error("Fim repository does not exist. Please run 'fim init' before.");
				System.exit(-1);
			}
		}

		try
		{
			command.execute(context.clone());
		}
		catch (DontWantToContinueException ex)
		{
			System.exit(0);
		}
		catch (BadFimUsageException | RepositoryCreationException ex)
		{
			System.exit(-1);
		}

		System.exit(0);
	}

	private static void findRepositoryRootDir(Context context)
	{
		boolean invokedFromSubDirectory = false;
		Path directory = getAbsoluteCurrentDirectory();
		while (directory != null)
		{
			Path dotFimDir = directory.resolve(Context.DOT_FIM_DIR);
			if (Files.exists(dotFimDir))
			{
				setRepositoryRootDir(context, directory, invokedFromSubDirectory);
				return;
			}

			directory = directory.getParent();
			invokedFromSubDirectory = true;
		}
	}

	private static void setRepositoryRootDir(Context context, Path directory, boolean invokedFromSubDirectory)
	{
		context.setRepositoryRootDir(directory);
		context.setInvokedFromSubDirectory(invokedFromSubDirectory);
	}

	private static Path getAbsoluteCurrentDirectory()
	{
		return Paths.get(".").toAbsolutePath().normalize();
	}

	private static Command findCommand(final String cmdName)
	{
		for (final Command command : commands)
		{
			if (command.getCmdName().equals(cmdName))
			{
				return command;
			}

			if (command.getShortCmdName().length() > 0 && command.getShortCmdName().equals(cmdName))
			{
				return command;
			}
		}
		return null;
	}

	private static Option createOption(String opt, String longOpt, boolean hasArg, String description, boolean required)
	{
		Option option = new Option(opt, longOpt, hasArg, description);
		option.setRequired(required);
		return option;
	}

	private static String[] filterEmptyArgs(String[] args)
	{
		List<String> filteredArgs = new ArrayList<>();
		for (String arg : args)
		{
			if (arg.length() > 0)
			{
				filteredArgs.add(arg);
			}
		}
		return filteredArgs.toArray(new String[0]);
	}

	private static void youMustSpecifyACommandToRun()
	{
		Logger.error("You must specify the command to run");
		printUsage();
		System.exit(-1);
	}

	public static void printUsage()
	{
		StringBuilder usage = new StringBuilder();
		usage.append("\n");
		usage.append("File Integrity Checker\n");
		usage.append("\n");
		usage.append("Available commands:\n");
		for (final Command command : commands)
		{
			String cmdName;
			if (command.getShortCmdName() != null && command.getShortCmdName().length() > 0)
			{
				cmdName = command.getShortCmdName() + " / " + command.getCmdName();
			}
			else
			{
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
