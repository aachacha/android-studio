/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.ide.common.vectordrawable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility functions for parsing path information.
 * The implementation details should be the same as the PathParser in Android framework.
 */
public class PathParser {
    private static final float[] EMPTY_FLOAT_ARRAY = new float[0];

    private static class ExtractFloatResult {
        // We need to return the position of the next separator and whether the
        // next float starts with a '-' or a '.'.
        private int mEndPosition;
        private boolean mEndWithNegOrDot;
    }

    // Do not instantiate.
    private PathParser() {}

    /**
     * Calculates the position of the next comma or space or negative sign.
     *
     * @param s the string to search
     * @param start the position to start searching
     * @param result the result of the extraction, including the position of the
     * the starting position of next number, whether it is ending with a '-'.
     */
    private static void extract(String s, int start, ExtractFloatResult result) {
        // Now looking for ' ', ',', '.' or '-' from the start.
        int currentIndex = start;
        boolean foundSeparator = false;
        result.mEndWithNegOrDot = false;
        boolean secondDot = false;
        boolean isExponential = false;
        for (; currentIndex < s.length(); currentIndex++) {
            boolean isPrevExponential = isExponential;
            isExponential = false;
            char currentChar = s.charAt(currentIndex);
            switch (currentChar) {
                case ' ':
                case ',':
                    foundSeparator = true;
                    break;
                case '-':
                    // The negative sign following a 'e' or 'E' is not a separator.
                    if (currentIndex != start && !isPrevExponential) {
                        foundSeparator = true;
                        result.mEndWithNegOrDot = true;
                    }
                    break;
                case '.':
                    if (!secondDot) {
                        secondDot = true;
                    } else {
                        // This is the second dot, and it is considered as a separator.
                        foundSeparator = true;
                        result.mEndWithNegOrDot = true;
                    }
                    break;
                case 'e':
                case 'E':
                    isExponential = true;
                    break;
            }
            if (foundSeparator) {
                break;
            }
        }
        // When there is nothing found, then we put the end position to the end
        // of the string.
        result.mEndPosition = currentIndex;
    }

    /**
     * Parses the floats in the string this is an optimized version of parseFloat(s.split(",|\\s"));
     *
     * @param s the string containing a command and list of floats
     * @return array of floats
     */
    private static float[] getFloats(String s) {
        if (s.charAt(0) == 'z' || s.charAt(0) == 'Z') {
            return EMPTY_FLOAT_ARRAY;
        }
        try {
            float[] results = new float[s.length()];
            int count = 0;
            int startPosition = 1;
            int endPosition;

            ExtractFloatResult result = new ExtractFloatResult();
            int totalLength = s.length();

            // The startPosition should always be the first character of the
            // current number, and endPosition is the character after the current
            // number.
            while (startPosition < totalLength) {
                extract(s, startPosition, result);
                endPosition = result.mEndPosition;

                if (startPosition < endPosition) {
                    results[count++] = Float.parseFloat(s.substring(startPosition, endPosition));
                }

                if (result.mEndWithNegOrDot) {
                    // Keep the '-' or '.' sign with next number.
                    startPosition = endPosition;
                } else {
                    startPosition = endPosition + 1;
                }
            }
            return Arrays.copyOfRange(results, 0, count);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Error in parsing \"" + s + "\"", e);
        }
    }

    private static void addNode(List<VdPath.Node> list, char cmd, float[] val) {
        list.add(new VdPath.Node(cmd, val));
    }

    private static int nextStart(String s, int end) {
        while (end < s.length()) {
            char c = s.charAt(end);
            // Note that 'e' or 'E' are not valid path commands, but could be
            // used for floating point numbers' scientific notation.
            // Therefore, when searching for next command, we should ignore 'e'
            // and 'E'.
            if ((((c - 'A') * (c - 'Z') <= 0) || ((c - 'a') * (c - 'z') <= 0))
                    && c != 'e' && c != 'E') {
                return end;
            }
            end++;
        }
        return end;
    }

    public static VdPath.Node[] parsePath(String value) {
        value = value.trim();
        List<VdPath.Node> list = new ArrayList<>();

        int start = 0;
        int end = 1;
        while (end < value.length()) {
            end = nextStart(value, end);
            String s = value.substring(start, end);
            float[] val = getFloats(s);
            char currentCommand = s.charAt(0);

            if (start == 0) {
                // For the starting command, special handling:
                // add M 0 0 if there is none.
                // This is good for transformation.
                if (currentCommand != 'M' && currentCommand != 'm') {
                    addNode(list, 'M', new float[2]);
                }
            }
            addNode(list, currentCommand, val);

            start = end;
            end++;
        }
        if (end - start == 1 && start < value.length()) {
            addNode(list, value.charAt(start), EMPTY_FLOAT_ARRAY);
        }
        return list.toArray(new VdPath.Node[list.size()]);
    }
}
