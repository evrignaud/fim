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

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.io.FileUtils;
import org.fim.internal.hash.FileHasherTest;
import org.fim.model.FileToIgnore;
import org.fim.model.FimIgnore;
import org.fim.tooling.StateAssert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

public class FimIgnoreManagerTest extends StateAssert
{
	private static Path rootDir = Paths.get("target/" + FileHasherTest.class.getSimpleName());

	private FimIgnoreManager cut = new FimIgnoreManager(defaultContext());

	private BasicFileAttributes fileAttributes;

	@BeforeClass
	public static void setupOnce() throws NoSuchAlgorithmException, IOException
	{
		FileUtils.deleteDirectory(rootDir.toFile());
		Files.createDirectories(rootDir);
	}

	@Test
	public void filesCanBeIgnored()
	{
		fileAttributes = Mockito.mock(BasicFileAttributes.class);
		Mockito.when(fileAttributes.isDirectory()).thenReturn(false);

		FimIgnore fimIgnore = new FimIgnore();
		ignoreFile(fimIgnore, "foo");
		ignoreFile(fimIgnore, "bar*");
		ignoreFile(fimIgnore, "*baz*");
		ignoreFile(fimIgnore, "*.mp3");
		ignoreFile(fimIgnore, "$Data[1]|2(3)*");
		ignoreFile(fimIgnore, "****qux****");

		assertFileIgnored("foo", fimIgnore);
		assertFileNotIgnored("a_foo", fimIgnore);

		assertFileIgnored("bar_yes", fimIgnore);
		assertFileNotIgnored("yes_bar", fimIgnore);

		assertFileIgnored("no_baz_yes", fimIgnore);

		assertFileIgnored("track12.mp3", fimIgnore);
		assertFileNotIgnored("track12_mp3", fimIgnore);

		assertFileIgnored("$Data[1]|2(3)_file", fimIgnore);

		assertFileIgnored("****qux****", fimIgnore);
	}

	@Test
	public void fileToIgnoreListDontAllowDuplicates()
	{
		FimIgnore fimIgnore = new FimIgnore();
		ignoreFile(fimIgnore, "foo");
		ignoreFile(fimIgnore, "foo");

		assertThat(fimIgnore.getFilesToIgnoreLocally().size()).isEqualTo(1);
	}

	@Test
	public void weCanLoadCorrectlyAFimIgnore() throws IOException
	{
		FimIgnore fimIgnore = cut.loadFimIgnore(rootDir);
		assertThat(fimIgnore.getFilesToIgnoreLocally().size()).isEqualTo(0);
		assertThat(fimIgnore.getFilesToIgnoreInAllDirectories().size()).isEqualTo(0);

		String fileContent = "**/*.mp3\n" +
				"*.mp4\n" +
				"**/.git\n" +
				"foo\n" +
				"**/bar";
		Files.write(rootDir.resolve(".fimignore"), fileContent.getBytes(), CREATE, APPEND);

		fimIgnore = cut.loadFimIgnore(rootDir);
		assertThat(fimIgnore.getFilesToIgnoreLocally().toString()).isEqualTo(
				"[FileToIgnore{fileNamePattern=foo, compiledPattern=^foo$}," +
						" FileToIgnore{fileNamePattern=*.mp4, compiledPattern=^.*\\.mp4$}]");

		assertThat(fimIgnore.getFilesToIgnoreInAllDirectories().toString()).isEqualTo(
				"[FileToIgnore{fileNamePattern=bar, compiledPattern=^bar$}," +
						" FileToIgnore{fileNamePattern=.git, compiledPattern=^\\.git$}," +
						" FileToIgnore{fileNamePattern=*.mp3, compiledPattern=^.*\\.mp3$}]");
	}

	private void assertFileIgnored(String fileName, FimIgnore fimIgnore)
	{
		assertThat(cut.isIgnored(Paths.get(fileName), fileAttributes, fimIgnore)).isTrue();
	}

	private void assertFileNotIgnored(String fileName, FimIgnore fimIgnore)
	{
		assertThat(cut.isIgnored(Paths.get(fileName), fileAttributes, fimIgnore)).isFalse();
	}

	private void ignoreFile(FimIgnore fimIgnore, String fileNamePattern)
	{
		FileToIgnore fileToIgnore;
		fileToIgnore = new FileToIgnore(fileNamePattern);
		fimIgnore.getFilesToIgnoreLocally().add(fileToIgnore);
	}
}
