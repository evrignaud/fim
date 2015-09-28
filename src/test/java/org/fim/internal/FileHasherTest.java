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

import static java.lang.Math.min;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.FileState.SIZE_100_MB;
import static org.fim.model.FileState.SIZE_1_KB;
import static org.fim.model.FileState.SIZE_1_MB;
import static org.fim.model.FileState.SIZE_4_KB;
import static org.fim.model.HashMode.dontHash;
import static org.fim.model.HashMode.hashAll;
import static org.fim.model.HashMode.hashMediumBlock;
import static org.fim.model.HashMode.hashSmallBlock;
import static org.fim.tooling.BlockNumber1mb._1MB_Block_1;
import static org.fim.tooling.BlockNumber1mb._1MB_Block_2;
import static org.fim.tooling.BlockNumber4kb._4KB_Block_1;
import static org.fim.tooling.BlockNumber4kb._4KB_Block_2;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.apache.commons.io.FileUtils;
import org.fim.model.FileHash;
import org.fim.model.HashMode;
import org.fim.tooling.BlockNumber1mb;
import org.fim.tooling.BlockNumber4kb;
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

	private static byte contentBytes[];
	private static Path rootDir = Paths.get("target/" + FileHasherTest.class.getSimpleName());

	static
	{
		StringBuilder builder = new StringBuilder();
		for (char c = 33; c < 126; c++)
		{
			builder.append(c);
		}
		contentBytes = builder.toString().getBytes();
	}

	private HashProgress hashProgress;
	private HashMode hashMode;
	private BuildableContext context;
	private FileHasher cut;

	public FileHasherTest(final HashMode hashMode)
	{
		this.hashMode = hashMode;
	}

	@Parameterized.Parameters(name = "Hash mode: {0}")
	public static Collection<Object[]> parameters()
	{
		return Arrays.asList(new Object[][]{
				{dontHash},
				{hashSmallBlock},
				{hashMediumBlock},
				{hashAll}
		});
	}

	@BeforeClass
	public static void setupOnce() throws NoSuchAlgorithmException, IOException
	{
		FileUtils.deleteDirectory(rootDir.toFile());
		Files.createDirectories(rootDir);
	}

	@Before
	public void setup() throws NoSuchAlgorithmException, IOException
	{
		hashProgress = mock(HashProgress.class);
		context = defaultContext();
		context.setHashMode(hashMode);
		context.setRepositoryRootDir(rootDir);

		when(hashProgress.getContext()).thenReturn(context);

		cut = new FileHasher(hashProgress, null, rootDir.toString());
	}

	@Test
	public void hashAn_Empty_File() throws IOException
	{
		checkFileHash(0, _4KB_Block_1, _1MB_Block_1);
	}

	@Test
	public void hashA_2KB_File() throws IOException
	{
		checkFileHash((2 * 1024) + 157, _4KB_Block_1, _1MB_Block_1);
	}

	@Test
	public void hashA_4KB_File() throws IOException
	{
		checkFileHash((4 * 1024) + 201, _4KB_Block_1, _1MB_Block_1);
	}

	@Test
	public void hashA_6KB_File() throws IOException
	{
		checkFileHash((6 * 1024) + 323, _4KB_Block_1, _1MB_Block_1);
	}

	@Test
	public void hashA_8KB_File() throws IOException
	{
		checkFileHash((8 * 1024) + 723, _4KB_Block_2, _1MB_Block_1);
	}

	@Test
	public void hashA_10KB_File() throws IOException
	{
		checkFileHash((10 * 1024) + 671, _4KB_Block_2, _1MB_Block_1);
	}

	@Test
	public void hashA_30KB_File() throws IOException
	{
		checkFileHash((30 * 1024) + 257, _4KB_Block_2, _1MB_Block_1);
	}

	@Test
	public void hashA_1MB_File() throws IOException
	{
		checkFileHash((1 * 1024 * 1024) + 91, _4KB_Block_2, _1MB_Block_1);
	}

	@Test
	public void hashA_2MB_File() throws IOException
	{
		checkFileHash((2 * 1024 * 1024) + 51, _4KB_Block_2, _1MB_Block_2);
	}

	@Test
	public void hashA_3MB_File() throws IOException
	{
		checkFileHash((3 * 1024 * 1024) + 101, _4KB_Block_2, _1MB_Block_2);
	}

	@Test
	public void hashA_60MB_File() throws IOException
	{
		checkFileHash((60 * 1024 * 1024) + 291, _4KB_Block_2, _1MB_Block_2);
	}

	private void checkFileHash(int fileSize, BlockNumber4kb blockNumber4kb, BlockNumber1mb blockNumber1mb) throws IOException
	{
		Path fileToHash = createFileWithSize(fileSize);

		// Compute the expectedHash using a very simple algorithm and Guava Sha512 impl
		FileHash expectedHash = computeExpectedHash(fileToHash, blockNumber4kb, blockNumber1mb);

		FileHash fileHash = cut.hashFile(fileToHash, Files.size(fileToHash));

		// displayFileHash(fileSize, fileHash);

		assertFileHash(fileSize, expectedHash, fileHash);
	}

	private void displayFileHash(int fileSize, FileHash fileHash)
	{
		System.out.println("File " + FileUtils.byteCountToDisplaySize(fileSize));
		System.out.println("\tsmallBlockHash=" + fileHash.getSmallBlockHash());
		System.out.println("\tmediumBlockHash=" + fileHash.getMediumBlockHash());
		System.out.println("\tfullHash=" + fileHash.getFullHash());
		System.out.println("");
	}

	private void assertFileHash(int fileSize, FileHash expectedFileHash, FileHash fileHash)
	{
		switch (hashMode)
		{
			case dontHash:
				assertThat(fileHash.getSmallBlockHash()).isEqualTo(NO_HASH);
				assertThat(fileHash.getMediumBlockHash()).isEqualTo(NO_HASH);
				assertThat(fileHash.getFullHash()).isEqualTo(NO_HASH);

				assertSmallBlockBytesHashedEqualsTo(0);
				assertMediumBlockBytesHashedEqualsTo(0);
				assertFullBytesHashedEqualsTo(0);
				break;

			case hashSmallBlock:
				assertThat(fileHash.getSmallBlockHash()).isEqualTo(expectedFileHash.getSmallBlockHash());
				assertThat(fileHash.getMediumBlockHash()).isEqualTo(NO_HASH);
				assertThat(fileHash.getFullHash()).isEqualTo(NO_HASH);

				assertSmallBlockBytesHashedEqualsTo(min(fileSize, SIZE_4_KB));
				assertMediumBlockBytesHashedEqualsTo(0);
				assertFullBytesHashedEqualsTo(0);
				break;

			case hashMediumBlock:
				assertThat(fileHash.getSmallBlockHash()).isEqualTo(expectedFileHash.getSmallBlockHash());
				assertThat(fileHash.getMediumBlockHash()).isEqualTo(expectedFileHash.getMediumBlockHash());
				assertThat(fileHash.getFullHash()).isEqualTo(NO_HASH);

				assertSmallBlockBytesHashedEqualsTo(min(fileSize, SIZE_4_KB));
				assertMediumBlockBytesHashedEqualsTo(min(fileSize, SIZE_1_MB));
				assertFullBytesHashedEqualsTo(0);
				break;

			case hashAll:
				assertThat(fileHash.getSmallBlockHash()).isEqualTo(expectedFileHash.getSmallBlockHash());
				assertThat(fileHash.getMediumBlockHash()).isEqualTo(expectedFileHash.getMediumBlockHash());
				assertThat(fileHash.getFullHash()).isEqualTo(expectedFileHash.getFullHash());

				assertSmallBlockBytesHashedEqualsTo(min(fileSize, SIZE_4_KB));
				assertMediumBlockBytesHashedEqualsTo(min(fileSize, SIZE_1_MB));
				assertFullBytesHashedEqualsTo(fileSize);
				break;
		}
	}

	private void assertSmallBlockBytesHashedEqualsTo(int size)
	{
		assertThat(cut.getHashers().getSmallBlockHasher().getBytesHashed()).isEqualTo(size);
	}

	private void assertMediumBlockBytesHashedEqualsTo(int size)
	{
		assertThat(cut.getHashers().getMediumBlockHasher().getBytesHashed()).isEqualTo(size);
	}

	private void assertFullBytesHashedEqualsTo(int size)
	{
		assertThat(cut.getHashers().getFullHasher().getBytesHashed()).isEqualTo(size);
	}

	private Path createFileWithSize(int fileSize) throws IOException
	{
		Path newFile = context.getRepositoryRootDir().resolve("file_" + fileSize);
		if (Files.exists(newFile))
		{
			Files.delete(newFile);
		}

		if (fileSize == 0)
		{
			Files.createFile(newFile);
			return newFile;
		}

		try (ByteArrayOutputStream out = new ByteArrayOutputStream(fileSize))
		{
			int contentSize = SIZE_1_KB / 4;
			int remaining = fileSize;
			for (int sequenceCount = 0; remaining > 0; sequenceCount++)
			{
				int size = min(contentSize, remaining);
				byte[] content = generateContent(sequenceCount, size);
				remaining -= size;
				out.write(content);
			}

			byte[] fileContent = out.toByteArray();
			assertThat(fileContent.length).isEqualTo(fileSize);
			Files.write(newFile, fileContent, CREATE, APPEND);
		}

		return newFile;
	}

	private byte[] generateContent(int sequenceCount, int contentSize)
	{
		byte[] content = new byte[contentSize];
		for (int index = 0; index < contentSize; index += 2)
		{
			content[index] = getContentByte(sequenceCount, false);
			if (index + 1 < contentSize)
			{
				content[index + 1] = getContentByte(sequenceCount, true);
			}
		}
		return content;
	}

	private byte getContentByte(int sequenceCount, boolean fromTheEnd)
	{
		int index = sequenceCount % contentBytes.length;
		if (fromTheEnd)
		{
			index = contentBytes.length - 1 - index;
		}
		return contentBytes[index];
	}

	private FileHash computeExpectedHash(Path fileToHash, BlockNumber4kb blockNumber4kb, BlockNumber1mb blockNumber1mb) throws IOException
	{
		byte[] fullContent = Files.readAllBytes(fileToHash);
		String smallBlockHash = generateSmallBlockHash(fullContent, blockNumber4kb);
		String mediumBlockHash = generateMediumBlockHash(fullContent, blockNumber1mb);
		String fullHash = generateFullHash(fullContent);

		return new FileHash(smallBlockHash, mediumBlockHash, fullHash);
	}

	private String generateSmallBlockHash(byte[] fullContent, BlockNumber4kb blockNumber4kb) throws IOException
	{
		switch (blockNumber4kb)
		{
			case _4KB_Block_1:
				return hashContent(extractBlock(fullContent, 0, SIZE_4_KB));

			case _4KB_Block_2:
				return hashContent(extractBlock(fullContent, SIZE_4_KB, SIZE_4_KB));

			default:
				return NO_HASH;
		}
	}

	private String generateMediumBlockHash(byte[] fullContent, BlockNumber1mb blockNumber1mb) throws IOException
	{
		switch (blockNumber1mb)
		{
			case _1MB_Block_1:
				return hashContent(extractBlock(fullContent, 0, SIZE_1_MB));

			case _1MB_Block_2:
				return hashContent(extractBlock(fullContent, SIZE_1_MB, SIZE_1_MB));

			default:
				return NO_HASH;
		}
	}

	private String generateFullHash(byte[] fullContent)
	{
		return hashContent(fullContent);
	}

	private byte[] extractBlock(byte[] fullContent, int startPosition, int size)
	{
		return Arrays.copyOfRange(fullContent, startPosition, min(fullContent.length, startPosition + size));
	}

	private String hashContent(byte[] content)
	{
		HashFunction hashFunction = Hashing.sha512();
		com.google.common.hash.Hasher hasher = hashFunction.newHasher(SIZE_100_MB);
		hasher.putBytes(content);
		HashCode hash = hasher.hash();
		return hash.toString();
	}
}
