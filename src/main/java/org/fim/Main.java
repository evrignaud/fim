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
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.fim.model.CompareMode;
import org.fim.model.CompareResult;
import org.fim.model.FileState;
import org.fim.model.FimOptions;
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
		options.addOption(createOption("d", "fimRepositoryDirectory", true, "Directory of a Fim repository that you want to use as master. Only for the remove duplicates command", false));
		options.addOption(createOption("y", "alwaysYes", false, "Always yes to every questions", false));
		return options;
	}

	public static Option createOption(String opt, String longOpt, boolean hasArg, String description, boolean required)
	{
		Option option = new Option(opt, longOpt, hasArg, description);
		option.setRequired(required);
		return option;
	}

	public static void main(String[] args) throws IOException, NoSuchAlgorithmException
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

		CommandLineParser cmdLineGnuParser = new DefaultParser();

		Options options = constructOptions();
		CommandLine commandLine;

		FimOptions fimOptions = new FimOptions();

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
				fimOptions.setVerbose(!commandLine.hasOption('q'));
				fimOptions.setCompareMode(commandLine.hasOption('f') ? CompareMode.FAST : CompareMode.FULL);
				fimOptions.setMessage(commandLine.getOptionValue('m', fimOptions.getMessage()));
				fimOptions.setThreadCount(Integer.parseInt(commandLine.getOptionValue('t', "" + fimOptions.getThreadCount())));
				fimOptions.setUseLastState(commandLine.hasOption('l'));
				fimOptions.setFimRepositoryDirectory(commandLine.getOptionValue('d'));
				fimOptions.setAlwaysYes(commandLine.hasOption('y'));

				if (command == Command.REMOVE_DUPLICATES && fimOptions.getFimRepositoryDirectory() == null)
				{
					System.out.println("The Fim repository directory must be provided");
					printUsage();
					System.exit(-1);
				}
			}
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

		File baseDirectory = new File(".");
		File stateDir = new File(StateGenerator.FIM_DIR, "states");

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

		State state;
		State lastState;
		State currentState;

		StateGenerator generator = new StateGenerator(fimOptions.getThreadCount(), fimOptions.getCompareMode());
		StateManager manager = new StateManager(stateDir, fimOptions.getCompareMode());
		StateComparator comparator = new StateComparator(fimOptions.getCompareMode());
		DuplicateFinder finder = new DuplicateFinder();

		switch (command)
		{
			case INIT:
				fastCompareNotSupported(fimOptions.getCompareMode());

				stateDir.mkdirs();
				currentState = generator.generateState("Initial State", baseDirectory);
				comparator.compare(null, currentState).displayChanges(fimOptions.isVerbose());
				manager.createNewState(currentState);
				break;

			case COMMIT:
				fastCompareNotSupported(fimOptions.getCompareMode());

				lastState = manager.loadLastState();
				currentState = generator.generateState(fimOptions.getMessage(), baseDirectory);
				CompareResult result = comparator.compare(lastState, currentState).displayChanges(fimOptions.isVerbose());
				if (result.somethingModified())
				{
					System.out.println("");
					if (fimOptions.isAlwaysYes() || confirmCommand("commit"))
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
				lastState = manager.loadLastState();
				currentState = generator.generateState(fimOptions.getMessage(), baseDirectory);
				comparator.compare(lastState, currentState).displayChanges(fimOptions.isVerbose());
				break;

			case FIND_DUPLICATES:
				fastCompareNotSupported(fimOptions.getCompareMode());

				System.out.println("Searching for duplicated files" + (fimOptions.isUseLastState() ? " from the last committed State" : ""));
				System.out.println("");
				if (fimOptions.isUseLastState())
				{
					state = manager.loadLastState();
				}
				else
				{
					state = generator.generateState(fimOptions.getMessage(), baseDirectory);
				}
				finder.findDuplicates(state).displayDuplicates(fimOptions.isVerbose());
				break;

			case REMOVE_DUPLICATES:
				fastCompareNotSupported(fimOptions.getCompareMode());

				File repository = new File(fimOptions.getFimRepositoryDirectory());
				if (!repository.exists())
				{
					System.out.printf("Directory %s does not exist%n", fimOptions.getFimRepositoryDirectory());
					System.exit(-1);
				}

				if (repository.getCanonicalPath().equals(baseDirectory.getCanonicalPath()))
				{
					System.out.printf("Cannot remove duplicates from the current directory%n");
					System.exit(-1);
				}

				File fimDir = new File(repository, StateGenerator.FIM_DIR);
				if (!fimDir.exists())
				{
					System.out.printf("Directory %s is not a Fim repository%n", fimOptions.getFimRepositoryDirectory());
					System.exit(-1);
				}

				System.out.println("Searching for duplicated files using the " + fimOptions.getFimRepositoryDirectory() + " directory as master");
				System.out.println("");

				File otherStateDir = new File(fimDir, "states");
				StateManager otherManager = new StateManager(otherStateDir, CompareMode.FULL);
				State otherState = otherManager.loadLastState();
				Map<String, FileState> otherHashes = new HashMap<>();
				for (FileState otherFileState : otherState.getFileStates())
				{
					otherHashes.put(otherFileState.getHash(), otherFileState);
				}

				State localState = generator.generateState(fimOptions.getMessage(), baseDirectory);
				for (FileState localFileState : localState.getFileStates())
				{
					FileState otherFileState = otherHashes.get(localFileState.getHash());
					if (otherFileState != null)
					{
						System.out.printf("%s is a duplicate of %s/%s%n", localFileState.getFileName(), fimOptions.getFimRepositoryDirectory(), otherFileState.getFileName());
						if (fimOptions.isAlwaysYes() || confirmCommand("remove it"))
						{
							System.out.printf("  %s removed%n", localFileState.getFileName());
							File localFile = new File(localFileState.getFileName());
							localFile.delete();
						}
					}
				}
				break;

			case RESET_DATES:
				fastCompareNotSupported(fimOptions.getCompareMode());

				lastState = manager.loadLastState();
				manager.resetDates(lastState);
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

	private static boolean confirmCommand(String action)
	{
		Scanner scanner = new Scanner(System.in);
		System.out.printf("Do you really want to %s (y/n)? ", action);
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
