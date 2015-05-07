/**
 * Created by evrignaud on 07/05/15.
 */
public enum Command
{
	INIT("init", "Initialize a binary_manager repository"),
	COMMIT("ci", "Commit the current directory state"),
	DIFF("diff", "Compare the current directory state with the previous one"),
	FIND_DUPLICATES("fdup", "Find duplicated files"),
	RESET_DATES("rdates", "Reset the file modification dates like in the current directory state"),
	LOG("log", "Display states log");

	public final String cmdName;
	public final String description;

	Command(String cmdName, String description)
	{
		this.cmdName = cmdName;
		this.description = description;
	}

	public static Command fromName(final String cmdName)
	{
		for (final Command command : values())
		{
			if (command.cmdName.equals(cmdName))
			{
				return command;
			}
		}
		return null;
	}
}
