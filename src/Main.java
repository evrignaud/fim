import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

/**
 * File Integrity Checker (FIC).
 */
public class Main
{
	// TODO: Rename to (FIC) File Integrity Checker (FIC)
	// TODO : Before commit ask y/n
	// TODO : Before commit check if it's necessary. Do nothing if there is no change

	/**
	 * Construct Options.
	 */
	public static Options constructOptions()
	{
		Options options = new Options();
		options.addOption(createOption("q", "quiet", false, "Do not display details", false));
		options.addOption(createOption("m", "message", true, "Message to store with the state", false));
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
		if (args.length < 1)
		{
			youMustSpecifyACommandToRun();
		}

		Command command = Command.fromName(args[0]);
		if (command == null)
		{
			youMustSpecifyACommandToRun();
		}

		CommandLineParser cmdLineGnuParser = new GnuParser();

		Options options = constructOptions();
		CommandLine commandLine;

		boolean verbose = true;
		String message = "";

		try
		{
			String[] actionArgs = Arrays.copyOfRange(args, 1, args.length);
			commandLine = cmdLineGnuParser.parse(options, actionArgs);
			if (commandLine.hasOption("h"))
			{
				printUsage();
				System.exit(0);
			}
			else
			{
				verbose = !commandLine.hasOption('q');
				message = commandLine.getOptionValue('m', message);
			}
		}
		catch (Exception ex)
		{
			printUsage();
			System.exit(-1);
		}

		File baseDirectory = new File(".");
		File stateDir = new File(".fic/states");

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
				System.exit(0);
			}
		}

		State previousState;
		State currentState;

		StateGenerator generator = new StateGenerator();
		StateManager manager = new StateManager(stateDir);
		StateComparator comparator = new StateComparator(verbose);
		DuplicateFinder finder = new DuplicateFinder(verbose);

		switch (command)
		{
			case INIT:
				stateDir.mkdirs();
				currentState = generator.generateState("Initial state", baseDirectory);
				comparator.compare(null, currentState);
				manager.createNewState(currentState);
				break;

			case COMMIT:
				previousState = manager.loadLastState();
				currentState = generator.generateState(message, baseDirectory);
				comparator.compare(previousState, currentState);
				manager.createNewState(currentState);
				break;

			case DIFF:
				previousState = manager.loadLastState();
				currentState = generator.generateState(message, baseDirectory);
				comparator.compare(previousState, currentState);
				break;

			case FIND_DUPLICATES:
				System.out.println("Searching for duplicated files");
				System.out.println("");
				currentState = generator.generateState(message, baseDirectory);
				finder.findDuplicates(currentState);
				break;

			case RESET_DATES:
				previousState = manager.loadLastState();
				manager.resetDates(previousState);
				break;

			case LOG:
				manager.displayLog();
				break;
		}
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
			usage += "- " + command.cmdName + ": " + command.description + "\n";
		}

		helpFormatter.printHelp(writer, 110, "fic <command>", "\nFile Integrity Checker\n", options, 5, 3, usage, true);
		writer.flush();
		System.out.println("");
	}
}
