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

/**
 * File Integrity Checker (FIC).
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
		boolean fastCompare = false;
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
				fastCompare = commandLine.hasOption('f');
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

		if (fastCompare)
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
				System.out.println("fic repository already exist");
				System.exit(0);
			}
		}
		else
		{
			if (!stateDir.exists())
			{
				System.out.println("fic repository does not exist. Please run 'fic init' before.");
				System.exit(-1);
			}
		}

		State previousState;
		State currentState;

		StateGenerator generator = new StateGenerator(threadCount, fastCompare);
		StateManager manager = new StateManager(stateDir, fastCompare);
		StateComparator comparator = new StateComparator(verbose, fastCompare);
		DuplicateFinder finder = new DuplicateFinder(verbose);

		switch (command)
		{
			case INIT:
				fastCompareNotSupported(fastCompare);

				stateDir.mkdirs();
				currentState = generator.generateState("Initial state", baseDirectory);
				comparator.compare(null, currentState).displayChanges();
				manager.createNewState(currentState);
				break;

			case COMMIT:
				fastCompareNotSupported(fastCompare);

				previousState = manager.loadPreviousState();
				currentState = generator.generateState(message, baseDirectory);
				comparator.compare(previousState, currentState).displayChanges();
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
				comparator.compare(previousState, currentState).displayChanges();
				break;

			case FIND_DUPLICATES:
				fastCompareNotSupported(fastCompare);

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
				finder.findDuplicates(state);
				break;

			case RESET_DATES:
				fastCompareNotSupported(fastCompare);

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

	private static void fastCompareNotSupported(boolean fastCompare)
	{
		if (fastCompare)
		{
			System.out.println("fastCompare option not supported by this command.");
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

		String usage = "\n  Available commands:\n";
		for (final Command command : Command.values())
		{
			if (command.shortCmdName != null && command.shortCmdName.length() > 0)
			{
				usage += String.format("- %s / %s: %s\n", command.cmdName, command.shortCmdName, command.description);
			}
			else
			{
				usage += String.format("- %s: %s\n", command.cmdName, command.description);
			}
		}

		helpFormatter.printHelp(writer, 110, "fic <command>", "\nFile Integrity Checker\n", options, 5, 3, usage, true);
		writer.flush();
		System.out.println("");
	}
}
