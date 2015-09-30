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
import static org.fim.model.HashMode.hashAll;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;

import org.fim.model.HashMode;
import org.junit.Before;
import org.junit.Test;

public class HasherTest
{
	private Hasher cut;

	@Before
	public void setup() throws NoSuchAlgorithmException, IOException
	{
		cut = new Hasher(hashAll, hashAll)
		{
			@Override
			protected long computeSizeToHash(HashMode blockHashMode)
			{
				return 0;
			}

			@Override
			protected long computeStartPosition(long fileSize)
			{
				return 0;
			}

			@Override
			protected ByteBuffer getBlockToHash(long position, ByteBuffer buffer)
			{
				return null;
			}
		};
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
}
