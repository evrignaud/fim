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
import org.fim.command.DiffCommand;
import org.fim.command.FindDuplicatesCommand;
import org.fim.command.HelpCommand;
import org.fim.command.InitCommand;
import org.fim.command.LogCommand;
import org.fim.command.PurgeStatesCommand;
import org.fim.command.RemoveDuplicatesCommand;
import org.fim.command.ResetDatesCommand;
import org.fim.command.VersionCommand;
import org.fim.model.Command;
import org.fim.model.Command.FimReposConstraint;
import org.fim.model.Context;
import org.fim.model.HashMode;

public class Main
{
	private static List<AbstractCommand> commands = buildCommands();
	private static Options options = buildOptions();

	private static List<AbstractCommand> buildCommands()
	{
		return Arrays.asList(
				new InitCommand(),
				new CommitCommand(),
				new DiffCommand(),
				new ResetDatesCommand(),
				new FindDuplicatesCommand(),
				new RemoveDuplicatesCommand(),
				new LogCommand(),
				new PurgeStatesCommand(),
				new HelpCommand(),
				new VersionCommand());
	}

	private static Options buildOptions()
	{
		Options options = new Options();
		options.addOption(createOption("a", "master-fim-repository", true, "Fim repository directory that you want to use as remote master.\nOnly for the remove duplicated files command", false));
		options.addOption(createOption("f", "fast-mode", false, "Do not hash file content. Use only filenames and modification dates", false));
		options.addOption(createOption("k", "hash-first-four-kilos", false, "Hash the first four kilos of the files", false));
		options.addOption(createOption("m", "hash-first-mega", false, "Hash the first mega of the files", false));
		options.addOption(createOption("h", "help", false, "Prints the Fim help", false));
		options.addOption(createOption("l", "use-last-state", false, "Use the last committed State", false));
		options.addOption(createOption("c", "comment", true, "Sets that State comment during init and commit", false));
		options.addOption(createOption("q", "quiet", false, "Do not display details", false));
		options.addOption(createOption("t", "thread-count", true, "Number of thread to use to hash files content in parallel", false));
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

			if (commandLine.hasOption('f'))
			{
				context.setHashMode(HashMode.dontHashFiles);
			}
			else if (commandLine.hasOption('k'))
			{
				context.setHashMode(HashMode.hashSmallBlock);
			}
			else if (commandLine.hasOption('m'))
			{
				context.setHashMode(HashMode.hashMediumBlock);
			}
			else
			{
				context.setHashMode(HashMode.computeAllHash);
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
			System.err.println(ex.getMessage());
			printUsage();
			System.exit(-1);
		}

		if (command == null)
		{
			youMustSpecifyACommandToRun();
		}

		if (context.getThreadCount() < 1)
		{
			System.err.println("Thread count must be at least one");
			System.exit(-1);
		}

		if (context.getThreadCount() != 1 && context.getHashMode() == HashMode.dontHashFiles)
		{
			context.setThreadCount(1);
			System.out.println("Not hashing file content so thread count forced to 1");
		}

		setRepositoryRootDir(context);

		FimReposConstraint constraint = command.getFimReposConstraint();
		if (constraint == FimReposConstraint.MUST_NOT_EXIST)
		{
			if (Files.exists(context.getRepositoryStatesDir()))
			{
				System.err.println("Fim repository already exist");
				System.exit(0);
			}
		}
		else if (constraint == FimReposConstraint.MUST_EXIST)
		{
			if (!Files.exists(context.getRepositoryStatesDir()))
			{
				System.err.println("Fim repository does not exist. Please run 'fim init' before.");
				System.exit(-1);
			}
		}

		command.execute((Context) context.clone());
	}

	private static void setRepositoryRootDir(Context context)
	{
		boolean invokedFromSubDirectory = false;
		Path directory = Paths.get(".").toAbsolutePath().normalize();
		while (directory != null)
		{
			Path dotFimDir = directory.resolve(Context.DOT_FIM_DIR);
			if (Files.exists(dotFimDir))
			{
				context.setRepositoryRootDir(directory);
				context.setInvokedFromSubDirectory(invokedFromSubDirectory);
				return;
			}

			directory = directory.getParent();
			invokedFromSubDirectory = true;
		}
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
		System.err.println("You must specify the command to run");
		printUsage();
		System.exit(-1);
	}

	public static void printUsage()
	{
		System.out.println("");
		PrintWriter writer = new PrintWriter(System.out);
		HelpFormatter helpFormatter = new HelpFormatter();

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

		helpFormatter.printHelp(writer, 120, "fim <command>", usage.toString(), options, 5, 3, "", true);
		writer.flush();
		System.out.println("");
	}
}
