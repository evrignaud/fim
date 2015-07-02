/*
 * Fim is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Fim is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.fim.model;

/**
 * @author evrignaud
 */
public class FileState implements Comparable<FileState>
{
	private String fileName;
	private long lastModified;
	private String hash;
	private String newHash;

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

	public boolean contentChanged()
	{
		return !hash.equals(newHash);
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

	public void setNewHash(String newHash)
	{
		this.newHash = newHash;
	}

	public void resetNewHash()
	{
		newHash = hash;
	}
}
