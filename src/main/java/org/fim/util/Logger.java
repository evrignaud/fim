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
package org.fim.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger
{
	public static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

	public static String getCurrentDate()
	{
		return dateFormat.format(new Date());
	}

	public static void info(String message)
	{
		writeLogMessage(new StringBuilder().append(getCurrentDate()).append(" - Info  - ").append(message).toString());
	}

	public static void alert(String message)
	{
		writeLogMessage(new StringBuilder().append(getCurrentDate()).append(" - Alert - ").append(message).toString());
	}

	public static void error(Exception ex)
	{
		error(exceptionStackTraceToString(ex));
	}

	public static void error(String message, Exception ex)
	{
		error(new StringBuilder().append(message).append(": ").append(exceptionStackTraceToString(ex)).toString());
	}

	public static void error(String message)
	{
		writeLogMessage(new StringBuilder().append(getCurrentDate()).append(" - Error - ").append(message).toString());
	}

	private static String exceptionStackTraceToString(Exception ex)
	{
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
		{
			PrintStream ps = new PrintStream(baos);
			ex.printStackTrace(ps);
			return baos.toString();
		}
		catch (IOException e)
		{
			return ex.getMessage();
		}
	}

	private static void writeLogMessage(String message)
	{
		System.out.println(message);
		System.out.flush();
	}
}
