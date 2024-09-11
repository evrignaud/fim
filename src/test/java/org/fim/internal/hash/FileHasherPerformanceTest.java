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

import org.fim.model.Constants;
import org.fim.model.Context;
import org.fim.model.FileHash;
import org.fim.tooling.RepositoryTool;
import org.fim.tooling.StateAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.min;
import static java.nio.file.StandardOpenOption.CREATE;
import static org.apache.commons.lang3.time.DurationFormatUtils.formatDuration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.HashMode.hashSmallBlock;
import static org.fim.tooling.TestConstants.SIZE_1_KB;
import static org.fim.util.FileUtil.byteCountToDisplaySize;
import static org.mockito.Mockito.mock;

@Disabled // Don't run it during unit tests
public class FileHasherPerformanceTest extends StateAssert {
    public static final int TOTAL_FILE_CONT = 2000;
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

    @BeforeEach
    public void setUp(TestInfo testInfo) throws NoSuchAlgorithmException, IOException {
        RepositoryTool tool = new RepositoryTool(testInfo, hashSmallBlock);
        Path rootDir = tool.getRootDir();
        context = tool.getContext();

        HashProgress hashProgress = mock(HashProgress.class);

        cut = new FileHasher(context, null, hashProgress, null, rootDir.toString());
    }

    @Test
    public void createFiles() throws IOException {
        long start = System.currentTimeMillis();

        for (int fileCount = 0; fileCount < TOTAL_FILE_CONT; fileCount++) {
            createFileWithSize(fileCount, (5 * Constants.SIZE_1_MB) + 291);
        }

        long duration = System.currentTimeMillis() - start;
        System.out.println("Took: " + formatDuration(duration, "HH:mm:ss"));
    }

    @Test
    public void hashFiles() throws IOException {
        long start = System.currentTimeMillis();

        List<FileHash> allHash = new ArrayList<>();
        for (int fileCount = 0; fileCount < TOTAL_FILE_CONT; fileCount++) {
            Path fileToHash = context.getRepositoryRootDir().resolve("file_" + fileCount);
            allHash.add(cut.hashFile(fileToHash, Files.size(fileToHash)));
        }

        assertThat(allHash.size()).isEqualTo(TOTAL_FILE_CONT);
        long duration = System.currentTimeMillis() - start;
        System.out.println("Took: " + formatDuration(duration, "HH:mm:ss"));
        System.out.println("Total bytes hash=" + byteCountToDisplaySize(cut.getTotalBytesHashed()));
    }

    private Path createFileWithSize(int fileCount, int fileSize) throws IOException {
        Path newFile = context.getRepositoryRootDir().resolve("file_" + fileCount);
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

}
