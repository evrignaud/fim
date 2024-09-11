/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2017  Etienne Vrignaud
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
 * along with Fim.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.fim.internal.hash;

import com.blackducksoftware.tools.commonframework.core.encoding.Ascii85Encoder;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.fim.model.Context;
import org.fim.model.FileHash;
import org.fim.model.HashMode;
import org.fim.model.Range;
import org.fim.tooling.RepositoryTool;
import org.fim.tooling.StateAssert;
import org.fim.util.TestAllHashModes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static java.lang.Math.min;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.HashMode.dontHash;
import static org.fim.model.HashMode.hashSmallBlock;
import static org.fim.tooling.TestConstants.NO_HASH;
import static org.fim.tooling.TestConstants.SIZE_100_MB;
import static org.fim.tooling.TestConstants.SIZE_10_KB;
import static org.fim.tooling.TestConstants.SIZE_12_KB;
import static org.fim.tooling.TestConstants.SIZE_1_KB;
import static org.fim.tooling.TestConstants.SIZE_1_MB;
import static org.fim.tooling.TestConstants.SIZE_24_KB;
import static org.fim.tooling.TestConstants.SIZE_2_KB;
import static org.fim.tooling.TestConstants.SIZE_2_MB;
import static org.fim.tooling.TestConstants.SIZE_30_KB;
import static org.fim.tooling.TestConstants.SIZE_30_MB;
import static org.fim.tooling.TestConstants.SIZE_3_MB;
import static org.fim.tooling.TestConstants.SIZE_4_KB;
import static org.fim.tooling.TestConstants.SIZE_512_KB;
import static org.fim.tooling.TestConstants.SIZE_60_MB;
import static org.fim.tooling.TestConstants.SIZE_6_KB;
import static org.fim.tooling.TestConstants.SIZE_8_KB;
import static org.mockito.Mockito.mock;

public class FileHasherTest extends StateAssert {
    public static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final byte[] CONTENT_BYTES;

    static {
        StringBuilder builder = new StringBuilder();
        for (char c = 33; c < 126; c++) {
            builder.append(c);
        }
        CONTENT_BYTES = builder.toString().getBytes();
    }

    private long globalSequenceCount = 0;
    private Context context;
    private FileHasher cut;

    private TestInfo testInfo;

    @BeforeEach
    void init(TestInfo testInfo) {
        this.testInfo = testInfo;
    }

    public void setUp(HashMode hashMode) {
        try {
            RepositoryTool tool = new RepositoryTool(testInfo, hashMode);
            Path rootDir = tool.getRootDir();
            context = tool.getContext();

            HashProgress hashProgress = mock(HashProgress.class);

            cut = new FileHasher(context, null, hashProgress, null, rootDir.toString());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @TestAllHashModes
    public void hashAn_Empty_File(HashMode hashMode) throws IOException {
        setUp(hashMode);

        checkFileHash(hashMode, 0,
                new Range[] { new Range(0, 0) },
                new Range[] { new Range(0, 0) });
    }

    @TestAllHashModes
    public void hashA_2KB_File(HashMode hashMode) throws IOException {
        setUp(hashMode);

        checkFileHash(hashMode, SIZE_2_KB + 157,
                new Range[] { new Range(0, SIZE_2_KB + 157) },
                new Range[] { new Range(0, SIZE_2_KB + 157) });
    }

    @TestAllHashModes
    public void hashA_4KB_File(HashMode hashMode) throws IOException {
        setUp(hashMode);

        checkFileHash(hashMode, SIZE_4_KB + 201,
                new Range[] { new Range(0, SIZE_4_KB) },
                new Range[] { new Range(0, SIZE_4_KB + 201) });
    }

    @TestAllHashModes
    public void hashA_6KB_File(HashMode hashMode) throws IOException {
        setUp(hashMode);

        checkFileHash(hashMode, SIZE_6_KB + 323,
                new Range[] { new Range(0, SIZE_4_KB) },
                new Range[] { new Range(0, SIZE_6_KB + 323) });
    }

    @TestAllHashModes
    public void hashA_8KB_File(HashMode hashMode) throws IOException {
        setUp(hashMode);

        checkFileHash(hashMode, SIZE_8_KB + 723,
                new Range[] { new Range(SIZE_4_KB, SIZE_8_KB) },
                new Range[] { new Range(0, SIZE_8_KB + 723) });
    }

    @TestAllHashModes
    public void hashA_10KB_File(HashMode hashMode) throws IOException {
        setUp(hashMode);

        checkFileHash(hashMode, SIZE_10_KB + 671,
                new Range[] { new Range(SIZE_4_KB, SIZE_8_KB) },
                new Range[] { new Range(0, SIZE_10_KB + 671) });
    }

    @TestAllHashModes
    public void hashA_30KB_File(HashMode hashMode) throws IOException {
        setUp(hashMode);

        checkFileHash(hashMode, SIZE_30_KB + 257,
                new Range[] { new Range(SIZE_4_KB, SIZE_8_KB), new Range(SIZE_12_KB, SIZE_12_KB + SIZE_4_KB),
                        new Range(SIZE_24_KB, SIZE_24_KB + SIZE_4_KB) },
                new Range[] { new Range(0, SIZE_30_KB + 257) });
    }

    @TestAllHashModes
    public void hashA_1MB_File(HashMode hashMode) throws IOException {
        setUp(hashMode);

        checkFileHash(hashMode, SIZE_1_MB + 91,
                new Range[] { new Range(SIZE_4_KB, SIZE_8_KB), new Range(SIZE_512_KB, SIZE_512_KB + SIZE_4_KB), new Range(SIZE_1_MB - SIZE_4_KB,
                        SIZE_1_MB) },
                new Range[] { new Range(0, SIZE_1_MB) });
    }

    @TestAllHashModes
    public void hashA_2MB_File(HashMode hashMode) throws IOException {
        setUp(hashMode);

        checkFileHash(hashMode, SIZE_2_MB + 51,
                new Range[] { new Range(SIZE_4_KB, SIZE_8_KB), new Range(SIZE_1_MB, SIZE_1_MB + SIZE_4_KB), new Range(SIZE_2_MB - SIZE_4_KB,
                        SIZE_2_MB) },
                new Range[] { new Range(SIZE_1_MB, SIZE_2_MB) });
    }

    @TestAllHashModes
    public void hashA_3MB_File(HashMode hashMode) throws IOException {
        setUp(hashMode);

        checkFileHash(hashMode, SIZE_3_MB + 101,
                new Range[] { new Range(SIZE_4_KB, SIZE_8_KB), new Range(SIZE_1_MB + SIZE_512_KB, SIZE_1_MB + SIZE_512_KB + SIZE_4_KB), new Range(
                        SIZE_3_MB - SIZE_4_KB, SIZE_3_MB) },
                new Range[] { new Range(SIZE_1_MB, SIZE_2_MB), new Range(SIZE_2_MB, SIZE_3_MB) });
    }

    @TestAllHashModes
    public void hashA_4MB_File(HashMode hashMode) throws IOException {
        setUp(hashMode);

        checkFileHash(hashMode, (4 * SIZE_1_MB) + (6 * SIZE_1_KB) + 594,
                new Range[] { new Range(SIZE_4_KB, SIZE_8_KB), new Range(SIZE_2_MB, SIZE_2_MB + SIZE_4_KB),
                        new Range(4 * SIZE_1_MB, (4 * SIZE_1_MB) + SIZE_4_KB) },
                new Range[] { new Range(SIZE_1_MB, SIZE_2_MB), new Range(SIZE_2_MB, SIZE_3_MB), new Range(SIZE_3_MB, 4 * SIZE_1_MB) });
    }

    @TestAllHashModes
    public void hashA_60MB_File(HashMode hashMode) throws IOException {
        setUp(hashMode);

        checkFileHash(hashMode, SIZE_60_MB + 291,
                new Range[] { new Range(SIZE_4_KB, SIZE_8_KB), new Range(SIZE_30_MB, SIZE_30_MB + SIZE_4_KB), new Range(SIZE_60_MB - SIZE_4_KB,
                        SIZE_60_MB) },
                new Range[] { new Range(SIZE_1_MB, SIZE_2_MB), new Range(SIZE_30_MB, SIZE_30_MB + SIZE_1_MB), new Range(SIZE_60_MB - SIZE_1_MB,
                        SIZE_60_MB) });
    }

    // This is a heavy test that takes several hours to run and cannot be run every time.
    @TestAllHashModes
    @Disabled
    public void checkHashIsCompleteInEveryCases(HashMode hashMode) throws IOException {
        setUp(hashMode);

        if (hashMode != dontHash) {
            int initialSize = 4190000;
            Path file = createFileWithSize(initialSize - 1);
            for (int fileSize = initialSize; fileSize < (10 * SIZE_1_MB); fileSize++) {
                byte contentByte = getContentByte(globalSequenceCount, false);
                globalSequenceCount++;
                Files.write(file, new byte[] { contentByte }, CREATE, APPEND);

                cut.hashFile(file, Files.size(file));
            }
        }
    }

    private void checkFileHash(HashMode hashMode, long fileSize, Range[] smallRanges, Range[] mediumRanges) throws IOException {
        Path fileToHash = createFileWithSize((int) fileSize);

        // Compute the expectedHash using a very simple algorithm and Guava Sha512 impl
        FileHash expectedHash = computeExpectedHash(fileToHash, smallRanges, mediumRanges);

        FileHash fileHash = cut.hashFile(fileToHash, Files.size(fileToHash));

        assertRangesEqualsTo(hashMode, smallRanges, mediumRanges);

        // displayFileHash(fileSize, fileHash);

        assertFileHashEqualsTo(hashMode, fileSize, expectedHash, fileHash);
    }

    /*
    private void displayFileHash(long fileSize, FileHash fileHash) {
        System.out.println("File " + byteCountToDisplaySize(fileSize));
        System.out.println("\tsmallBlockHash=" + fileHash.getSmallBlockHash());
        System.out.println("\tmediumBlockHash=" + fileHash.getMediumBlockHash());
        System.out.println("\tfullHash=" + fileHash.getFullHash());
        System.out.println();
    }
    */

    private void assertRangesEqualsTo(HashMode hashMode, Range[] smallRanges, Range[] mediumRanges) {
        if (hashMode != dontHash) {
            BlockHasher smallBlockHasher = (BlockHasher) cut.getFrontHasher().getSmallBlockHasher();
            assertThat(smallBlockHasher.getRanges()).isEqualTo(smallRanges);

            if (hashMode != hashSmallBlock) {
                BlockHasher mediumBlockHasher = (BlockHasher) cut.getFrontHasher().getMediumBlockHasher();
                assertThat(mediumBlockHasher.getRanges()).isEqualTo(mediumRanges);
            }
        }
    }

    private void assertFileHashEqualsTo(HashMode hashMode, long fileSize, FileHash expectedFileHash, FileHash fileHash) {
        long expectedSmallSizeToHash = getExpectedSizeToHash(fileSize, SIZE_4_KB);
        long expectedMediumSizeToHash = getExpectedSizeToHash(fileSize, SIZE_1_MB);
        switch (hashMode) {
            case dontHash:
                assertThat(fileHash.getSmallBlockHash()).isEqualTo(NO_HASH);
                assertThat(fileHash.getMediumBlockHash()).isEqualTo(NO_HASH);
                assertThat(fileHash.getFullHash()).isEqualTo(NO_HASH);

                assertSmallBlockBytesHashedEqualsTo(0);
                assertMediumBlockBytesHashedEqualsTo(0);
                assertFullBytesHashedEqualsTo(0);
                assertMaxBytesHashedEqualsTo(0);
                break;

            case hashSmallBlock:
                assertThat(fileHash.getSmallBlockHash()).isEqualTo(expectedFileHash.getSmallBlockHash());
                assertThat(fileHash.getMediumBlockHash()).isEqualTo(NO_HASH);
                assertThat(fileHash.getFullHash()).isEqualTo(NO_HASH);

                assertSmallBlockBytesHashedEqualsTo(expectedSmallSizeToHash);
                assertMediumBlockBytesHashedEqualsTo(0);
                assertFullBytesHashedEqualsTo(0);
                assertMaxBytesHashedEqualsTo(expectedSmallSizeToHash);
                break;

            case hashMediumBlock:
                assertThat(fileHash.getSmallBlockHash()).isEqualTo(expectedFileHash.getSmallBlockHash());
                assertThat(fileHash.getMediumBlockHash()).isEqualTo(expectedFileHash.getMediumBlockHash());
                assertThat(fileHash.getFullHash()).isEqualTo(NO_HASH);

                assertSmallBlockBytesHashedEqualsTo(expectedSmallSizeToHash);
                assertMediumBlockBytesHashedEqualsTo(expectedMediumSizeToHash);
                assertFullBytesHashedEqualsTo(0);
                assertMaxBytesHashedEqualsTo(expectedMediumSizeToHash);
                break;

            case hashAll:
                assertThat(fileHash.getSmallBlockHash()).isEqualTo(expectedFileHash.getSmallBlockHash());
                assertThat(fileHash.getMediumBlockHash()).isEqualTo(expectedFileHash.getMediumBlockHash());
                assertThat(fileHash.getFullHash()).isEqualTo(expectedFileHash.getFullHash());

                assertSmallBlockBytesHashedEqualsTo(expectedSmallSizeToHash);
                assertMediumBlockBytesHashedEqualsTo(expectedMediumSizeToHash);
                assertFullBytesHashedEqualsTo(fileSize);
                assertMaxBytesHashedEqualsTo(fileSize);
                break;
        }
    }

    private long getExpectedSizeToHash(long fileSize, int blockSize) {
        long sizeToHash;
        if (fileSize > 4L * blockSize) {
            sizeToHash = 3L * blockSize;
        } else if (fileSize > 3L * blockSize) {
            sizeToHash = 2L * blockSize;
        } else {
            sizeToHash = blockSize;
        }
        sizeToHash = min(fileSize, sizeToHash);
        return sizeToHash;
    }

    private void assertSmallBlockBytesHashedEqualsTo(long expectedSizeToHash) {
        assertBlockBytesHashedEqualsTo(expectedSizeToHash, (BlockHasher) cut.getFrontHasher().getSmallBlockHasher());
    }

    private void assertMediumBlockBytesHashedEqualsTo(long expectedSizeToHash) {
        assertBlockBytesHashedEqualsTo(expectedSizeToHash, (BlockHasher) cut.getFrontHasher().getMediumBlockHasher());
    }

    private void assertBlockBytesHashedEqualsTo(long expectedSizeToHash, BlockHasher blockHasher) {
        long sizeToHash = blockHasher.getSizeToHash();
        long bytesHashed = blockHasher.getBytesHashed();

        assertThat(sizeToHash).isEqualTo(bytesHashed);
        assertThat(bytesHashed).isEqualTo(expectedSizeToHash);
    }

    private void assertFullBytesHashedEqualsTo(long expectedSizeToHash) {
        assertThat(cut.getFrontHasher().getFullHasher().getBytesHashed()).isEqualTo(expectedSizeToHash);
    }

    private void assertMaxBytesHashedEqualsTo(long expectedSizeToHash) {
        assertThat(cut.getFrontHasher().getBytesHashed()).isEqualTo(expectedSizeToHash);
    }

    private Path createFileWithSize(int fileSize) throws IOException {
        Path newFile = context.getRepositoryRootDir().resolve("file_" + fileSize);
        if (Files.exists(newFile)) {
            Files.delete(newFile);
        }

        if (fileSize == 0) {
            Files.createFile(newFile);
            return newFile;
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream(fileSize)) {
            int contentSize = SIZE_1_KB / 4;
            int remaining = fileSize;
            for (; remaining > 0; globalSequenceCount++) {
                int size = min(contentSize, remaining);
                byte[] content = generateContent(globalSequenceCount, size);
                remaining -= size;
                out.write(content);
            }

            byte[] fileContent = out.toByteArray();
            assertThat(fileContent.length).isEqualTo(fileSize);
            Files.write(newFile, fileContent, CREATE);
        }

        return newFile;
    }

    private byte[] generateContent(long sequenceCount, int contentSize) {
        byte[] content = new byte[contentSize];
        for (int index = 0; index < contentSize; index += 2) {
            content[index] = getContentByte(sequenceCount, false);
            if (index + 1 < contentSize) {
                content[index + 1] = getContentByte(sequenceCount, true);
            }
        }
        return content;
    }

    private byte getContentByte(long sequenceCount, boolean fromTheEnd) {
        int index = (int) (sequenceCount % CONTENT_BYTES.length);
        if (fromTheEnd) {
            index = CONTENT_BYTES.length - 1 - index;
        }
        return CONTENT_BYTES[index];
    }

    private FileHash computeExpectedHash(Path fileToHash, Range[] smallRanges, Range[] mediumRanges) throws IOException {
        byte[] fullContent = Files.readAllBytes(fileToHash);
        String smallBlockHash = generateBlockHash(fullContent, smallRanges);
        String mediumBlockHash = generateBlockHash(fullContent, mediumRanges);
        String fullHash = generateFullHash(fullContent);

        return new FileHash(smallBlockHash, mediumBlockHash, fullHash);
    }

    private String generateBlockHash(byte[] fullContent, Range[] ranges) {
        HashFunction hashFunction = Hashing.sha512();
        com.google.common.hash.Hasher hasher = hashFunction.newHasher(SIZE_100_MB);

        for (Range range : ranges) {
            byte[] content = extractBlock(fullContent, range);
            hasher.putBytes(content);
        }

        HashCode hash = hasher.hash();
        return ascii85Encode(hash.asBytes());
    }

    private String generateFullHash(byte[] fullContent) {
        return hashContent(fullContent);
    }

    private byte[] extractBlock(byte[] fullContent, Range range) {
        return Arrays.copyOfRange(fullContent, (int) range.getFrom(), (int) range.getTo());
    }

    private String hashContent(byte[] content) {
        HashFunction hashFunction = Hashing.sha512();
        com.google.common.hash.Hasher hasher = hashFunction.newHasher(SIZE_100_MB);
        hasher.putBytes(content);
        HashCode hash = hasher.hash();
        return ascii85Encode(hash.asBytes());
    }

    private static String ascii85Encode(byte[] bytesToBeEncoded) {
        return new String(Ascii85Encoder.encode(bytesToBeEncoded), UTF8);
    }
}
