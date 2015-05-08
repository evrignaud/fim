/**
 * Created by evrignaud on 07/05/15.
 */
public enum Command
{
	INIT("init", "", "Initialize a binary_manager repository"),
	COMMIT("commit", "ci", "Commit the current directory state"),
	DIFF("diff", "", "Compare the current directory state with the previous one"),
	FIND_DUPLICATES("find-duplicates", "fdup", "Find duplicated files"),
	LOG("log", "", "Display states log"),
	RESET_DATES("reset-dates", "rdates", "Reset the file modification dates like in the current directory state");

	public final String cmdName;
	public final String shortCmdName;
	public final String description;

	Command(String cmdName, String shortCmdName, String description)
	{
		this.cmdName = cmdName;
		this.shortCmdName = shortCmdName;
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

			if (command.shortCmdName.equals(cmdName))
			{
				return command;
			}
		}
		return null;
	}
}
