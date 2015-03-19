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

package io.undertow.protocols.http2;

import java.nio.ByteBuffer;
import org.junit.Assert;
import org.junit.Test;

import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;

/**
 * HPACK unit test case, based on examples from the spec
 *
 * @author Stuart Douglas
 */
public class HpackSpecExamplesUnitTestCase {

    @Test
    public void testExample_D_2_1() throws HpackException {
        //custom-key: custom-header
        byte[] data = {
                0x40, 0x0a, 0x63, 0x75, 0x73, 0x74, 0x6f, 0x6d, 0x2d,
                0x6b, 0x65, 0x79, 0x0d, 0x63, 0x75, 0x73,
                0x74, 0x6f, 0x6d, 0x2d, 0x68, 0x65, 0x61, 0x64, 0x65, 0x72};
        HpackDecoder decoder = new HpackDecoder(Hpack.DEFAULT_TABLE_SIZE);
        HeaderMapEmitter emitter = new HeaderMapEmitter();
        decoder.setHeaderEmitter(emitter);
        decoder.decode(ByteBuffer.wrap(data));
        Assert.assertEquals(1, emitter.map.size());
        Assert.assertEquals("custom-header", emitter.map.getFirst(new HttpString("custom-key")));
        Assert.assertEquals(1, decoder.getFilledTableSlots());
        Assert.assertEquals(55, decoder.getCurrentMemorySize());
        assertTableState(decoder, 1, "custom-key", "custom-header");
    }

    @Test
    public void testExample_D_2_2() throws HpackException {
        //:path: /sample/path
        byte[] data = {0x04, 0x0c, 0x2f, 0x73, 0x61, 0x6d, 0x70, 0x6c, 0x65, 0x2f, 0x70, 0x61, 0x74, 0x68};
        HpackDecoder decoder = new HpackDecoder(Hpack.DEFAULT_TABLE_SIZE);
        HeaderMapEmitter emitter = new HeaderMapEmitter();
        decoder.setHeaderEmitter(emitter);
        decoder.decode(ByteBuffer.wrap(data));
        Assert.assertEquals(1, emitter.map.size());
        Assert.assertEquals("/sample/path", emitter.map.getFirst(new HttpString(":path")));
        Assert.assertEquals(0, decoder.getFilledTableSlots());
        Assert.assertEquals(0, decoder.getCurrentMemorySize());
    }

    @Test
    public void testExample_D_2_3() throws HpackException {
        //password: secret
        byte[] data = {0x10, 0x08, 0x70, 0x61, 0x73, 0x73, 0x77, 0x6f, 0x72, 0x64, 0x06, 0x73, 0x65, 0x63, 0x72, 0x65, 0x74};
        HpackDecoder decoder = new HpackDecoder(Hpack.DEFAULT_TABLE_SIZE);
        HeaderMapEmitter emitter = new HeaderMapEmitter();
        decoder.setHeaderEmitter(emitter);
        decoder.decode(ByteBuffer.wrap(data));
        Assert.assertEquals(1, emitter.map.size());
        Assert.assertEquals("secret", emitter.map.getFirst(new HttpString("password")));
        Assert.assertEquals(0, decoder.getFilledTableSlots());
        Assert.assertEquals(0, decoder.getCurrentMemorySize());
    }

    @Test
    public void testExample_D_2_4() throws HpackException {
        //:method: GET
        byte[] data = {(byte) 0x82};
        HpackDecoder decoder = new HpackDecoder(Hpack.DEFAULT_TABLE_SIZE);
        HeaderMapEmitter emitter = new HeaderMapEmitter();
        decoder.setHeaderEmitter(emitter);
        decoder.decode(ByteBuffer.wrap(data));
        Assert.assertEquals(1, emitter.map.size());
        Assert.assertEquals("GET", emitter.map.getFirst(new HttpString(":method")));
        Assert.assertEquals(0, decoder.getFilledTableSlots());
        Assert.assertEquals(0, decoder.getCurrentMemorySize());
    }

    @Test
    public void testExample_D_3() throws HpackException {
        //d 3.1
        byte[] data = {(byte) 0x82, (byte) 0x86, (byte) 0x84, 0x41, 0x0f, 0x77, 0x77, 0x77, 0x2e, 0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65, 0x2e, 0x63, 0x6f, 0x6d};
        HpackDecoder decoder = new HpackDecoder(Hpack.DEFAULT_TABLE_SIZE);
        HeaderMapEmitter emitter = new HeaderMapEmitter();
        decoder.setHeaderEmitter(emitter);
        decoder.decode(ByteBuffer.wrap(data));
        Assert.assertEquals(4, emitter.map.size());
        Assert.assertEquals("GET", emitter.map.getFirst(new HttpString(":method")));
        Assert.assertEquals("http", emitter.map.getFirst(new HttpString(":scheme")));
        Assert.assertEquals("/", emitter.map.getFirst(new HttpString(":path")));
        Assert.assertEquals("www.example.com", emitter.map.getFirst(new HttpString(":authority")));
        Assert.assertEquals(1, decoder.getFilledTableSlots());
        Assert.assertEquals(57, decoder.getCurrentMemorySize());
        assertTableState(decoder, 1, ":authority", "www.example.com");

        //d 3.2
        data = new byte[]{(byte) 0x82, (byte) 0x86, (byte) 0x84, (byte) 0xbe, 0x58, 0x08, 0x6e, 0x6f, 0x2d, 0x63, 0x61, 0x63, 0x68, 0x65};
        emitter = new HeaderMapEmitter();
        decoder.setHeaderEmitter(emitter);
        decoder.decode(ByteBuffer.wrap(data));
        Assert.assertEquals(5, emitter.map.size());
        Assert.assertEquals("GET", emitter.map.getFirst(new HttpString(":method")));
        Assert.assertEquals("http", emitter.map.getFirst(new HttpString(":scheme")));
        Assert.assertEquals("/", emitter.map.getFirst(new HttpString(":path")));
        Assert.assertEquals("www.example.com", emitter.map.getFirst(new HttpString(":authority")));
        Assert.assertEquals("no-cache", emitter.map.getFirst(new HttpString("cache-control")));
        Assert.assertEquals(2, decoder.getFilledTableSlots());
        Assert.assertEquals(110, decoder.getCurrentMemorySize());
        assertTableState(decoder, 1, "cache-control", "no-cache");
        assertTableState(decoder, 2, ":authority", "www.example.com");

        //d 3.3
        data = new byte[]{(byte) 0x82, (byte) 0x87, (byte) 0x85, (byte) 0xbf, 0x40, 0x0a, 0x63, 0x75, 0x73, 0x74, 0x6f, 0x6d, 0x2d, 0x6b, 0x65, 0x79, 0x0c, 0x63, 0x75, 0x73,
                0x74, 0x6f, 0x6d, 0x2d, 0x76, 0x61, 0x6c, 0x75, 0x65};
        emitter = new HeaderMapEmitter();
        decoder.setHeaderEmitter(emitter);
        decoder.decode(ByteBuffer.wrap(data));
        Assert.assertEquals(5, emitter.map.size());
        Assert.assertEquals("GET", emitter.map.getFirst(new HttpString(":method")));
        Assert.assertEquals("https", emitter.map.getFirst(new HttpString(":scheme")));
        Assert.assertEquals("/index.html", emitter.map.getFirst(new HttpString(":path")));
        Assert.assertEquals("www.example.com", emitter.map.getFirst(new HttpString(":authority")));
        Assert.assertEquals("custom-value", emitter.map.getFirst(new HttpString("custom-key")));
        Assert.assertEquals(3, decoder.getFilledTableSlots());
        Assert.assertEquals(164, decoder.getCurrentMemorySize());
        assertTableState(decoder, 1, "custom-key", "custom-value");
        assertTableState(decoder, 2, "cache-control", "no-cache");
        assertTableState(decoder, 3, ":authority", "www.example.com");

    }


    @Test
    public void testExample_D_4() throws HpackException {
        //d 4.1
        byte[] data = {(byte) 0x82, (byte) 0x86, (byte) 0x84, 0x41, (byte) 0x8c, (byte) 0xf1, (byte) 0xe3,
                (byte) 0xc2, (byte) 0xe5, (byte) 0xf2, 0x3a, 0x6b, (byte) 0xa0, (byte) 0xab, (byte) 0x90, (byte) 0xf4, (byte) 0xff};
        HpackDecoder decoder = new HpackDecoder(Hpack.DEFAULT_TABLE_SIZE);
        HeaderMapEmitter emitter = new HeaderMapEmitter();
        decoder.setHeaderEmitter(emitter);
        decoder.decode(ByteBuffer.wrap(data));
        Assert.assertEquals(4, emitter.map.size());
        Assert.assertEquals("GET", emitter.map.getFirst(new HttpString(":method")));
        Assert.assertEquals("http", emitter.map.getFirst(new HttpString(":scheme")));
        Assert.assertEquals("/", emitter.map.getFirst(new HttpString(":path")));
        Assert.assertEquals("www.example.com", emitter.map.getFirst(new HttpString(":authority")));
        Assert.assertEquals(1, decoder.getFilledTableSlots());
        Assert.assertEquals(57, decoder.getCurrentMemorySize());
        assertTableState(decoder, 1, ":authority", "www.example.com");


        //d 4.2
        data = new byte[]{(byte) 0x82, (byte) 0x86, (byte) 0x84, (byte) 0xbe, 0x58, (byte) 0x86, (byte) 0xa8, (byte) 0xeb, 0x10, 0x64, (byte) 0x9c, (byte) 0xbf};
        emitter = new HeaderMapEmitter();
        decoder.setHeaderEmitter(emitter);
        decoder.decode(ByteBuffer.wrap(data));
        Assert.assertEquals(5, emitter.map.size());
        Assert.assertEquals("GET", emitter.map.getFirst(new HttpString(":method")));
        Assert.assertEquals("http", emitter.map.getFirst(new HttpString(":scheme")));
        Assert.assertEquals("/", emitter.map.getFirst(new HttpString(":path")));
        Assert.assertEquals("www.example.com", emitter.map.getFirst(new HttpString(":authority")));
        Assert.assertEquals("no-cache", emitter.map.getFirst(new HttpString("cache-control")));
        Assert.assertEquals(2, decoder.getFilledTableSlots());
        Assert.assertEquals(110, decoder.getCurrentMemorySize());
        assertTableState(decoder, 1, "cache-control", "no-cache");
        assertTableState(decoder, 2, ":authority", "www.example.com");

        //d 4.3
        data = new byte[]{(byte) 0x82, (byte) 0x87, (byte) 0x85, (byte) 0xbf, 0x40, (byte) 0x88, 0x25, (byte) 0xa8, 0x49, (byte) 0xe9, 0x5b, (byte) 0xa9, 0x7d,
                0x7f, (byte) 0x89, 0x25, (byte) 0xa8, 0x49, (byte) 0xe9, 0x5b, (byte) 0xb8, (byte) 0xe8, (byte) 0xb4, (byte) 0xbf};
        emitter = new HeaderMapEmitter();
        decoder.setHeaderEmitter(emitter);
        decoder.decode(ByteBuffer.wrap(data));
        Assert.assertEquals(5, emitter.map.size());
        Assert.assertEquals("GET", emitter.map.getFirst(new HttpString(":method")));
        Assert.assertEquals("https", emitter.map.getFirst(new HttpString(":scheme")));
        Assert.assertEquals("/index.html", emitter.map.getFirst(new HttpString(":path")));
        Assert.assertEquals("www.example.com", emitter.map.getFirst(new HttpString(":authority")));
        Assert.assertEquals("custom-value", emitter.map.getFirst(new HttpString("custom-key")));
        Assert.assertEquals(3, decoder.getFilledTableSlots());
        Assert.assertEquals(164, decoder.getCurrentMemorySize());
        assertTableState(decoder, 1, "custom-key", "custom-value");
        assertTableState(decoder, 2, "cache-control", "no-cache");
        assertTableState(decoder, 3, ":authority", "www.example.com");
    }

    @Test
    public void testExample_D_5() throws HpackException {
        byte[] data = {0x48, 0x03, 0x33, 0x30, 0x32, 0x58, 0x07, 0x70, 0x72, 0x69, 0x76, 0x61, 0x74, 0x65, 0x61, 0x1d
                , 0x4d, 0x6f, 0x6e, 0x2c, 0x20, 0x32, 0x31, 0x20, 0x4f, 0x63, 0x74, 0x20, 0x32, 0x30, 0x31, 0x33
                , 0x20, 0x32, 0x30, 0x3a, 0x31, 0x33, 0x3a, 0x32, 0x31, 0x20, 0x47, 0x4d, 0x54, 0x6e, 0x17, 0x68
                , 0x74, 0x74, 0x70, 0x73, 0x3a, 0x2f, 0x2f, 0x77, 0x77, 0x77, 0x2e, 0x65, 0x78, 0x61, 0x6d, 0x70
                , 0x6c, 0x65, 0x2e, 0x63, 0x6f, 0x6d};
        HpackDecoder decoder = new HpackDecoder(256);

        //d 5.1
        HeaderMapEmitter emitter = new HeaderMapEmitter();
        decoder.setHeaderEmitter(emitter);
        decoder.decode(ByteBuffer.wrap(data));
        Assert.assertEquals(4, emitter.map.size());
        Assert.assertEquals("302", emitter.map.getFirst(new HttpString(":status")));
        Assert.assertEquals("private", emitter.map.getFirst(new HttpString("cache-control")));
        Assert.assertEquals("Mon, 21 Oct 2013 20:13:21 GMT", emitter.map.getFirst(new HttpString("date")));
        Assert.assertEquals("https://www.example.com", emitter.map.getFirst(new HttpString("location")));
        Assert.assertEquals(4, decoder.getFilledTableSlots());
        Assert.assertEquals(222, decoder.getCurrentMemorySize());
        assertTableState(decoder, 1, "location", "https://www.example.com");
        assertTableState(decoder, 2, "date", "Mon, 21 Oct 2013 20:13:21 GMT");
        assertTableState(decoder, 3, "cache-control", "private");
        assertTableState(decoder, 4, ":status", "302");

        //d 5.2
        data = new byte[]{(byte) 0x48, 0x03, 0x33, 0x30, 0x37, (byte) 0xc1, (byte) 0xc0, (byte) 0xbf};
        emitter = new HeaderMapEmitter();
        decoder.setHeaderEmitter(emitter);
        decoder.decode(ByteBuffer.wrap(data));
        Assert.assertEquals(4, emitter.map.size());
        Assert.assertEquals("307", emitter.map.getFirst(new HttpString(":status")));
        Assert.assertEquals("private", emitter.map.getFirst(new HttpString("cache-control")));
        Assert.assertEquals("Mon, 21 Oct 2013 20:13:21 GMT", emitter.map.getFirst(new HttpString("date")));
        Assert.assertEquals("https://www.example.com", emitter.map.getFirst(new HttpString("location")));
        Assert.assertEquals(4, decoder.getFilledTableSlots());
        Assert.assertEquals(222, decoder.getCurrentMemorySize());
        assertTableState(decoder, 1, ":status", "307");
        assertTableState(decoder, 2, "location", "https://www.example.com");
        assertTableState(decoder, 3, "date", "Mon, 21 Oct 2013 20:13:21 GMT");
        assertTableState(decoder, 4, "cache-control", "private");

        data = new byte[]{(byte) 0x88, (byte) 0xc1, 0x61, 0x1d, 0x4d, 0x6f, 0x6e, 0x2c, 0x20, 0x32, 0x31, 0x20, 0x4f, 0x63, 0x74, 0x20
                , 0x32, 0x30, 0x31, 0x33, 0x20, 0x32, 0x30, 0x3a, 0x31, 0x33, 0x3a, 0x32, 0x32, 0x20, 0x47, 0x4d
                , 0x54, (byte) 0xc0, 0x5a, 0x04, 0x67, 0x7a, 0x69, 0x70, 0x77, 0x38, 0x66, 0x6f, 0x6f, 0x3d, 0x41, 0x53
                , 0x44, 0x4a, 0x4b, 0x48, 0x51, 0x4b, 0x42, 0x5a, 0x58, 0x4f, 0x51, 0x57, 0x45, 0x4f, 0x50, 0x49
                , 0x55, 0x41, 0x58, 0x51, 0x57, 0x45, 0x4f, 0x49, 0x55, 0x3b, 0x20, 0x6d, 0x61, 0x78, 0x2d, 0x61
                , 0x67, 0x65, 0x3d, 0x33, 0x36, 0x30, 0x30, 0x3b, 0x20, 0x76, 0x65, 0x72, 0x73, 0x69, 0x6f, 0x6e
                , 0x3d, 0x31};
        emitter = new HeaderMapEmitter();
        decoder.setHeaderEmitter(emitter);
        decoder.decode(ByteBuffer.wrap(data));
        Assert.assertEquals(6, emitter.map.size());
        Assert.assertEquals("200", emitter.map.getFirst(new HttpString(":status")));
        Assert.assertEquals("private", emitter.map.getFirst(new HttpString("cache-control")));
        Assert.assertEquals("Mon, 21 Oct 2013 20:13:22 GMT", emitter.map.getFirst(new HttpString("date")));
        Assert.assertEquals("https://www.example.com", emitter.map.getFirst(new HttpString("location")));
        Assert.assertEquals("foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1", emitter.map.getFirst(new HttpString("set-cookie")));
        Assert.assertEquals("gzip", emitter.map.getFirst(new HttpString("content-encoding")));
        Assert.assertEquals(3, decoder.getFilledTableSlots());
        Assert.assertEquals(215, decoder.getCurrentMemorySize());
        assertTableState(decoder, 1, "set-cookie", "foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1");
        assertTableState(decoder, 2, "content-encoding", "gzip");
        assertTableState(decoder, 3, "date", "Mon, 21 Oct 2013 20:13:22 GMT");
    }


    @Test
    public void testExample_D_6() throws HpackException {
        byte[] data = {0x48, (byte) 0x82, 0x64, 0x02, 0x58, (byte) 0x85, (byte) 0xae, (byte) 0xc3, 0x77, 0x1a, 0x4b, 0x61, (byte) 0x96, (byte) 0xd0, 0x7a, (byte) 0xbe
                , (byte) 0x94, 0x10, 0x54, (byte) 0xd4, 0x44, (byte) 0xa8, 0x20, 0x05, (byte) 0x95, 0x04, 0x0b, (byte) 0x81, 0x66, (byte) 0xe0, (byte) 0x82, (byte) 0xa6
                , 0x2d, 0x1b, (byte) 0xff, 0x6e, (byte) 0x91, (byte) 0x9d, 0x29, (byte) 0xad, 0x17, 0x18, 0x63, (byte) 0xc7, (byte) 0x8f, 0x0b, (byte) 0x97, (byte) 0xc8
                , (byte) 0xe9, (byte) 0xae, (byte) 0x82, (byte) 0xae, 0x43, (byte) 0xd3
        };
        HpackDecoder decoder = new HpackDecoder(256);

        //d 5.1
        HeaderMapEmitter emitter = new HeaderMapEmitter();
        decoder.setHeaderEmitter(emitter);
        decoder.decode(ByteBuffer.wrap(data));
        Assert.assertEquals(4, emitter.map.size());
        Assert.assertEquals("302", emitter.map.getFirst(new HttpString(":status")));
        Assert.assertEquals("private", emitter.map.getFirst(new HttpString("cache-control")));
        Assert.assertEquals("Mon, 21 Oct 2013 20:13:21 GMT", emitter.map.getFirst(new HttpString("date")));
        Assert.assertEquals("https://www.example.com", emitter.map.getFirst(new HttpString("location")));
        Assert.assertEquals(4, decoder.getFilledTableSlots());
        Assert.assertEquals(222, decoder.getCurrentMemorySize());
        assertTableState(decoder, 1, "location", "https://www.example.com");
        assertTableState(decoder, 2, "date", "Mon, 21 Oct 2013 20:13:21 GMT");
        assertTableState(decoder, 3, "cache-control", "private");
        assertTableState(decoder, 4, ":status", "302");

        //d 5.2
        data = new byte[]{(byte) 0x48, (byte) 0x83, 0x64, 0x0e, (byte) 0xff, (byte) 0xc1, (byte) 0xc0, (byte) 0xbf};
        emitter = new HeaderMapEmitter();
        decoder.setHeaderEmitter(emitter);
        decoder.decode(ByteBuffer.wrap(data));
        Assert.assertEquals(4, emitter.map.size());
        Assert.assertEquals("307", emitter.map.getFirst(new HttpString(":status")));
        Assert.assertEquals("private", emitter.map.getFirst(new HttpString("cache-control")));
        Assert.assertEquals("Mon, 21 Oct 2013 20:13:21 GMT", emitter.map.getFirst(new HttpString("date")));
        Assert.assertEquals("https://www.example.com", emitter.map.getFirst(new HttpString("location")));
        Assert.assertEquals(4, decoder.getFilledTableSlots());
        Assert.assertEquals(222, decoder.getCurrentMemorySize());
        assertTableState(decoder, 1, ":status", "307");
        assertTableState(decoder, 2, "location", "https://www.example.com");
        assertTableState(decoder, 3, "date", "Mon, 21 Oct 2013 20:13:21 GMT");
        assertTableState(decoder, 4, "cache-control", "private");

        data = new byte[]{(byte) 0x88, (byte) 0xc1, 0x61, (byte) 0x96, (byte) 0xd0, 0x7a, (byte) 0xbe, (byte) 0x94, 0x10, 0x54, (byte) 0xd4, 0x44, (byte) 0xa8, 0x20, 0x05, (byte) 0x95
                , 0x04, 0x0b, (byte) 0x81, 0x66, (byte) 0xe0, (byte) 0x84, (byte) 0xa6, 0x2d, 0x1b, (byte) 0xff, (byte) 0xc0, 0x5a, (byte) 0x83, (byte) 0x9b, (byte) 0xd9, (byte) 0xab
                , 0x77, (byte) 0xad, (byte) 0x94, (byte) 0xe7, (byte) 0x82, 0x1d, (byte) 0xd7, (byte) 0xf2, (byte) 0xe6, (byte) 0xc7, (byte) 0xb3, 0x35, (byte) 0xdf, (byte) 0xdf, (byte) 0xcd, 0x5b
                , 0x39, 0x60, (byte) 0xd5, (byte) 0xaf, 0x27, 0x08, 0x7f, 0x36, 0x72, (byte) 0xc1, (byte) 0xab, 0x27, 0x0f, (byte) 0xb5, 0x29, 0x1f
                , (byte) 0x95, (byte) 0x87, 0x31, 0x60, 0x65, (byte) 0xc0, 0x03, (byte) 0xed, 0x4e, (byte) 0xe5, (byte) 0xb1, 0x06, 0x3d, 0x50, 0x07
        };
        emitter = new HeaderMapEmitter();
        decoder.setHeaderEmitter(emitter);
        decoder.decode(ByteBuffer.wrap(data));
        Assert.assertEquals(6, emitter.map.size());
        Assert.assertEquals("200", emitter.map.getFirst(new HttpString(":status")));
        Assert.assertEquals("private", emitter.map.getFirst(new HttpString("cache-control")));
        Assert.assertEquals("Mon, 21 Oct 2013 20:13:22 GMT", emitter.map.getFirst(new HttpString("date")));
        Assert.assertEquals("https://www.example.com", emitter.map.getFirst(new HttpString("location")));
        Assert.assertEquals("foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1", emitter.map.getFirst(new HttpString("set-cookie")));
        Assert.assertEquals("gzip", emitter.map.getFirst(new HttpString("content-encoding")));
        Assert.assertEquals(3, decoder.getFilledTableSlots());
        Assert.assertEquals(215, decoder.getCurrentMemorySize());
        assertTableState(decoder, 1, "set-cookie", "foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1");
        assertTableState(decoder, 2, "content-encoding", "gzip");
        assertTableState(decoder, 3, "date", "Mon, 21 Oct 2013 20:13:22 GMT");
    }

    private static void assertTableState(HpackDecoder decoder, int index, String name, String value) {
        int idx = decoder.getRealIndex(index);
        Hpack.HeaderField val = decoder.getHeaderTable()[idx];
        Assert.assertEquals(name, val.name.toString());
        Assert.assertEquals(value, val.value);
    }

    private static class HeaderMapEmitter implements HpackDecoder.HeaderEmitter {
        HeaderMap map = new HeaderMap();

        @Override
        public void emitHeader(HttpString name, String value, boolean neverIndex) {
            map.add(name, value);
        }
    }

}
