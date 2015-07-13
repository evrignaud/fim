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

public class Parameters implements Cloneable
{
	public static final String DOT_FIM_DIR = ".fim";

	private File defaultStateDir;
	private boolean verbose;
	private HashMode hashMode;
	private String comment;
	private boolean useLastState;
	private int threadCount;
	private String masterFimRepositoryDir;
	private boolean alwaysYes;

	public Parameters()
	{
		this.defaultStateDir = new File(DOT_FIM_DIR, "states");
		this.verbose = true;
		this.hashMode = HashMode.COMPUTE_ALL_HASH;
		this.comment = "";
		this.useLastState = false;
		this.threadCount = Runtime.getRuntime().availableProcessors() / 2;
		this.masterFimRepositoryDir = null;
		this.alwaysYes = false;
	}

	public File getDefaultStateDir()
	{
		return defaultStateDir;
	}

	public void setDefaultStateDir(File defaultStateDir)
	{
		this.defaultStateDir = defaultStateDir;
	}

	public boolean isVerbose()
	{
		return verbose;
	}

	public void setVerbose(boolean verbose)
	{
		this.verbose = verbose;
	}

	public HashMode getHashMode()
	{
		return hashMode;
	}

	public void setHashMode(HashMode hashMode)
	{
		this.hashMode = hashMode;
	}

	public String getComment()
	{
		return comment;
	}

	public void setComment(String comment)
	{
		this.comment = comment;
	}

	public boolean isUseLastState()
	{
		return useLastState;
	}

	public void setUseLastState(boolean useLastState)
	{
		this.useLastState = useLastState;
	}

	public String getMasterFimRepositoryDir()
	{
		return masterFimRepositoryDir;
	}

	public void setMasterFimRepositoryDir(String masterFimRepositoryDir)
	{
		this.masterFimRepositoryDir = masterFimRepositoryDir;
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

	public Object clone() throws CloneNotSupportedException
	{
		return super.clone();
	}
}
