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
import org.fim.model.State;

/**
 * File Integrity Manager (FIM).
 * ______ _ _         _____      _                  _ _           ___  ___
 * |  ___(_) |       |_   _|    | |                (_) |          |  \/  |
 * | |_   _| | ___     | | _ __ | |_ ___  __ _ _ __ _| |_ _   _   | .  . | __ _ _ __   __ _  __ _  ___ _ __
 * |  _| | | |/ _ \    | || '_ \| __/ _ \/ _` | '__| | __| | | |  | |\/| |/ _` | '_ \ / _` |/ _` |/ _ \ '__|
 * | |   | | |  __/   _| || | | | ||  __/ (_| | |  | | |_| |_| |  | |  | | (_| | | | | (_| | (_| |  __/ |
 * \_|   |_|_|\___|   \___/_| |_|\__\___|\__, |_|  |_|\__|\__, |  \_|  |_/\__,_|_| |_|\__,_|\__, |\___|_|
 *                                        __/ |            __/ |                             __/ |
 *                                       |___/            |___/                             |___/
 *
 * FIM manage the integrity of a complete file tree.
 * With FIM you can manage the integrity of a big amount of data files that you could not put into an archive.
 * For example videos and photos can be managed by this tool.
 * Using it you can ensure the integrity of a whole videos / photos directory tree.
 * To do so FIM manages States that acts like the Central Directory does for a Zip file.
 * One State is an index that holds one entry per file that contains the file name,
 * a hash of the file content and the file modification date.
 * Only the States are kept into an history. There is no history for the file content.
 *
 * Created by evrignaud on 05/05/15.
 */
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
		options.addOption(createOption("m", "message", true, "Message to store with the state", false));
		options.addOption(createOption("t", "threadCount", true, "Number of thread to use for state generation", false));
		options.addOption(createOption("l", "useLastState", false, "Use last state", false));
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

		int threadCount = 1;
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
				currentState = generator.generateState("Initial state", baseDirectory);
				comparator.compare(null, currentState).displayChanges(verbose);
				manager.createNewState(currentState);
				break;

			case COMMIT:
				fastCompareNotSupported(compareMode);

				previousState = manager.loadPreviousState();
				currentState = generator.generateState(message, baseDirectory);
				comparator.compare(previousState, currentState).displayChanges(verbose);
				if (comparator.somethingModified())
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

				System.out.println("Searching for duplicated files" + (useLastState ? " from the previous state" : ""));
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
				finder.findDuplicates(state, verbose);
				break;

			case RESET_DATES:
				fastCompareNotSupported(compareMode);

				previousState = manager.loadPreviousState();
				manager.resetDates(previousState);
				break;

			case LOG:
				manager.displayLog();
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
		System.out.println("" +
				"______ _ _         _____      _                  _ _           ___  ___                                  \n" +
				"|  ___(_) |       |_   _|    | |                (_) |          |  \\/  |                                  \n" +
				"| |_   _| | ___     | | _ __ | |_ ___  __ _ _ __ _| |_ _   _   | .  . | __ _ _ __   __ _  __ _  ___ _ __ \n" +
				"|  _| | | |/ _ \\    | || '_ \\| __/ _ \\/ _` | '__| | __| | | |  | |\\/| |/ _` | '_ \\ / _` |/ _` |/ _ \\ '__|\n" +
				"| |   | | |  __/   _| || | | | ||  __/ (_| | |  | | |_| |_| |  | |  | | (_| | | | | (_| | (_| |  __/ |   \n" +
				"\\_|   |_|_|\\___|   \\___/_| |_|\\__\\___|\\__, |_|  |_|\\__|\\__, |  \\_|  |_/\\__,_|_| |_|\\__,_|\\__, |\\___|_|   \n" +
				"                                       __/ |            __/ |                             __/ |          \n" +
				"                                      |___/            |___/                             |___/           \n" +
				"\n" +
				"FIM manage the integrity of a complete file tree.\n" +
				"With FIM you can manage the integrity of a big amount of data files that you could not put into an archive.\n" +
				"For example videos and photos can be managed by this tool.\n" +
				"Using it you can ensure the integrity of a whole videos / photos directory tree.\n" +
				"To do so FIM manages States that acts like the Central Directory does for a Zip file.\n" +
				"One State is an index that holds one entry per file that contains the file name,\n" +
				"a hash of the file content and the file modification date.\n" +
				"Only the States are kept into an history. There is no history for the file content.\n");
		Options options = constructOptions();
		PrintWriter writer = new PrintWriter(System.out);
		HelpFormatter helpFormatter = new HelpFormatter();

		String usage = "\n  Available commands:\n";
		for (final Command command : Command.values())
		{
			if (command.getShortCmdName() != null && command.getShortCmdName().length() > 0)
			{
				usage += String.format("- %s / %s: %s\n", command.getCmdName(), command.getShortCmdName(), command.getDescription());
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
