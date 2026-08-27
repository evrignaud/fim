/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2026 Etienne Vrignaud
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

package org.fim.tooling;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import static org.assertj.core.api.Assertions.assertThat;
import org.fim.internal.InitProgressManager;
import org.fim.model.Context;
import org.fim.util.DosFilePermissions;
import org.junit.jupiter.api.TestInfo;

/**
 * A throwaway directory to run the fim command line against, and the means to arrange files in it.
 *
 * The files are small on purpose. {@link RepositoryTool} builds files of at least 10 MiB, which is what the
 * block hashing tests need but also what makes the existing scenario suites take tens of seconds. Nothing
 * about dispatching a command, parsing an option or writing a State depends on the size of the files, so the
 * behaviour of the command line can be covered with kilobytes and stay quick. The cases that genuinely need
 * large files, where a mode hashing three blocks has to miss a change that full hashing catches, are in the
 * slow tier and ask for the size they need.
 */
public class IntegrationRepository {
    /** Small enough to be quick, large enough to hold recognisable content. */
    public static final int DEFAULT_FILE_SIZE = 2 * 1_024;

    private final Path rootDir;
    private final FimCli fim;

    public IntegrationRepository(TestInfo testInfo) throws IOException {
        this(testInfo, null);
    }

    public IntegrationRepository(TestInfo testInfo, String variant) throws IOException {
        String testClassName = testInfo.getTestClass().orElseThrow().getSimpleName();
        String testName = testInfo.getTestMethod().orElseThrow().getName();
        String directoryName = TestConstants.BUILD_TEST_OUTPUTS + "/" + testClassName + "-" + testName
                               + (variant == null ? "" : "-" + variant);

        rootDir = Paths.get(directoryName);
        FileUtils.deleteDirectory(rootDir.toFile());
        Files.createDirectories(rootDir);

        fim = new FimCli(rootDir);
    }

    public Path rootDir() {
        return rootDir;
    }

    public FimCli fim() {
        return fim;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Where Fim keeps its own content
    // -----------------------------------------------------------------------------------------------------------------

    public Path dotFimDir() {
        return rootDir.resolve(Context.DOT_FIM_DIR);
    }

    public Path statesDir() {
        return dotFimDir().resolve("states");
    }

    public Path settingsFile() {
        return dotFimDir().resolve("settings.json");
    }

    public Path stateFile(int stateNumber) {
        return statesDir().resolve("state_" + stateNumber + ".json.gz");
    }

    /** How many States the repository holds, counted from the files rather than from the settings. */
    public int stateCount() {
        int count = 0;
        while (Files.exists(stateFile(count + 1))) {
            count++;
        }
        return count;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Arranging files
    // -----------------------------------------------------------------------------------------------------------------

    public Path resolve(String relativeName) {
        return rootDir.resolve(relativeName);
    }

    public boolean exists(String relativeName) {
        return Files.exists(resolve(relativeName));
    }

    public Path createDirectory(String relativeName) throws IOException {
        Path directory = resolve(relativeName);
        Files.createDirectories(directory);
        return directory;
    }

    /**
     * Create a file whose content is derived from its name, so that two files created this way are never
     * duplicates of one another unless a test asks for that by copying one.
     */
    public Path createFile(String relativeName) throws IOException {
        return createFile(relativeName, "content of " + relativeName);
    }

    public Path createFile(String relativeName, String content) throws IOException {
        return createFile(relativeName, content, DEFAULT_FILE_SIZE);
    }

    public Path createFile(String relativeName, String content, int size) throws IOException {
        Path file = resolve(relativeName);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.write(file, padded(content, size), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return file;
    }

    public void createFiles(String... relativeNames) throws IOException {
        for (String relativeName : relativeNames) {
            createFile(relativeName);
        }
    }

    /** Files whose content is identical, so they are duplicates of each other. */
    public void createIdenticalFiles(String content, String... relativeNames) throws IOException {
        createIdenticalFiles(content, DEFAULT_FILE_SIZE, relativeNames);
    }

    /**
     * Duplicates of a chosen size, for a test that needs two duplicate sets to come out in a known order.
     * Sets are listed by wasted space, so two sets of the same size and count could be reported either way
     * round and a test relying on that order would be flaky.
     */
    public void createIdenticalFiles(String content, int size, String... relativeNames) throws IOException {
        for (String relativeName : relativeNames) {
            createFile(relativeName, content, size);
        }
    }

    public void setContent(String relativeName, String content) throws IOException {
        createFile(relativeName, content);
    }

    public void appendTo(String relativeName, String content) throws IOException {
        Files.write(resolve(relativeName), content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
    }

    public void copy(String from, String to) throws IOException {
        Path target = resolve(to);
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.copy(resolve(from), target, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
    }

    public void move(String from, String to) throws IOException {
        Path target = resolve(to);
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.move(resolve(from), target, StandardCopyOption.REPLACE_EXISTING);
    }

    public void delete(String relativeName) throws IOException {
        Files.delete(resolve(relativeName));
    }

    public void writeFimIgnore(String relativeDirectory, String content) throws IOException {
        Path directory = relativeDirectory.isEmpty() ? rootDir : resolve(relativeDirectory);
        Files.createDirectories(directory);
        Files.write(directory.resolve(".fimignore"), content.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Timestamps
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Push the modification time forward by more than a second, so that a filesystem storing times to the
     * second still reports the change.
     */
    public void touchLastModified(String relativeName) throws IOException {
        Path file = resolve(relativeName);
        long stamp = Math.max(System.currentTimeMillis(), Files.getLastModifiedTime(file).toMillis()) + 2_000;
        Files.setLastModifiedTime(file, FileTime.fromMillis(stamp));
    }

    /**
     * Change what a file holds while leaving both of its timestamps as they were.
     *
     * This is what a silent corruption looks like from the outside, and it is the case `dcor` exists to find:
     * the content no longer matches the recorded hash, yet nothing about the file claims it was touched.
     */
    public void corruptContentKeepingTimestamps(String relativeName, String content) throws IOException {
        Path file = resolve(relativeName);
        BasicFileAttributes before = Files.readAttributes(file, BasicFileAttributes.class);

        Files.write(file, padded(content, DEFAULT_FILE_SIZE),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        restoreTimes(file, before);
    }

    /**
     * Overwrite a few bytes at one position, leaving the length and both timestamps untouched.
     *
     * This is how a change is placed at a chosen depth in a file, which is what separates the hash modes: a
     * mode reading three blocks can only notice a change that falls inside one of them, while full hashing
     * reads everything and notices wherever it lands. Nothing else about the file changes, so the mode is the
     * only reason one run sees it and another does not.
     */
    public void overwriteBytesKeepingSizeAndTimestamps(String relativeName, long offset, String replacement)
            throws IOException {
        Path file = resolve(relativeName);
        BasicFileAttributes before = Files.readAttributes(file, BasicFileAttributes.class);

        byte[] bytes = replacement.getBytes(StandardCharsets.UTF_8);
        if (offset + bytes.length > before.size()) {
            throw new IllegalArgumentException(
                    "writing " + bytes.length + " bytes at " + offset + " would grow " + relativeName
                    + ", which is " + before.size() + " bytes long");
        }

        try (java.nio.channels.FileChannel channel =
                     java.nio.channels.FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.write(java.nio.ByteBuffer.wrap(bytes), offset);
        }

        restoreTimes(file, before);
    }

    private static void restoreTimes(Path file, BasicFileAttributes before) throws IOException {
        Files.getFileAttributeView(file, BasicFileAttributeView.class)
                .setTimes(before.lastModifiedTime(), before.lastAccessTime(), before.creationTime());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Permissions
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Make a file read-only, which is a change of attributes rather than of content. Expressed for both
     * platforms because Fim records POSIX permissions on Linux and Mac OS X and the DOS attributes on Windows.
     */
    public void setReadOnly(String relativeName) throws IOException {
        setPermissions(relativeName, "r--r--r--", "R");
    }

    public void setReadWrite(String relativeName) throws IOException {
        setPermissions(relativeName, "rw-rw-r--", "");
    }

    public void setPermissions(String relativeName, String posixPermissions, String dosPermissions) throws IOException {
        Path file = resolve(relativeName);
        if (SystemUtils.IS_OS_WINDOWS) {
            DosFilePermissions.setPermissions(new Context(), file, dosPermissions);
        } else {
            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString(posixPermissions);
            Files.getFileAttributeView(file, PosixFileAttributeView.class).setPermissions(permissions);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Damaging the repository itself
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Change something inside a State file while leaving the hash it carries alone.
     *
     * A State stores a hash of its own content, so this is what a State that was edited or damaged on disk
     * looks like: it still unzips, it still parses, but it no longer agrees with its own hash. Overwriting
     * raw bytes instead would break the gzip wrapper and fail earlier, somewhere less interesting.
     *
     * @param search a fragment of the stored json, which must be present or the test would silently pass
     */
    public void tamperWithState(int stateNumber, String search, String replacement) throws IOException {
        Path stateFile = stateFile(stateNumber);
        String json;
        try (Reader reader = new InputStreamReader(new GZIPInputStream(Files.newInputStream(stateFile)), StandardCharsets.UTF_8)) {
            json = IOUtils.toString(reader);
        }

        assertThat(json)
                .withFailMessage("the State file does not contain '%s', so tampering with it would change nothing", search)
                .contains(search);
        json = json.replace(search, replacement);

        try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(stateFile)), StandardCharsets.UTF_8)) {
            writer.write(json);
        }
    }

    /**
     * Put the repository in the state an interrupted {@code fim init} leaves behind: hashes saved as
     * progress, and no committed State yet.
     *
     * Truly interrupting an init would mean killing the JVM partway through, which a test cannot do to
     * itself. Rearranging the files the same way exercises the same code, since what the resume path reads is
     * the progress file, not the reason it exists.
     */
    public void makeInitLookInterrupted() throws IOException {
        Files.move(stateFile(1), dotFimDir().resolve(InitProgressManager.INIT_PROGRESS_FILE));
        FileUtils.deleteDirectory(statesDir().toFile());
        Files.deleteIfExists(settingsFile());
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Repeat the content until the requested size is reached, so that a file is recognisable from its bytes
     * and two files with different content never come out the same length by accident.
     */
    private static byte[] padded(String content, int size) {
        StringBuilder builder = new StringBuilder(size + content.length() + 16);
        int line = 0;
        while (builder.length() < size) {
            line++;
            builder.append(line).append(": ").append(content).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }
}
