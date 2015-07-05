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
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.fim.model.CompareMode;
import org.fim.model.CompareResult;
import org.fim.model.State;

public class Main
{
	/**
	 * Construct Options.
	 */
	public static Options constructOptions()
	{
		Options options = new Options();
		options.addOption(createOption("q", "quiet", false, "Do not display details", false));
		options.addOption(createOption("f", "fastCompare", false, "Compare only filenames and modification dates", false));
		options.addOption(createOption("m", "message", true, "Message to store with the State", false));
		options.addOption(createOption("t", "threadCount", true, "Number of thread to use to hash files content in parallel", false));
		options.addOption(createOption("l", "useLastState", false, "Use the last committed State", false));
		return options;
	}

	public static Option createOption(String opt, String longOpt, boolean hasArg, String description, boolean required)
	{
		Option option = new Option(opt, longOpt, hasArg, description);
		option.setRequired(required);
		return option;
	}

	public static void main(String[] args) throws IOException
	{
		String[] filteredArgs = filterEmptyArgs(args);
		if (filteredArgs.length < 1)
		{
			youMustSpecifyACommandToRun();
		}

		Command command = Command.fromName(filteredArgs[0]);
		if (command == null)
		{
			youMustSpecifyACommandToRun();
		}

		CommandLineParser cmdLineGnuParser = new GnuParser();

		Options options = constructOptions();
		CommandLine commandLine;

		boolean verbose = true;
		CompareMode compareMode = CompareMode.FULL;
		String message = "";
		boolean useLastState = false;
		int threadCount = Runtime.getRuntime().availableProcessors();

		try
		{
			String[] actionArgs = Arrays.copyOfRange(filteredArgs, 1, filteredArgs.length);
			commandLine = cmdLineGnuParser.parse(options, actionArgs);
			if (commandLine.hasOption("h"))
			{
				printUsage();
				System.exit(0);
			}
			else
			{
				verbose = !commandLine.hasOption('q');
				compareMode = commandLine.hasOption('f') ? CompareMode.FAST : CompareMode.FULL;
				message = commandLine.getOptionValue('m', message);
				threadCount = Integer.parseInt(commandLine.getOptionValue('t', "" + threadCount));
				useLastState = commandLine.hasOption('l');
			}
		}
		catch (Exception ex)
		{
			printUsage();
			System.exit(-1);
		}

		if (compareMode == CompareMode.FAST)
		{
			threadCount = 1;
			System.out.println("Using fast compare mode. Thread count forced to 1");
		}

		if (threadCount < 1)
		{
			System.out.println("Thread count must be at least one");
			System.exit(0);
		}

		File baseDirectory = new File(".");
		File stateDir = new File(StateGenerator.FIC_DIR, "states");

		if (command == Command.INIT)
		{
			if (stateDir.exists())
			{
				System.out.println("fim repository already exist");
				System.exit(0);
			}
		}
		else
		{
			if (!stateDir.exists())
			{
				System.out.println("fim repository does not exist. Please run 'fim init' before.");
				System.exit(-1);
			}
		}

		State previousState;
		State currentState;

		StateGenerator generator = new StateGenerator(threadCount, compareMode);
		StateManager manager = new StateManager(stateDir, compareMode);
		StateComparator comparator = new StateComparator(compareMode);
		DuplicateFinder finder = new DuplicateFinder();

		switch (command)
		{
			case INIT:
				fastCompareNotSupported(compareMode);

				stateDir.mkdirs();
				currentState = generator.generateState("Initial State", baseDirectory);
				comparator.compare(null, currentState).displayChanges(verbose);
				manager.createNewState(currentState);
				break;

			case COMMIT:
				fastCompareNotSupported(compareMode);

				previousState = manager.loadPreviousState();
				currentState = generator.generateState(message, baseDirectory);
				CompareResult result = comparator.compare(previousState, currentState).displayChanges(verbose);
				if (result.somethingModified())
				{
					System.out.println("");
					if (confirmCommand("commit"))
					{
						manager.createNewState(currentState);
					}
					else
					{
						System.out.println("Nothing committed");
					}
				}
				break;

			case DIFF:
				previousState = manager.loadPreviousState();
				currentState = generator.generateState(message, baseDirectory);
				comparator.compare(previousState, currentState).displayChanges(verbose);
				break;

			case FIND_DUPLICATES:
				fastCompareNotSupported(compareMode);

				System.out.println("Searching for duplicated files" + (useLastState ? " from the last committed State" : ""));
				System.out.println("");
				State state;
				if (useLastState)
				{
					state = manager.loadPreviousState();
				}
				else
				{
					state = generator.generateState(message, baseDirectory);
				}
				finder.findDuplicates(state).displayDuplicates(verbose);
				break;

			case RESET_DATES:
				fastCompareNotSupported(compareMode);

				previousState = manager.loadPreviousState();
				manager.resetDates(previousState);
				break;

			case LOG:
				manager.displayStatesLog();
				break;
		}
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

	private static void fastCompareNotSupported(CompareMode compareMode)
	{
		if (compareMode == CompareMode.FAST)
		{
			System.out.println("Fast compare mode not supported by this command.");
			System.exit(-1);
		}
	}

	private static boolean confirmCommand(String command)
	{
		Scanner scanner = new Scanner(System.in);
		System.out.printf("Do you really want to %s (y/n)? ", command);
		String str = scanner.next();
		if (str.equalsIgnoreCase("y"))
		{
			return true;
		}
		return false;
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
		Options options = constructOptions();
		PrintWriter writer = new PrintWriter(System.out);
		HelpFormatter helpFormatter = new HelpFormatter();

		String usage = "\nAvailable commands:\n";
		for (final Command command : Command.values())
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
