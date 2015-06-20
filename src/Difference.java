/**
 * Created by evrignaud on 20/06/15.
 */
public class Difference implements Comparable<Difference>
{
	public FileState originalState;
	public FileState fileState;

	public Difference(FileState originalState, FileState fileState)
	{
		this.originalState = originalState;
		this.fileState = fileState;
	}

	@Override
	public int compareTo(Difference other)
	{
		return fileState.compareTo(other.fileState);
	}
}
