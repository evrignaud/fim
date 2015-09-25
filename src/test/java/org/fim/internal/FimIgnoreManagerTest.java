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

import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import org.fim.model.FileToIgnore;
import org.fim.tooling.StateAssert;
import org.junit.Test;
import org.mockito.Mockito;

public class FimIgnoreManagerTest extends StateAssert
{
	private FimIgnoreManager cut = new FimIgnoreManager(defaultContext());

	private BasicFileAttributes fileAttributes;

	@Test
	public void filesCanBeIgnored()
	{
		fileAttributes = Mockito.mock(BasicFileAttributes.class);
		Mockito.when(fileAttributes.isDirectory()).thenReturn(false);

		List<FileToIgnore> ignoreList = new ArrayList<>();
		ignoreFile(ignoreList, "foo");
		ignoreFile(ignoreList, "bar*");
		ignoreFile(ignoreList, "*baz*");
		ignoreFile(ignoreList, "*.mp3");
		ignoreFile(ignoreList, "$Data[1]|2(3)*");
		ignoreFile(ignoreList, "****qux****");

		assertFileIgnored("foo", ignoreList);
		assertFileNotIgnored("a_foo", ignoreList);

		assertFileIgnored("bar_yes", ignoreList);
		assertFileNotIgnored("yes_bar", ignoreList);

		assertFileIgnored("no_baz_yes", ignoreList);

		assertFileIgnored("track12.mp3", ignoreList);
		assertFileNotIgnored("track12_mp3", ignoreList);

		assertFileIgnored("$Data[1]|2(3)_file", ignoreList);

		assertFileIgnored("****qux****", ignoreList);
	}

	private void assertFileIgnored(String fileName, List<FileToIgnore> ignoreList)
	{
		assertThat(cut.isIgnored(Paths.get(fileName), fileAttributes, ignoreList)).isTrue();
	}

	private void assertFileNotIgnored(String fileName, List<FileToIgnore> ignoreList)
	{
		assertThat(cut.isIgnored(Paths.get(fileName), fileAttributes, ignoreList)).isFalse();
	}

	private void ignoreFile(List<FileToIgnore> localIgnore, String regexp)
	{
		FileToIgnore fileToIgnore;
		fileToIgnore = new FileToIgnore(regexp);
		localIgnore.add(fileToIgnore);
	}
}
