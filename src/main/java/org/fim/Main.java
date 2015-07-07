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

import java.io.File;
import java.io.PrintWriter;
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
import org.fim.command.RemoveDuplicatesCommand;
import org.fim.command.ResetDatesCommand;
import org.fim.internal.StateGenerator;
import org.fim.model.Command;
import org.fim.model.CompareMode;
import org.fim.model.FimOptions;

public class Main
{
	private static List<AbstractCommand> commands;
	private static Options options;

	private static List<AbstractCommand> getCommands()
	{
		return Arrays.asList(
				new InitCommand(),
				new CommitCommand(),
				new DiffCommand(),
				new FindDuplicatesCommand(),
				new RemoveDuplicatesCommand(),
				new LogCommand(),
				new ResetDatesCommand(),
				new HelpCommand());
	}

	private static Options getOptions()
	{
		Options options = new Options();
		options.addOption(createOption("q", "quiet", false, "Do not display details", false));
		options.addOption(createOption("f", "fastCompare", false, "Compare only filenames and modification dates", false));
		options.addOption(createOption("m", "message", true, "Message to store with the State", false));
		options.addOption(createOption("t", "threadCount", true, "Number of thread to use to hash files content in parallel", false));
		options.addOption(createOption("l", "useLastState", false, "Use the last committed State", false));
		options.addOption(createOption("d", "fimRepositoryDirectory", true, "Directory of a Fim repository that you want to use as master. Only for the remove duplicates command", false));
		options.addOption(createOption("y", "alwaysYes", false, "Always yes to every questions", false));
		return options;
	}

	public static void main(String[] args) throws Exception
	{
		commands = getCommands();
		options = getOptions();

		String[] filteredArgs = filterEmptyArgs(args);
		if (filteredArgs.length < 1)
		{
			youMustSpecifyACommandToRun();
		}

		Command command = findCommand(filteredArgs[0]);
		if (command == null)
		{
			youMustSpecifyACommandToRun();
		}

		CommandLineParser cmdLineGnuParser = new DefaultParser();

		CommandLine commandLine;

		FimOptions fimOptions = new FimOptions();

		try
		{
			String[] actionArgs = Arrays.copyOfRange(filteredArgs, 1, filteredArgs.length);
			commandLine = cmdLineGnuParser.parse(options, actionArgs);

			fimOptions.setVerbose(!commandLine.hasOption('q'));
			fimOptions.setCompareMode(commandLine.hasOption('f') ? CompareMode.FAST : CompareMode.FULL);
			fimOptions.setMessage(commandLine.getOptionValue('m', fimOptions.getMessage()));
			fimOptions.setThreadCount(Integer.parseInt(commandLine.getOptionValue('t', "" + fimOptions.getThreadCount())));
			fimOptions.setUseLastState(commandLine.hasOption('l'));
			fimOptions.setFimRepositoryDirectory(commandLine.getOptionValue('d'));
			fimOptions.setAlwaysYes(commandLine.hasOption('y'));
		}
		catch (Exception ex)
		{
			printUsage();
			System.exit(-1);
		}

		if (fimOptions.getCompareMode() == CompareMode.FAST)
		{
			fimOptions.setThreadCount(1);
			System.out.println("Using fast compare mode. Thread count forced to 1");
		}

		if (fimOptions.getThreadCount() < 1)
		{
			System.out.println("Thread count must be at least one");
			System.exit(-1);
		}

		fimOptions.setBaseDirectory(new File("."));
		fimOptions.setStateDir(new File(StateGenerator.FIM_DIR, "states"));

		if (command.getCmdName().equals("init"))
		{
			if (fimOptions.getStateDir().exists())
			{
				System.out.println("fim repository already exist");
				System.exit(0);
			}
		}
		else
		{
			if (!fimOptions.getStateDir().exists())
			{
				System.out.println("fim repository does not exist. Please run 'fim init' before.");
				System.exit(-1);
			}
		}

		command.execute(fimOptions);
	}

	private static Command findCommand(final String cmdName)
	{
		for (final Command command : commands)
		{
			if (command.getCmdName().equals(cmdName))
			{
				return command;
			}

			if (command.getShortCmdName().equals(cmdName))
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
		System.out.println("You must specify the command to run");
		printUsage();
		System.exit(-1);
	}

	public static void printUsage()
	{
		System.out.println("");
		PrintWriter writer = new PrintWriter(System.out);
		HelpFormatter helpFormatter = new HelpFormatter();

		String usage = "\nAvailable commands:\n";
		for (final Command command : commands)
		{
			if (command.getShortCmdName() != null && command.getShortCmdName().length() > 0)
			{
				usage += String.format("- %s / %s: %s\n", command.getShortCmdName(), command.getCmdName(), command.getDescription());
			}
			else
			{
				usage += String.format("- %s: %s\n", command.getCmdName(), command.getDescription());
			}
		}

		helpFormatter.printHelp(writer, 110, "fim <command>", "\nFile Integrity Checker\n", options, 5, 3, usage, true);
		writer.flush();
		System.out.println("");
	}
}
