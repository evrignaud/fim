/**
 * Created by evrignaud on 20/06/15.
 */
public class Difference implements Comparable<Difference>
{
	private FileState originalState;
	private FileState fileState;

	public Difference(FileState originalState, FileState fileState)
	{
		this.setOriginalState(originalState);
		this.setFileState(fileState);
	}

	@Override
	public int compareTo(Difference other)
	{
		return getFileState().compareTo(other.getFileState());
	}

	public FileState getOriginalState()
	{
		return originalState;
	}

	public void setOriginalState(FileState originalState)
	{
		this.originalState = originalState;
	}

	public FileState getFileState()
	{
		return fileState;
	}

	public void setFileState(FileState fileState)
	{
		this.fileState = fileState;
	}
}
