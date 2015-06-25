/**
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
