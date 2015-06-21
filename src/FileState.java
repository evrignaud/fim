/**
 * Created by evrignaud on 05/05/15.
 */
public class FileState implements Comparable<FileState>
{
	private String fileName;
	private long lastModified;
	private String hash;

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

		this.setFileName(fileName);
		this.setLastModified(lastModified);
		this.setHash(hash);
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

		if (getLastModified() != fileState.getLastModified())
		{
			return false;
		}
		if (!getFileName().equals(fileState.getFileName()))
		{
			return false;
		}
		if (!getHash().equals(fileState.getHash()))
		{
			return false;
		}

		return true;
	}

	@Override
	public int hashCode()
	{
		int result = getFileName().hashCode();
		result = 31 * result + (int) (getLastModified() ^ (getLastModified() >>> 32));
		result = 31 * result + getHash().hashCode();
		return result;
	}

	@Override
	public String toString()
	{
		return new StringBuilder().append("FileState{").append("fileName='").append(getFileName()).append('\'').
				append(", lastModified=").append(getLastModified()).
				append(", hash='").append(getHash()).append('\'').
				append('}').toString();
	}

	@Override
	public int compareTo(FileState other)
	{
		return getFileName().compareTo(other.getFileName());
	}

	public String getFileName()
	{
		return fileName;
	}

	public void setFileName(String fileName)
	{
		this.fileName = fileName;
	}

	public long getLastModified()
	{
		return lastModified;
	}

	public void setLastModified(long lastModified)
	{
		this.lastModified = lastModified;
	}

	public String getHash()
	{
		return hash;
	}

	public void setHash(String hash)
	{
		this.hash = hash;
	}
}
