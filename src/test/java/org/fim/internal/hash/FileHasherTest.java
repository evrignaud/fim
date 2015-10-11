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
package org.fim.internal.hash;

import static java.lang.Math.min;
import static java.nio.file.StandardOpenOption.CREATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.HashMode.dontHash;
import static org.fim.model.HashMode.hashAll;
import static org.fim.model.HashMode.hashMediumBlock;
import static org.fim.model.HashMode.hashSmallBlock;
import static org.fim.model.TestContants.NO_HASH;
import static org.fim.model.TestContants._100_MB;
import static org.fim.model.TestContants._10_KB;
import static org.fim.model.TestContants._12_KB;
import static org.fim.model.TestContants._1_KB;
import static org.fim.model.TestContants._1_MB;
import static org.fim.model.TestContants._24_KB;
import static org.fim.model.TestContants._2_KB;
import static org.fim.model.TestContants._2_MB;
import static org.fim.model.TestContants._30_KB;
import static org.fim.model.TestContants._30_MB;
import static org.fim.model.TestContants._3_MB;
import static org.fim.model.TestContants._4_KB;
import static org.fim.model.TestContants._512_KB;
import static org.fim.model.TestContants._60_MB;
import static org.fim.model.TestContants._6_KB;
import static org.fim.model.TestContants._8_KB;
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
import org.fim.model.Range;
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
		checkFileHash(0,
				new Range[]{new Range(0, 0)},
				new Range[]{new Range(0, 0)});
	}

	@Test
	public void hashA_2KB_File() throws IOException
	{
		checkFileHash(_2_KB + 157,
				new Range[]{new Range(0, _2_KB + 157)},
				new Range[]{new Range(0, _2_KB + 157)});
	}

	@Test
	public void hashA_4KB_File() throws IOException
	{
		checkFileHash(_4_KB + 201,
				new Range[]{new Range(0, _4_KB)},
				new Range[]{new Range(0, _4_KB + 201)});
	}

	@Test
	public void hashA_6KB_File() throws IOException
	{
		checkFileHash(_6_KB + 323,
				new Range[]{new Range(0, _4_KB)},
				new Range[]{new Range(0, _6_KB + 323)});
	}

	@Test
	public void hashA_8KB_File() throws IOException
	{
		checkFileHash(_8_KB + 723,
				new Range[]{new Range(_4_KB, _8_KB)},
				new Range[]{new Range(0, _8_KB + 723)});
	}

	@Test
	public void hashA_10KB_File() throws IOException
	{
		checkFileHash(_10_KB + 671,
				new Range[]{new Range(_4_KB, _8_KB)},
				new Range[]{new Range(0, _10_KB + 671)});
	}

	@Test
	public void hashA_30KB_File() throws IOException
	{
		checkFileHash(_30_KB + 257,
				new Range[]{new Range(_4_KB, _8_KB), new Range(_12_KB, _12_KB + _4_KB), new Range(_24_KB, _24_KB + _4_KB)},
				new Range[]{new Range(0, _30_KB + 257)});
	}

	@Test
	public void hashA_1MB_File() throws IOException
	{
		checkFileHash(_1_MB + 91,
				new Range[]{new Range(_4_KB, _8_KB), new Range(_512_KB, _512_KB + _4_KB), new Range(_1_MB - _4_KB, _1_MB)},
				new Range[]{new Range(0, _1_MB)});
	}

	@Test
	public void hashA_2MB_File() throws IOException
	{
		checkFileHash(_2_MB + 51,
				new Range[]{new Range(_4_KB, _8_KB), new Range(_1_MB, _1_MB + _4_KB), new Range(_2_MB - _4_KB, _2_MB)},
				new Range[]{new Range(_1_MB, _2_MB)});
	}

	@Test
	public void hashA_3MB_File() throws IOException
	{
		checkFileHash(_3_MB + 101,
				new Range[]{new Range(_4_KB, _8_KB), new Range(_1_MB + _512_KB, _1_MB + _512_KB + _4_KB), new Range(_3_MB - _4_KB, _3_MB)},
				new Range[]{new Range(_1_MB, _2_MB), new Range(_2_MB, _3_MB)});
	}

	@Test
	public void hashA_60MB_File() throws IOException
	{
		checkFileHash(_60_MB + 291,
				new Range[]{new Range(_4_KB, _8_KB), new Range(_30_MB, _30_MB + _4_KB), new Range(_60_MB - _4_KB, _60_MB)},
				new Range[]{new Range(_1_MB, _2_MB), new Range(_30_MB, _30_MB + _1_MB), new Range(_60_MB - _1_MB, _60_MB)});
	}

	private void checkFileHash(long fileSize, Range[] smallRanges, Range[] mediumRanges) throws IOException
	{
		Path fileToHash = createFileWithSize((int) fileSize);

		// Compute the expectedHash using a very simple algorithm and Guava Sha512 impl
		FileHash expectedHash = computeExpectedHash(fileToHash, smallRanges, mediumRanges);

		FileHash fileHash = cut.hashFile(fileToHash, Files.size(fileToHash));

		assertRangesEqualsTo(smallRanges, mediumRanges);

		// displayFileHash(fileSize, fileHash);

		assertFileHashEqualsTo(fileSize, expectedHash, fileHash);
	}

	private void displayFileHash(long fileSize, FileHash fileHash)
	{
		System.out.println("File " + FileUtils.byteCountToDisplaySize(fileSize));
		System.out.println("\tsmallBlockHash=" + fileHash.getSmallBlockHash());
		System.out.println("\tmediumBlockHash=" + fileHash.getMediumBlockHash());
		System.out.println("\tfullHash=" + fileHash.getFullHash());
		System.out.println("");
	}

	private void assertRangesEqualsTo(Range[] smallRanges, Range[] mediumRanges)
	{
		if (hashMode != dontHash)
		{
			BlockHasher smallBlockHasher = (BlockHasher) cut.getFrontHasher().getSmallBlockHasher();
			assertThat(smallBlockHasher.getRanges()).isEqualTo(smallRanges);

			if (hashMode != hashSmallBlock)
			{
				BlockHasher mediumBlockHasher = (BlockHasher) cut.getFrontHasher().getMediumBlockHasher();
				assertThat(mediumBlockHasher.getRanges()).isEqualTo(mediumRanges);
			}
		}
	}

	private void assertFileHashEqualsTo(long fileSize, FileHash expectedFileHash, FileHash fileHash)
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

				assertSmallBlockBytesHashedEqualsTo(getExpectedSizeToHash(fileSize, _4_KB));
				assertMediumBlockBytesHashedEqualsTo(0);
				assertFullBytesHashedEqualsTo(0);
				break;

			case hashMediumBlock:
				assertThat(fileHash.getSmallBlockHash()).isEqualTo(expectedFileHash.getSmallBlockHash());
				assertThat(fileHash.getMediumBlockHash()).isEqualTo(expectedFileHash.getMediumBlockHash());
				assertThat(fileHash.getFullHash()).isEqualTo(NO_HASH);

				assertSmallBlockBytesHashedEqualsTo(getExpectedSizeToHash(fileSize, _4_KB));
				assertMediumBlockBytesHashedEqualsTo(getExpectedSizeToHash(fileSize, _1_MB));
				assertFullBytesHashedEqualsTo(0);
				break;

			case hashAll:
				assertThat(fileHash.getSmallBlockHash()).isEqualTo(expectedFileHash.getSmallBlockHash());
				assertThat(fileHash.getMediumBlockHash()).isEqualTo(expectedFileHash.getMediumBlockHash());
				assertThat(fileHash.getFullHash()).isEqualTo(expectedFileHash.getFullHash());

				assertSmallBlockBytesHashedEqualsTo(getExpectedSizeToHash(fileSize, _4_KB));
				assertMediumBlockBytesHashedEqualsTo(getExpectedSizeToHash(fileSize, _1_MB));
				assertFullBytesHashedEqualsTo(fileSize);
				break;
		}
	}

	private long getExpectedSizeToHash(long fileSize, int blockSize)
	{
		long sizeToHash;
		if (fileSize > 4 * blockSize)
		{
			sizeToHash = 3 * blockSize;
		}
		else if (fileSize > 3 * blockSize)
		{
			sizeToHash = 2 * blockSize;
		}
		else
		{
			sizeToHash = blockSize;
		}
		sizeToHash = min(fileSize, sizeToHash);
		return sizeToHash;
	}

	private void assertSmallBlockBytesHashedEqualsTo(long expectedSizeToHash)
	{
		assertBlockBytesHashedEqualsTo(expectedSizeToHash, (BlockHasher) cut.getFrontHasher().getSmallBlockHasher());
	}

	private void assertMediumBlockBytesHashedEqualsTo(long expectedSizeToHash)
	{
		assertBlockBytesHashedEqualsTo(expectedSizeToHash, (BlockHasher) cut.getFrontHasher().getMediumBlockHasher());
	}

	private void assertBlockBytesHashedEqualsTo(long expectedSizeToHash, BlockHasher blockHasher)
	{
		long sizeToHash = blockHasher.getSizeToHash();
		long bytesHashed = blockHasher.getBytesHashed();

		assertThat(sizeToHash).isEqualTo(bytesHashed);
		assertThat(bytesHashed).isEqualTo(expectedSizeToHash);
	}

	private void assertFullBytesHashedEqualsTo(long expectedSizeToHash)
	{
		assertThat(cut.getFrontHasher().getFullHasher().getBytesHashed()).isEqualTo(expectedSizeToHash);
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
			int contentSize = _1_KB / 4;
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
			Files.write(newFile, fileContent, CREATE);
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

	//	smallRange, mediumRange
	private FileHash computeExpectedHash(Path fileToHash, Range[] smallRanges, Range[] mediumRanges) throws IOException
	{
		byte[] fullContent = Files.readAllBytes(fileToHash);
		String smallBlockHash = generateBlockHash(fullContent, smallRanges);
		String mediumBlockHash = generateBlockHash(fullContent, mediumRanges);
		String fullHash = generateFullHash(fullContent);

		return new FileHash(smallBlockHash, mediumBlockHash, fullHash);
	}

	private String generateBlockHash(byte[] fullContent, Range[] ranges) throws IOException
	{
		HashFunction hashFunction = Hashing.sha512();
		com.google.common.hash.Hasher hasher = hashFunction.newHasher(_100_MB);

		for (Range range : ranges)
		{
			byte[] content = extractBlock(fullContent, range);
			hasher.putBytes(content);
		}

		HashCode hash = hasher.hash();
		return hash.toString();
	}

	private String generateFullHash(byte[] fullContent)
	{
		return hashContent(fullContent);
	}

	private byte[] extractBlock(byte[] fullContent, Range range)
	{
		return Arrays.copyOfRange(fullContent, (int) range.getFrom(), (int) range.getTo());
	}

	private String hashContent(byte[] content)
	{
		HashFunction hashFunction = Hashing.sha512();
		com.google.common.hash.Hasher hasher = hashFunction.newHasher(_100_MB);
		hasher.putBytes(content);
		HashCode hash = hasher.hash();
		return hash.toString();
	}
}
