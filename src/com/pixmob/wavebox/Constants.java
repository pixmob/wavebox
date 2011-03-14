/*
 * Copyright (C) 2011 Alexandre Roman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pixmob.wavebox;

import java.io.UnsupportedEncodingException;

/**
 * Library constants.
 * @author Pixmob
 */
class Constants {
    /**
     * Log tag.
     */
    public static final String TAG = "Wavebox";
    
    /**
     * <code>RIFF</code> block identifier.
     */
    public static final byte[] RIFF = toBytes("RIFF");
    /**
     * <code>WAVE</code> block identifier.
     */
    public static final byte[] WAVE = toBytes("WAVE");
    /**
     * <code>fmt </code> block identifier.
     */
    public static final byte[] FMT = toBytes("fmt ");
    /**
     * <code>data</code> block identifier.
     */
    public static final byte[] DATA = toBytes("data");
    
    /**
     * Header length for a WAVE file.
     */
    public static final int HEADER_LEN = 44;
    
    private static byte[] toBytes(String s) {
        try {
            return s.getBytes("ASCII");
        } catch (UnsupportedEncodingException e) {
            // unlikely to happen
            throw new IllegalStateException("ASCII encoding is not available",
                    e);
        }
    }
}
