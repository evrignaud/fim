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
package org.fim.util;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.fim.model.FileState;

/**
 * @author evrignaud
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
