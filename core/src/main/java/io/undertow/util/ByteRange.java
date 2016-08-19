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
import java.util.Date;
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

    /**
     * Returns a representation of the range result. If this returns null then a 200 response should be sent instead
     * @param resourceContentLength
     * @return
     */
    public RangeResponseResult getResponseResult(final long resourceContentLength, String ifRange, Date lastModified, String eTag) {
        if(ranges.isEmpty()) {
            return null;
        }
        long start = getStart(0);
        long end = getEnd(0);
        long rangeLength;
        if(ifRange != null && !ifRange.isEmpty()) {
            if(ifRange.charAt(0) == '"') {
                //entity tag
                if(eTag != null && !eTag.equals(ifRange)) {
                    return null;
                }
            } else {
                Date ifDate = DateUtils.parseDate(ifRange);
                if(ifDate != null && lastModified != null && ifDate.getTime() < lastModified.getTime()) {
                    return null;
                }
            }
        }

        if(start == -1 ) {
            //suffix range
            long toWrite = end;
            if(toWrite >= 0) {
                rangeLength = toWrite;
            } else {
                //ignore the range request
                return new RangeResponseResult(0, 0, 0, "bytes */" + resourceContentLength, StatusCodes.REQUEST_RANGE_NOT_SATISFIABLE);
            }
            start = resourceContentLength - end;
            end = resourceContentLength - 1;
        } else if(end == -1) {
            //prefix range
            long toWrite = resourceContentLength - start;
            if(toWrite >= 0) {
                rangeLength = toWrite;
            } else {
                //ignore the range request
                return new RangeResponseResult(0, 0, 0, "bytes */" + resourceContentLength, StatusCodes.REQUEST_RANGE_NOT_SATISFIABLE);
            }
            end = resourceContentLength - 1;
        } else {
            if(start >= resourceContentLength || start > end) {
                return new RangeResponseResult(0, 0, 0, "bytes */" + resourceContentLength, StatusCodes.REQUEST_RANGE_NOT_SATISFIABLE);
            }
            long toWrite = end - start + 1;
            rangeLength = toWrite;
        }
        return new RangeResponseResult(start, end, rangeLength,  "bytes " + start + "-" + end + "/" + resourceContentLength, StatusCodes.PARTIAL_CONTENT);
    }

    public static class RangeResponseResult {
        private final long start;
        private final long end;
        private final long contentLength;
        private final String contentRange;
        private final int statusCode;

        public RangeResponseResult(long start, long end, long contentLength, String contentRange, int statusCode) {
            this.start = start;
            this.end = end;
            this.contentLength = contentLength;
            this.contentRange = contentRange;
            this.statusCode = statusCode;
        }

        public long getStart() {
            return start;
        }

        public long getEnd() {
            return end;
        }

        public long getContentLength() {
            return contentLength;
        }

        public String getContentRange() {
            return contentRange;
        }

        public int getStatusCode() {
            return statusCode;
        }
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
