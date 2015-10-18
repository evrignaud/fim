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

import static org.fim.model.HashMode.hashAll;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.rits.cloning.Cloner;

public class Context
{
	public static final String DOT_FIM_DIR = ".fim";

	private static final Cloner CLONER = new Cloner();

	private boolean invokedFromSubDirectory;
	private Path currentDirectory;
	private Path repositoryRootDir;
	private boolean verbose;
	private HashMode hashMode;
	private String comment;
	private boolean useLastState;
	private int threadCount;
	private String masterFimRepositoryDir;
	private boolean alwaysYes;

	public Context()
	{
		setInvokedFromSubDirectory(false);
		setCurrentDirectory(Paths.get("."));
		setRepositoryRootDir(getCurrentDirectory());
		setVerbose(true);
		setHashMode(hashAll);
		setComment("");
		setUseLastState(false);
		setThreadCount(Runtime.getRuntime().availableProcessors() / 2);
		setMasterFimRepositoryDir(null);
		setAlwaysYes(false);
	}

	public boolean isInvokedFromSubDirectory()
	{
		return invokedFromSubDirectory;
	}

	public void setInvokedFromSubDirectory(boolean invokedFromSubDirectory)
	{
		this.invokedFromSubDirectory = invokedFromSubDirectory;
	}

	public Path getCurrentDirectory()
	{
		return currentDirectory;
	}

	public void setCurrentDirectory(Path currentDirectory)
	{
		this.currentDirectory = currentDirectory;
	}

	public Path getAbsoluteCurrentDirectory()
	{
		return currentDirectory.toAbsolutePath().normalize();
	}

	public Path getRepositoryRootDir()
	{
		return repositoryRootDir;
	}

	public void setRepositoryRootDir(Path repositoryRootDir)
	{
		this.repositoryRootDir = repositoryRootDir.toAbsolutePath().normalize();
	}

	public Path getRepositoryDotFimDir()
	{
		return repositoryRootDir.resolve(DOT_FIM_DIR);
	}

	public Path getRepositoryStatesDir()
	{
		return getRepositoryDotFimDir().resolve("states");
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
		if (threadCount < 1)
		{
			this.threadCount = 1;
		}
		else
		{
			this.threadCount = threadCount;
		}
	}

	public Context clone()
	{
		return CLONER.deepClone(this);
	}
}
