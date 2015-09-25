/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2015  Etienne Vrignaud
 *
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
 * along with Fim.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.fim.model;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FileToIgnore
{
	private String regexpFileName;
	private Pattern compiledFilename;

	public FileToIgnore(String regexpFileName)
	{
		this.regexpFileName = regexpFileName.trim();
		try
		{
			String regex = this.regexpFileName;

			// Only * is allowed in the fileName. So escape the other regexp special chars
			regex = escapeChars(regex, '\\', '^', '$', '{', '}', '[', ']', '(', ')', '.', '|', '?');

			// Many * char are converted to only one
			while (regex.contains("**"))
			{
				regex = regex.replace("**", "*");
			}

			// * is converted to .* in order to match zero or many chars
			regex = regex.replace("*", ".*");

			// Add start and end line anchors
			regex = "^" + regex + "$";

			this.compiledFilename = Pattern.compile(regex);
		}
		catch (PatternSyntaxException ex)
		{
			this.compiledFilename = null;
		}
	}

	private String escapeChars(String str, char... charToEscape)
	{
		String result = str;
		for (char c : charToEscape)
		{
			result = result.replace("" + c, "\\" + c);
		}

		return result;
	}

	public String getRegexpFileName()
	{
		return regexpFileName;
	}

	public Pattern getCompiledFilename()
	{
		return compiledFilename;
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (other == null || getClass() != other.getClass())
		{
			return false;
		}
		FileToIgnore that = (FileToIgnore) other;
		return Objects.equals(compiledFilename.toString(), that.compiledFilename.toString());
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(compiledFilename.toString());
	}
}
