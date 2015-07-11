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
package org.fim.internal;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.fim.model.FileState;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FileHasherPerf
{
	private final Charset utf8 = Charset.forName("UTF-8");

	@Mock
	private StateGenerator stateGenerator;

	private List<FileState> fileStates;
	private String rootDir;
	private File fileToHash;
	private FileHasher cut;

	@Before
	public void setup() throws NoSuchAlgorithmException
	{
		fileStates = new ArrayList<>();
		rootDir = "target/" + this.getClass().getSimpleName();
		fileToHash = new File("file_01");
		cut = new FileHasher(stateGenerator, fileStates, rootDir, fileToHash);
	}

	@Test
	public void findMostEfficientHash() throws IOException
	{
		for (int index = 0; index < 10; index++)
		{
			File file = new File(rootDir, "file_" + index);
			createBigFile(file, 60 * 1024 * 1024);
		}

		long start = System.currentTimeMillis();
		for (int index = 0; index < 200000; index++)
		{
			File file = new File(rootDir, "file_" + (index % 10));
			cut.hashFileUsingNIO(file);
		}
		System.out.println("hashFileUsingNIO took: " + (System.currentTimeMillis() - start) + " ms");

		start = System.currentTimeMillis();
		for (int index = 0; index < 200000; index++)
		{
			File file = new File(rootDir, "file_" + (index % 10));
			cut.hashFileChunkByChunk(file);
		}
		System.out.println("hashFileChunkByChunk took: " + (System.currentTimeMillis() - start) + " ms");
	}

	private void createBigFile(File file, long fileSize) throws IOException
	{
		if (file.exists())
		{
			file.delete();
		}

		SecureRandom random = new SecureRandom();
		do
		{
			String randomString = new BigInteger(130, random).toString(32);
			FileUtils.writeStringToFile(file, randomString, true);
		}
		while (file.length() < fileSize);
	}
}
