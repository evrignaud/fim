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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.fim.model.FileHash;
import org.fim.model.HashMode;
import org.fim.tooling.BuildableContext;
import org.fim.tooling.StateAssert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class FileHasherTest extends StateAssert
{
	public static final String NO_HASH = "no_hash";

	private StateGenerator stateGenerator;

	private HashMode hashMode;
	private BuildableContext context;
	private FileHasher cut;

	public FileHasherTest(final HashMode hashMode)
	{
		this.hashMode = hashMode;
	}

	@BeforeClass
	public static void setupOnce()
	{
		Security.addProvider(new BouncyCastleProvider());
	}

	@Parameterized.Parameters(name = "Hash mode: {0}")
	public static Collection<Object[]> parameters()
	{
		return Arrays.asList(new Object[][]{
				{HashMode.dontHash},
				{HashMode.hashSmallBlock},
				{HashMode.hashMediumBlock},
				{HashMode.hashAll}
		});
	}

	@Before
	public void setup() throws GeneralSecurityException, IOException
	{
		Path rootDir = Paths.get("target/" + this.getClass().getSimpleName());

		stateGenerator = mock(StateGenerator.class);
		context = defaultContext();
		context.setHashMode(hashMode);
		context.setRepositoryRootDir(rootDir);

		when(stateGenerator.getContext()).thenReturn(context);

		FileUtils.deleteDirectory(rootDir.toFile());
		Files.createDirectories(rootDir);

		cut = new FileHasher(stateGenerator, null, rootDir.toString());
	}

	@Test
	public void weCanConvertToHexa()
	{
		byte[] bytes = new byte[]{(byte) 0xa4, (byte) 0xb0, (byte) 0xe5, (byte) 0xfd};
		String hexString = cut.toHexString(bytes);
		assertThat(hexString).isEqualTo("a4b0e5fd");
	}

	@Test
	public void weCanConvertToHexaWithZero()
	{
		byte[] bytes = new byte[]{(byte) 0xa0, 0x40, 0x0b, 0x00, (byte) 0xe0, 0x05, 0x0f, 0x0d};
		String hexString = cut.toHexString(bytes);
		assertThat(hexString).isEqualTo("a0400b00e0050f0d");
	}

	@Test
	public void weCanHashAn_Empty_File() throws IOException
	{
		String smallBlockHash = "0eab42de4c3ceb9235fc91acffe746b29c29a8c366b7c60e4e67c466f36a4304c00fa9caf9d87976ba469bcbe06713b435f091ef2769fb160cdab33d3670680e";
		String mediumBlockHash = smallBlockHash;
		String fullFileHash = smallBlockHash;

		Path fileToHash = createFileWithSize(0);

		FileHash fileHash = cut.hashFile(fileToHash, Files.size(fileToHash));

		assertFileHash(fileHash, smallBlockHash, mediumBlockHash, fullFileHash);
	}

	@Test
	public void weCanHashA_2KB_File() throws IOException
	{
		String smallBlockHash = "1af5d49d0da9efa558297b9895f560c483dfe9785bfa35d587b5657896d07aae939e5f67648774600dd4458f165612e26ef199e0812703710b524cd125dcfc9e";
		String mediumBlockHash = smallBlockHash;
		String fullFileHash = smallBlockHash;

		Path fileToHash = createFileWithSize(2 * 1024);

		FileHash fileHash = cut.hashFile(fileToHash, Files.size(fileToHash));

		assertFileHash(fileHash, smallBlockHash, mediumBlockHash, fullFileHash);
	}

	@Test
	public void weCanHashA_30KB_File() throws IOException
	{
		String smallBlockHash = "3589cbc818057856c6ac13b3baa1368ecb7005a75c807f13817ab6f03d16346107532848aeeb9979e172bd86312c044676209540c4e06d508c796dc90d073526";
		String mediumBlockHash = "9f26b15e16e850abe70375bdbc9b9fb66fe305b9cf23147174c4460253caddfbc356c8453eec57c4a9551efea6f5a11644382bfcdb7232d89f7adf275a550de3";
		String fullFileHash = mediumBlockHash;

		Path fileToHash = createFileWithSize(30 * 1024);

		FileHash fileHash = cut.hashFile(fileToHash, Files.size(fileToHash));

		assertFileHash(fileHash, smallBlockHash, mediumBlockHash, fullFileHash);
	}

	@Test
	public void weCanHashA_60MB_File() throws IOException
	{
		String smallBlockHash = "3589cbc818057856c6ac13b3baa1368ecb7005a75c807f13817ab6f03d16346107532848aeeb9979e172bd86312c044676209540c4e06d508c796dc90d073526";
		String mediumBlockHash = "a67a8e1723b682c3955a0c7cfca7bbbeacb2e31b2582942fac5fa8d9cd3406c8e6a43633287f3a724189252a00239c6a33557059a3d2738c7daaf98bca71dd52";
		String fullFileHash = "07ba81967b8baa14dd312d74751f977a3fd3971ef8f0429c83a9a9fa8b85814cf59dc8dfa2d1bfd72e5f79fecb2aa7c3dc91aaee2a167a387f012456017ffacc";

		Path fileToHash = createFileWithSize(60 * 1024 * 1024);

		FileHash fileHash = cut.hashFile(fileToHash, Files.size(fileToHash));

		assertFileHash(fileHash, smallBlockHash, mediumBlockHash, fullFileHash);
	}

	private void assertFileHash(FileHash fileHash, String smallBlockHash, String mediumBlockHash, String fullFileHash)
	{
		switch (hashMode)
		{
			case dontHash:
				assertThat(fileHash.getSmallBlockHash()).isEqualTo(NO_HASH);
				assertThat(fileHash.getMediumBlockHash()).isEqualTo(NO_HASH);
				assertThat(fileHash.getFullHash()).isEqualTo(NO_HASH);
				break;

			case hashSmallBlock:
				assertThat(fileHash.getSmallBlockHash()).isEqualTo(smallBlockHash);
				assertThat(fileHash.getMediumBlockHash()).isEqualTo(NO_HASH);
				assertThat(fileHash.getFullHash()).isEqualTo(NO_HASH);
				break;

			case hashMediumBlock:
				assertThat(fileHash.getSmallBlockHash()).isEqualTo(smallBlockHash);
				assertThat(fileHash.getMediumBlockHash()).isEqualTo(mediumBlockHash);
				assertThat(fileHash.getFullHash()).isEqualTo(NO_HASH);
				break;

			case hashAll:
				assertThat(fileHash.getSmallBlockHash()).isEqualTo(smallBlockHash);
				assertThat(fileHash.getMediumBlockHash()).isEqualTo(mediumBlockHash);
				assertThat(fileHash.getFullHash()).isEqualTo(fullFileHash);
				break;
		}
	}

	private Path createFileWithSize(long fileSize) throws IOException
	{
		Path license = Paths.get("LICENSE");
		byte[] content = Files.readAllBytes(license);

		Path newFile = context.getRepositoryRootDir().resolve("LICENSE_" + fileSize);
		if (Files.exists(newFile))
		{
			Files.delete(newFile);
		}

		if (fileSize == 0)
		{
			Files.createFile(newFile);
			return newFile;
		}

		if (content.length > fileSize)
		{
			content = Arrays.copyOf(content, (int) fileSize);
		}

		for (int size = 0; size < fileSize; size += content.length)
		{
			Files.write(newFile, content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}

		return newFile;
	}
}
