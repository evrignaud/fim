import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by evrignaud on 07/05/15.
 */
public class FormatUtil
{
	public static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

	public static String formatDate(FileState fileState)
	{
		return dateFormat.format(new Date(fileState.getLastModified()));
	}

	public static String formatDate(long timestamp)
	{
		return dateFormat.format(new Date(timestamp));
	}
}
