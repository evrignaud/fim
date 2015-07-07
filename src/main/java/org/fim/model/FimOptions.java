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

import java.io.File;

public class FimOptions
{
	private File baseDirectory;
	private File stateDir;
	private boolean verbose = true;
	private CompareMode compareMode = CompareMode.FULL;
	private String message = "";
	private boolean useLastState = false;
	private int threadCount = Runtime.getRuntime().availableProcessors();
	private String fimRepositoryDirectory = null;
	private boolean alwaysYes = false;

	public File getBaseDirectory()
	{
		return baseDirectory;
	}

	public void setBaseDirectory(File baseDirectory)
	{
		this.baseDirectory = baseDirectory;
	}

	public File getStateDir()
	{
		return stateDir;
	}

	public void setStateDir(File stateDir)
	{
		this.stateDir = stateDir;
	}

	public boolean isVerbose()
	{
		return verbose;
	}

	public void setVerbose(boolean verbose)
	{
		this.verbose = verbose;
	}

	public CompareMode getCompareMode()
	{
		return compareMode;
	}

	public void setCompareMode(CompareMode compareMode)
	{
		this.compareMode = compareMode;
	}

	public String getMessage()
	{
		return message;
	}

	public void setMessage(String message)
	{
		this.message = message;
	}

	public boolean isUseLastState()
	{
		return useLastState;
	}

	public void setUseLastState(boolean useLastState)
	{
		this.useLastState = useLastState;
	}

	public String getFimRepositoryDirectory()
	{
		return fimRepositoryDirectory;
	}

	public void setFimRepositoryDirectory(String fimRepositoryDirectory)
	{
		this.fimRepositoryDirectory = fimRepositoryDirectory;
	}

	public boolean isAlwaysYes()
	{
		return alwaysYes;
	}

	public void setAlwaysYes(boolean alwaysYes)
	{
		this.alwaysYes = alwaysYes;
	}

	public int getThreadCount()
	{
		return threadCount;
	}

	public void setThreadCount(int threadCount)
	{
		this.threadCount = threadCount;
	}
}
