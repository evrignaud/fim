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

package org.fim.internal;

import org.fim.model.Attribute;
import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.model.FileHash;
import org.fim.model.FileState;
import org.fim.model.FileTime;
import org.fim.model.State;
import org.fim.util.TimeElapsed;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled // Don't run it during unit tests
public class StateComparatorPerformanceTest {
    private static final Comparator<FileState> FILE_NAME_COMPARATOR = new FileState.FileNameComparator();
    private static final String CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    public void canCompareQuicklyTwoBigStates() {
        TimeElapsed te = new TimeElapsed();
        Context context = new Context();

        int count = 1_000_000;
        State lastState = createState(count);
        State currentState = createState(count);

        CompareResult result = new StateComparator(context, lastState, currentState).compare().displayChanges("Nothing modified");
        assertThat(result.getAdded().size()).isEqualTo(count);
        assertThat(result.getDeleted().size()).isEqualTo(count);
        long duration = te.getDuration();
        System.out.printf("Duration = %d ms%n", duration);
        assertThat(duration).isLessThan(4 * 60 * 1000);
    }

    private static State createState(int count) {
        State state = new State();
        for (int index = 0; index < count; index++) {
            if ((index % 100_000) == 0) {
                System.out.print('.');
            }

            String fileName = randomString(50);
            long fileLength = RANDOM.nextInt(10 * 1024 * 1024) + 120;
            long now = System.currentTimeMillis();
            FileTime fileTime = new FileTime(RANDOM.nextInt((int) now), RANDOM.nextInt((int) now));
            FileHash fileHash = new FileHash(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString());
            List<Attribute> attributeList = new ArrayList<>();
            FileState fileState = new FileState(fileName, fileLength, fileTime, fileHash, attributeList);

            state.getFileStates().add(fileState);
        }
        state.getFileStates().sort(FILE_NAME_COMPARATOR);

        System.out.println();

        return state;
    }

    private static String randomString(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int index = 0; index < len; index++) {
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }
}
