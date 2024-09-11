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

package org.fim.model;

import org.fim.tooling.BuildableState;
import org.fim.tooling.ObjectAssert;
import org.fim.tooling.StateAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.text.ParseException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class StateTest extends StateAssert {
    // public static final String MODIFICATION_TIME = "2015/07/23 23:24:10";
    public static final long MODIFICATION_TIMESTAMP = 1437686650000L; // MODIFICATION_TIME translated in milliseconds

    private BuildableState a1;
    private BuildableState a2;
    private BuildableState b;

    @BeforeEach
    public void setUp() {
        a1 = new BuildableState(defaultContext()).addFiles("file_1", "file_2");
        a2 = a1.clone();
        a2.setTimestamp(a1.getTimestamp());

        b = a1.delete("file_2").addFiles("file_3");
        b.setTimestamp(a1.getTimestamp());
    }

    @Test
    public void equalsIsWorking() {
        ObjectAssert.equalsIsWorking(a1, a2, b);
    }

    @Test
    public void hashcodeIsWorking() {
        ObjectAssert.hashcodeIsWorking(a1, a2, b);
    }

    @Test
    public void toStringIsWorking() {
        assertThat(a1.toString().contains("modelVersion")).isTrue();
    }

    @Test
    public void canHashAState() throws ParseException {
        fixTimeStamps(a1);

        String a1Hash = a1.hashState();
        assertThat(a1Hash.length()).isEqualTo(80);
        assertThat(a1Hash).isEqualTo("[#CZ-RQZX;TDnGiB^jK(`Q+n3h\\enrMKKsX-C!4:Ll8$c\"hEK+rRmW)DjDh?prX;6Hn0UL8LV=S*;0L!");
    }

    @Test
    public void hashDependsOnContent() throws ParseException {
        fixTimeStamps(a1);
        fixTimeStamps(a2);
        fixTimeStamps(b);

        String a1Hash = a1.hashState();
        String a2Hash = a2.hashState();
        String bHash = b.hashState();

        assertThat(a1Hash).isEqualTo(a2Hash);

        assertThat(a1Hash).isNotEqualTo(bHash);
    }

    @Test
    public void aStateCanBeCloned() {
        State s = a1.clone();
        assertThat(s.hashState()).isEqualTo(a1.hashState());

        s.getModificationCounts().setAdded(1);
        State clone = s.clone();
        assertThat(clone.getModificationCounts().getAdded()).isEqualTo(1);
    }

    @Test
    public void canFilterFilesInside() {
        State s = a1.addFiles("dir_1/file_1", "dir_1/file_2", "dir_2/file_1", "dir_2/file_2");

        State filteredState = s.filterDirectory(Paths.get("."), Paths.get("dir_1"), true);

        assertThat(toFileNames(filteredState.getFileStates())).isEqualTo(Arrays.asList("dir_1/file_1", "dir_1/file_2"));
    }

    @Test
    public void canFilterFilesOutside() {
        State s = a1.addFiles("dir_1/file_1", "dir_1/file_2", "dir_2/file_1", "dir_2/file_2");

        State filteredState = s.filterDirectory(Paths.get("."), Paths.get("dir_1"), false);

        assertThat(toFileNames(filteredState.getFileStates())).isEqualTo(Arrays.asList("dir_2/file_1", "dir_2/file_2", "file_1", "file_2"));
    }

    private void fixTimeStamps(BuildableState s) {
        // Fix the timeStamps in order that state hash can be verified

        // SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        // Date date = sdf.parse(MODIFICATION_TIME);
        // long timestamp = date.getTime();
        long timestamp = MODIFICATION_TIMESTAMP;

        s.setTimestamp(timestamp);
        for (FileState fileState : s.getFileStates()) {
            fileState.getFileTime().reset(timestamp);
        }
    }
}
