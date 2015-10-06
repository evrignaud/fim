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

import static org.assertj.core.api.Assertions.assertThat;
import static org.fim.model.HashMode.hashSmallBlock;
import static org.fim.model.TestContants._12_KB;
import static org.fim.model.TestContants._16_KB;
import static org.fim.model.TestContants._1_KB;
import static org.fim.model.TestContants._20_KB;
import static org.fim.model.TestContants._4_KB;
import static org.fim.model.TestContants._5_KB;
import static org.fim.model.TestContants._8_KB;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import org.fim.model.HashMode;
import org.junit.Before;
import org.junit.Test;

public class BlockHasherTest
{
	private BlockHasher cut;

	@Before
	public void setup() throws NoSuchAlgorithmException, IOException
	{
		cut = new BlockHasher(hashSmallBlock)
		{
			@Override
			protected int getBlockSize()
			{
				return _4_KB;
			}

			@Override
			protected boolean isCompatible(HashMode hashMode)
			{
				return true;
			}
		};
	}

	@Test
	public void calculateBlockIndex()
	{
		cut.reset(31);
		assertThat(cut.getMiddleBlockIndex()).isEqualTo(0);

		cut.reset(_4_KB + 109);
		assertThat(cut.getMiddleBlockIndex()).isEqualTo(0);

		cut.reset(_8_KB + 205);
		assertThat(cut.getMiddleBlockIndex()).isEqualTo(1);

		cut.reset(_12_KB + 301);
		assertThat(cut.getMiddleBlockIndex()).isEqualTo(1);
		assertThat(cut.getEndBlockIndex()).isEqualTo(2);

		cut.reset(_16_KB + 407);
		assertThat(cut.getMiddleBlockIndex()).isEqualTo(2);
		assertThat(cut.getEndBlockIndex()).isEqualTo(3);

		cut.reset(_20_KB + 509);
		assertThat(cut.getMiddleBlockIndex()).isEqualTo(2);
		assertThat(cut.getEndBlockIndex()).isEqualTo(4);
	}

	@Test
	public void rangesAreCalculatedCorrectly()
	{
		cut.reset(31);
		assertThat(cut.getRanges()).isEqualTo(new Range[]{new Range(0, 31)});

		cut.reset(_4_KB + 109);
		assertThat(cut.getRanges()).isEqualTo(new Range[]{new Range(0, _4_KB)});

		cut.reset(_8_KB + 205);
		assertThat(cut.getRanges()).isEqualTo(new Range[]{new Range(_4_KB, _8_KB)});

		cut.reset(_12_KB + 301);
		assertThat(cut.getRanges()).isEqualTo(new Range[]{new Range(_4_KB, _8_KB), new Range(_8_KB, _12_KB)});

		cut.reset(_16_KB + 407);
		assertThat(cut.getRanges()).isEqualTo(new Range[]{new Range(_4_KB, _8_KB), new Range(_8_KB, _12_KB), new Range(_12_KB, _16_KB)});

		cut.reset(_20_KB + 509);
		assertThat(cut.getRanges()).isEqualTo(new Range[]{new Range(_4_KB, _8_KB), new Range(_8_KB, _12_KB), new Range(_16_KB, _20_KB)});
	}

	@Test
	public void weCanRetrieveTheNextRange()
	{
		cut.reset(_20_KB + 509);
		assertThat(cut.getNextRange(0)).isEqualTo(new Range(_4_KB, _8_KB));

		assertThat(cut.getNextRange(_1_KB)).isEqualTo(new Range(_4_KB, _8_KB));

		assertThat(cut.getNextRange(_4_KB)).isEqualTo(new Range(_4_KB, _8_KB));

		assertThat(cut.getNextRange(_4_KB + 1)).isEqualTo(new Range(_8_KB, _12_KB));

		assertThat(cut.getNextRange(_5_KB)).isEqualTo(new Range(_8_KB, _12_KB));

		assertThat(cut.getNextRange(_12_KB)).isEqualTo(new Range(_16_KB, _20_KB));

		assertThat(cut.getNextRange(_20_KB)).isEqualTo(null);
	}
}
