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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
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
public class FileHasherTest
{
	private final Charset utf8 = Charset.forName("UTF-8");

	@Mock
	private StateGenerator stateGenerator;

	private List<FileState> fileStates;
	private String baseDir;
	private File fileToHash;
	private FileHasher cut;

	@Before
	public void setup() throws NoSuchAlgorithmException
	{
		fileStates = new ArrayList<>();
		baseDir = "target/" + this.getClass().getSimpleName();
		fileToHash = new File("file_01");
		cut = new FileHasher(stateGenerator, fileStates, baseDir, fileToHash);
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
	public void weCanGetTheRelativeFileName()
	{
		String relativeFileName = cut.getRelativeFileName("/dir1/dir2/dir3", "/dir1/dir2/dir3/dir4/file1");
		assertThat(relativeFileName).isEqualTo("dir4/file1");

		relativeFileName = cut.getRelativeFileName("/dir5/dir6/dir7", "/dir1/dir2/dir3/dir4/file1");
		assertThat(relativeFileName).isEqualTo("dir1/dir2/dir3/dir4/file1");

		relativeFileName = cut.getRelativeFileName("/dir1/dir2/dir3", "dir4/file1");
		assertThat(relativeFileName).isEqualTo("dir4/file1");
	}

	@Test
	public void weCanHashALittleFile()
	{
		fileToHash = new File("LICENSE");
		String hash = cut.hashFile(fileToHash);
		assertThat(hash.length()).isEqualTo(128);
		assertThat(hash).isEqualTo("57547468f95220e8e0e265f0682b1dc787e123fa984d12482b38ef69b6f3a8e0843f36bccf4262f3c686e6a9fb55552ed386e295f72e6401f66480d2da6145d1");
	}

	@Test
	public void weCanHashABigFile() throws IOException
	{
		fileToHash = createBigLicenseFile(60 * 1024 * 1024);
		String hash = cut.hashFile(fileToHash);
		assertThat(hash.length()).isEqualTo(128);
		assertThat(hash).isEqualTo("e891a71e312bc6e34f549664706951516c42f660face62756bb155301c5e06ba79db94f83dedd43467530021935f5b427a58d7a5bd245ea1b2b0db8d7b08ee7a");
	}

	private File createBigLicenseFile(long fileSize) throws IOException
	{
		File license = new File("LICENSE");
		String content = FileUtils.readFileToString(license, utf8);

		File bigLicense = new File(baseDir, "BIG_LICENSE");
		if (bigLicense.exists())
		{
			bigLicense.delete();
		}

		do
		{
			FileUtils.writeStringToFile(bigLicense, content, true);
		}
		while (bigLicense.length() < fileSize);

		return bigLicense;
	}
}
