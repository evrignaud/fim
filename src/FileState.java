/**
 * Created by evrignaud on 05/05/15.
 */
public class FileState implements Comparable<FileState>
{
	public String fileName;
	public long lastModified;
	public String hash;

	public FileState(String fileName, long lastModified, String hash)
	{
		if (fileName == null)
		{
			throw new IllegalArgumentException("Invalid null fileName");
		}
		if (hash == null)
		{
			throw new IllegalArgumentException("Invalid null hash");
		}

		this.fileName = fileName;
		this.lastModified = lastModified;
		this.hash = hash;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (o == null || getClass() != o.getClass())
		{
			return false;
		}

		FileState fileState = (FileState) o;

		if (lastModified != fileState.lastModified)
		{
			return false;
		}
		if (!fileName.equals(fileState.fileName))
		{
			return false;
		}
		if (!hash.equals(fileState.hash))
		{
			return false;
		}

		return true;
	}

	@Override
	public int hashCode()
	{
		int result = fileName.hashCode();
		result = 31 * result + (int) (lastModified ^ (lastModified >>> 32));
		result = 31 * result + hash.hashCode();
		return result;
	}

	@Override
	public String toString()
	{
		return new StringBuilder().append("FileState{").append("fileName='").append(fileName).append('\'').
				append(", lastModified=").append(lastModified).
				append(", hash='").append(hash).append('\'').
				append('}').toString();
	}

	@Override
	public int compareTo(FileState other)
	{
		return fileName.compareTo(other.fileName);
	}
}
