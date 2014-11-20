/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.util;

import io.undertow.UndertowLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a byte range for a range request
 *
 *
 * @author Stuart Douglas
 */
public class ByteRange {

    private final List<Range> ranges;

    public ByteRange(List<Range> ranges) {
        this.ranges = ranges;
    }

    public int getRanges() {
        return ranges.size();
    }

    /**
     * Gets the start of the specified range segment, of -1 if this is a suffix range segment
     * @param range The range segment to get
     * @return The range start
     */
    public long getStart(int range) {
        return ranges.get(range).getStart();
    }

    /**
     * Gets the end of the specified range segment, or the number of bytes if this is a suffix range segment
     * @param range The range segment to get
     * @return The range end
     */
    public long getEnd(int range) {
        return ranges.get(range).getEnd();
    }

    /**
     * Attempts to parse a range request. If the range request is invalid it will just return null so that
     * it may be ignored.
     *
     *
     * @param rangeHeader The range spec
     * @return A range spec, or null if the range header could not be parsed
     */
    public static ByteRange parse(String rangeHeader) {
        if(rangeHeader == null || rangeHeader.length() < 7) {
            return null;
        }
        if(!rangeHeader.startsWith("bytes=")) {
            return null;
        }
        List<Range> ranges = new ArrayList<>();
        String[] parts = rangeHeader.substring(6).split(",");
        for(String part : parts) {
            try {
                int index = part.indexOf('-');
                if (index == 0) {
                    //suffix range spec
                    //represents the last N bytes
                    //internally we represent this using a -1 as the start position
                    long val = Long.parseLong(part.substring(1));
                    if(val < 0) {
                        UndertowLogger.REQUEST_LOGGER.debugf("Invalid range spec %s", rangeHeader);
                        return null;
                    }
                    ranges.add(new Range(-1, val));
                } else {
                    if(index == -1) {
                        UndertowLogger.REQUEST_LOGGER.debugf("Invalid range spec %s", rangeHeader);
                        return null;
                    }
                    long start = Long.parseLong(part.substring(0, index));
                    if(start < 0) {
                        UndertowLogger.REQUEST_LOGGER.debugf("Invalid range spec %s", rangeHeader);
                        return null;
                    }
                    long end;
                    if (index + 1 < part.length()) {
                        end = Long.parseLong(part.substring(index + 1));
                        if(end < start) {
                            UndertowLogger.REQUEST_LOGGER.debugf("Invalid range spec %s", rangeHeader);
                            return null;
                        }
                    } else {
                        end = -1;
                    }
                    ranges.add(new Range(start, end));
                }
            } catch (NumberFormatException e) {
                UndertowLogger.REQUEST_LOGGER.debugf("Invalid range spec %s", rangeHeader);
                return null;
            }
        }
        if(ranges.isEmpty()) {
            return null;
        }
        return new ByteRange(ranges);
    }

    public static class Range {
        private final long start, end;

        public Range(long start, long end) {
            this.start = start;
            this.end = end;
        }

        public long getStart() {
            return start;
        }

        public long getEnd() {
            return end;
        }
    }
}
