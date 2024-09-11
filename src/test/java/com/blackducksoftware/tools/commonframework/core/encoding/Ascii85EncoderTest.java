/**
 * This code part comes for the following archived github project:
 * https://github.com/blackducksoftware/common-framework/tree/master
 * <p>
 * CommonFramework
 * <p>
 * Copyright (C) 2017 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 * <p>
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.blackducksoftware.tools.commonframework.core.encoding;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author sbillings
 */
public class Ascii85EncoderTest {
    private static final Random RANDOM = new Random(System.currentTimeMillis());

    @AfterAll
    public static void tearDownAfterClass() throws Exception {
    }

    @Test
    public void test() throws Exception {
        final int numTests = 10000; // 10K is a good number

        for (int i = 0; i < numTests; i++) {
            final byte[] bytes = generateRandomBytes(256);
            testWithBytes(bytes);
        }
    }

    private void testWithBytes(final byte[] origBytes) throws Exception {
        final byte[] encodedBytes = Ascii85Encoder.encode(origBytes);
        assertTrue(isAscii(encodedBytes));

        final String encodedString = new String(encodedBytes, StandardCharsets.UTF_8);
        final byte[] decodedBytes = Ascii85Encoder.decode(encodedString.getBytes());
        assertEquals(Arrays.toString(origBytes), Arrays.toString(decodedBytes));
    }

    public static byte[] generateRandomBytes(int length) {
        byte[] randomBytes = new byte[length];
        RANDOM.nextBytes(randomBytes);
        return randomBytes;
    }

    private boolean isAscii(final byte[] bytes) {
        final String stringToTest = new String(bytes, StandardCharsets.UTF_8);
        return StringUtils.isAsciiPrintable(stringToTest);
    }

}
