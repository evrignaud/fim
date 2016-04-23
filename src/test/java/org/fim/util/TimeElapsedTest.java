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
package org.fim.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class TimeElapsedTest
{
	private boolean startCalled = false;
	private boolean stopCalled = false;

	@Test
	public void weCanEstimateTheElapsedTime() throws InterruptedException
	{
		TimeElapsed cut = new MyTimeElapsed();
		assertThat(startCalled).isEqualTo(true);
		startCalled = false;

		Thread.sleep(10);

		assertThat(cut.getDuration()).isGreaterThan(10);
		assertThat(stopCalled).isEqualTo(true);
		assertThat(startCalled).isEqualTo(true);
	}

	private class MyTimeElapsed extends TimeElapsed
	{
		@Override
		public void start()
		{
			startCalled = true;
			super.start();
		}

		@Override
		protected void stop()
		{
			stopCalled = true;
			super.stop();
		}
	}
}
