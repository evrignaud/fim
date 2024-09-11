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

import org.apache.commons.codec.DecoderException;

/**
 * Methods for Ascii85 (aka Base85) encoding and decoding. Data to be encoded
 * must be a byte array whose length is a multiple of 4. The encoded Ascii85
 * data will consist of printable ASCII characters, and will have a length 25%
 * longer than the original data.
 * <p>
 * Ascii85 encode/decode algorithms adapted from https://pdfbox.apache.org/
 * org.apache.pdfbox.io package. I've done a significant amount of refactoring
 * to make the code more readable, but it could use more.
 */
public class Ascii85Encoder {
    private static final int MAX_ENCODED_CHAR_VALUE_OFFSET = 93;

    private static final char MIN_ENCODED_CHAR_VALUE = '!';

    private static final char ALL_ZERO_GROUP_INDICATOR = 'z';

    private static final char FINAL_GROUP_PADDING_CHAR = 'u';

    /**
     * Ascii85-encode the given (binary) data. Data to be encoded must be a byte
     * array whose length is a multiple of 4. Ascii85 encoding produces five
     * ASCII printable characters from each chunk of four bytes of binary data.
     *
     * @param bytesToBeEncoded
     * @return encoded data
     */
    public static byte[] encode(byte[] bytesToBeEncoded) {

        if (bytesToBeEncoded.length % 4 != 0) {
            throw new IllegalArgumentException("The byte array to encode must have a length that is divisible by 4");
        }

        // Will produce 5 ascii bytes per 4 binary bytes of input
        // This is why length of input must be a multiple of 4
        byte[] outputBuffer = new byte[(int) (bytesToBeEncoded.length * (5.0 / 4.0))];
        int outputBufferIndex = 0;

        byte[] inputChunk = new byte[4];
        byte[] outputChunk = new byte[5];

        int indataIndex = 0;

        // for each byte in input buffer
        for (int i = 0; i < bytesToBeEncoded.length; i++) {

            // copy next byte from input to inputChunk buffer
            inputChunk[indataIndex++] = bytesToBeEncoded[i];

            // if we have filled inputChunk buffer, encode its contents into
            // outputChunk
            if (indataIndex == 4) {
                encodeChunk(inputChunk, outputChunk);

                // copy encoded bytes to output buffer
                for (int outdataIndex = 0; outdataIndex < 5; outdataIndex++) {
                    if (outputChunk[outdataIndex] == 0) {
                        break;
                    }
                    outputBuffer[outputBufferIndex++] = outputChunk[outdataIndex];
                }
                indataIndex = 0; // reset indata index
            }
        }

        return outputBuffer;
    }

    /**
     * This will transform the given four ascii bytes to Ascii85 encoding (5
     * ascii chars). This method is copied nearly as-is from the
     * transformASCII85 method in apache pdfbox.
     */
    private static void encodeChunk(byte[] inputChunk, byte[] outputChunk) {
        long word = ((((inputChunk[0] << 8) | (inputChunk[1] & 0xFF)) << 16) | ((inputChunk[2] & 0xFF) << 8) | (inputChunk[3] & 0xFF)) & 0xFFFFFFFFL;

        if (word == 0) {
            outputChunk[0] = (byte) ALL_ZERO_GROUP_INDICATOR;
            outputChunk[1] = 0;
            return;
        }
        long x;
        x = word / (85L * 85L * 85L * 85L);
        outputChunk[0] = (byte) (x + MIN_ENCODED_CHAR_VALUE);
        word -= x * 85L * 85L * 85L * 85L;

        x = word / (85L * 85L * 85L);
        outputChunk[1] = (byte) (x + MIN_ENCODED_CHAR_VALUE);
        word -= x * 85L * 85L * 85L;

        x = word / (85L * 85L);
        outputChunk[2] = (byte) (x + MIN_ENCODED_CHAR_VALUE);
        word -= x * 85L * 85L;

        x = word / 85L;
        outputChunk[3] = (byte) (x + MIN_ENCODED_CHAR_VALUE);

        outputChunk[4] = (byte) ((word % 85L) + MIN_ENCODED_CHAR_VALUE);
    }

    /**
     * Decode the given Ascii85-encoded data.
     *
     * @param bytesToBeDecoded
     * @return decoded data
     * @throws DecoderException
     */
    public static byte[] decode(byte[] bytesToBeDecoded) throws DecoderException {
        byte[] chunkOfFiveCharsToDecode = new byte[5]; // Chunk of 5 chars to
        // decode (into 4 binary
        // bytes)
        byte[] chunkOfFourDecodedBytes = new byte[4]; // Chunk of 4 decoded
        // bytes

        if (bytesToBeDecoded.length % 5 != 0) {
            throw new IllegalArgumentException("The byte array to decode must have a length that is divisible by 5");
        }
        byte[] outputBuffer = new byte[(int) (bytesToBeDecoded.length * 4.0 / 5.0)];
        int outputBufferIndex = 0;

        for (int bytesToBeDecodedIndex = 0; bytesToBeDecodedIndex < bytesToBeDecoded.length; ) {
            int n = 4;
            byte byteToBeDecoded = bytesToBeDecoded[bytesToBeDecodedIndex++];
            if (byteToBeDecoded == ALL_ZERO_GROUP_INDICATOR) {
                chunkOfFourDecodedBytes[0] = chunkOfFourDecodedBytes[1] = chunkOfFourDecodedBytes[2] = chunkOfFourDecodedBytes[3] = 0;
                n = 4;
            } else {
                chunkOfFiveCharsToDecode[0] = byteToBeDecoded; // may be EOF
                // here....
                int chunkOfFiveCharsToDecodeIndex;
                for (chunkOfFiveCharsToDecodeIndex = 1; chunkOfFiveCharsToDecodeIndex < 5; ++chunkOfFiveCharsToDecodeIndex) {
                    byteToBeDecoded = bytesToBeDecoded[bytesToBeDecodedIndex++];
                    chunkOfFiveCharsToDecode[chunkOfFiveCharsToDecodeIndex] = byteToBeDecoded;

                }
                n = chunkOfFiveCharsToDecodeIndex - 1;
                if (n == 0) {
                    chunkOfFiveCharsToDecode = null;
                    chunkOfFourDecodedBytes = null;
                    break;
                }
                if (chunkOfFiveCharsToDecodeIndex < 5) {
                    for (++chunkOfFiveCharsToDecodeIndex; chunkOfFiveCharsToDecodeIndex < 5; ++chunkOfFiveCharsToDecodeIndex) {
                        // use FINAL_GROUP_PADDING_CHAR for padding
                        chunkOfFiveCharsToDecode[chunkOfFiveCharsToDecodeIndex] = (byte) FINAL_GROUP_PADDING_CHAR;
                    }
                }
                // decode stream
                long decodedByteValue = 0;
                for (chunkOfFiveCharsToDecodeIndex = 0; chunkOfFiveCharsToDecodeIndex < 5; ++chunkOfFiveCharsToDecodeIndex) {
                    byte offsetOfByteToBeDecoded = (byte) (chunkOfFiveCharsToDecode[chunkOfFiveCharsToDecodeIndex] - MIN_ENCODED_CHAR_VALUE);
                    if (offsetOfByteToBeDecoded < 0 || offsetOfByteToBeDecoded > MAX_ENCODED_CHAR_VALUE_OFFSET) {
                        throw new IllegalArgumentException("Invalid data in Ascii85 stream");
                    }
                    decodedByteValue = (decodedByteValue * 85L) + offsetOfByteToBeDecoded;
                }
                for (chunkOfFiveCharsToDecodeIndex = 3; chunkOfFiveCharsToDecodeIndex >= 0; --chunkOfFiveCharsToDecodeIndex) {
                    chunkOfFourDecodedBytes[chunkOfFiveCharsToDecodeIndex] = (byte) (decodedByteValue & 0xFFL);
                    decodedByteValue >>>= 8;
                }
                for (int i = 0; i < 4; i++) {
                    outputBuffer[outputBufferIndex++] = chunkOfFourDecodedBytes[i];
                }
            }
        }
        return outputBuffer;
    }
}
