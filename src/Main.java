import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

public class Main
{
	/**
	 * Construct Options.
	 */
	public static Options constructOptions()
	{
		Options options = new Options();
		options.addOption(createOption("b", "base-dir", true, "The base directory to scan", true));
		options.addOption(createOption("s", "state-dir", true, "The state directory that contains every states", false));
		options.addOption(createOption("m", "message", true, "Message to store with the state", false));
		options.addOption(createOption("c", "commit", false, "Command: Create a new state file", false));
		options.addOption(createOption("d", "diff", false, "Command: Compare with last state", false));
		options.addOption(createOption("f", "find-duplicates", false, "Command: Find duplicates", false));
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
		CommandLineParser cmdLineGnuParser = new GnuParser();

		Options options = constructOptions();
		CommandLine commandLine;

		File baseDirectory = null;
		File stateDir = null;
		String message = "";
		boolean commitAction = false;
		boolean diffAction = false;
		boolean findDuplicatesAction = false;

		try
		{
			commandLine = cmdLineGnuParser.parse(options, args);
			if (commandLine.hasOption("h") || commandLine.getOptions().length == 0)
			{
				printUsage();
				System.exit(0);
			}
			else
			{
				baseDirectory = new File(commandLine.getOptionValue('b'));
				stateDir = new File(commandLine.getOptionValue('s', "states"));
				message = commandLine.getOptionValue('m', message);

				commitAction = commandLine.hasOption("c");
				diffAction = commandLine.hasOption("d");
				findDuplicatesAction = commandLine.hasOption("f");
			}
		}
		catch (Exception ex)
		{
			printUsage();
			System.exit(-1);
		}

		if (!commitAction && !diffAction && !findDuplicatesAction)
		{
			System.out.println("You must specify the command to run");
			printUsage();
			System.exit(-1);
		}
		
		StateGenerator generator = new StateGenerator();
		State newState = generator.generateState(message, baseDirectory);

		if (commitAction)
		{
			StateManager manager = new StateManager(stateDir);
			manager.createNewState(newState);
		}
		else if (diffAction)
		{
			StateManager manager = new StateManager(stateDir);
			State oldState = manager.loadLastState();

			StateComparator comparator = new StateComparator();
			comparator.compare(oldState, newState);
		}
		else if (findDuplicatesAction)
		{
			System.out.println("Searching for duplicated files");

			DuplicateFinder finder = new DuplicateFinder();
			finder.findDuplicates(newState);
		}
	}

	public static void printUsage()
	{
		System.out.println("");
		Options options = constructOptions();
		PrintWriter writer = new PrintWriter(System.out);
		HelpFormatter helpFormatter = new HelpFormatter();

		String usage = "";

		helpFormatter.printHelp(writer, 110, "binary_manager", "\nManages binary files\n", options, 5, 3, usage, true);
		writer.flush();
		System.out.println("");
	}
}
