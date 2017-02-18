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

package io.undertow.websockets.extensions;

import io.undertow.testutils.category.UnitTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.zip.Deflater;
import java.util.zip.Inflater;


/**
 * An auxiliar test class for compression/decompression operations implemented on extensions context.
 *
 * @author Lucas Ponce
 */
@Category(UnitTest.class)
public class CompressionUtilsTest {

    private static Inflater decompress;
    private static Deflater compress;
    private static byte[] buf = new byte[1024];

    @Before
    public void setup() throws Exception {
        compress = new Deflater(Deflater.BEST_SPEED, true);
        decompress = new Inflater(true);
    }

    @After
    public void finish() throws Exception {
        compress.end();
        decompress.end();
    }

    @Test
    public void testBasicCompressDecompress() throws Exception {
        String raw = "Hello";

        compress.setInput(raw.getBytes());
        compress.finish();
        int read = compress.deflate(buf, 0, buf.length, Deflater.SYNC_FLUSH);

        decompress.setInput(buf, 0, read);
        read = decompress.inflate(buf);

        Assert.assertEquals("Hello", new String(buf, 0, read, "UTF-8"));

        compress.reset();
        decompress.reset();

        raw = "Hello, World!";

        compress.setInput(raw.getBytes());
        compress.finish();
        read = compress.deflate(buf, 0, buf.length, Deflater.SYNC_FLUSH);

        decompress.setInput(buf, 0, read);
        read = decompress.inflate(buf);

        Assert.assertEquals("Hello, World!", new String(buf, 0, read, "UTF-8"));
    }

    @Test
    public void testCompressDecompressByFrames() throws Exception {
        String raw = "Hello, World! This is a long input example data with a lot of content for testing";

        /*
            This test shares same buffer, 0-511 for compress, 512-1023 for decompress
         */
        int position1 = 0;
        int position2 = 512;
        int chunkLength = 10;

        // Frame 1
        compress.setInput(raw.getBytes(), position1, chunkLength);

        int compressed = compress.deflate(buf, 0, 512, Deflater.SYNC_FLUSH);

        decompress.setInput(buf, 0, compressed);
        int decompressed = decompress.inflate(buf, position2, buf.length - position2);

        // Frame 2
        position1 += chunkLength;
        position2 += decompressed;
        compress.setInput(raw.getBytes(), position1, chunkLength);

        compressed = compress.deflate(buf, 0, 512, Deflater.NO_FLUSH);

        decompress.setInput(buf, 0, compressed);
        decompress.finished();
        decompressed = decompress.inflate(buf, position2, buf.length - position2);

        // Frame 3
        position1 += chunkLength;
        position2 += decompressed;
        compress.setInput(raw.getBytes(), position1, raw.getBytes().length - position1);
        compress.finish();
        compressed = compress.deflate(buf, 0, 512, Deflater.NO_FLUSH);

        decompress.setInput(buf, 0, compressed);
        decompressed = decompress.inflate(buf, position2, buf.length - position2);

        Assert.assertEquals(raw, new String(buf, 512, position2 + decompressed - 512, "UTF-8"));
    }

    @Test
    public void testCompressByFramesDecompressWhole() throws Exception {
        String raw = "Hello, World! This is a long input example data with a lot of content for testing";

        byte[] compressed = new byte[raw.length() + 64];
        byte[] decompressed = new byte[raw.length()];

        int n = 0, total = 0;

        // Compress Frame1
        compress.setInput(raw.getBytes(), 0, 10);
        n = compress.deflate(compressed, 0, compressed.length, Deflater.SYNC_FLUSH);
        total += n;

        // Compress Frame2
        compress.setInput(raw.getBytes(), 10, 10);
        n = compress.deflate(compressed, total, compressed.length - total, Deflater.SYNC_FLUSH);
        total += n;

        // Compress Frame3
        compress.setInput(raw.getBytes(), 20, raw.getBytes().length - 20);

        n = compress.deflate(compressed, total, compressed.length - total, Deflater.SYNC_FLUSH);
        total += n;

        // Uncompress
        decompress.setInput(compressed, 0, total);
        n = decompress.inflate(decompressed, 0, decompressed.length);

        Assert.assertEquals(raw, new String(decompressed, 0, n, "UTF-8"));
    }

    @Test
    public void testLongMessage() throws Exception {

        int LONG_MSG = 16384;
        StringBuilder longMsg = new StringBuilder(LONG_MSG);

        byte[] longbuf = new byte[LONG_MSG + 64];
        byte[] output = new byte[LONG_MSG];

        for (int i = 0; i < LONG_MSG; i++) {
            longMsg.append(new Integer(i).toString().charAt(0));
        }
        String msg = longMsg.toString();
        byte[] input = msg.getBytes();
        byte[] compressBuf = new byte[LONG_MSG + 64];
        byte[] decompressBuf = new byte[LONG_MSG];

        compress.setInput(input);
        compress.finish();
        int read = compress.deflate(compressBuf, 0, compressBuf.length, Deflater.SYNC_FLUSH);

        decompress.setInput(compressBuf, 0, read);
        read = decompress.inflate(decompressBuf);

        Assert.assertEquals(msg, new String(decompressBuf, 0, read, "UTF-8"));
    }

    @Test
    public void testCompressByFramesDecompressWholeLongMessage() throws Exception {
        int LONG_MSG = 75 * 1024;
        StringBuilder longMsg = new StringBuilder(LONG_MSG);

        byte[] longbuf = new byte[LONG_MSG + 64];
        byte[] output = new byte[LONG_MSG];

        for (int i = 0; i < LONG_MSG; i++) {
            longMsg.append(new Integer(i).toString().charAt(0));
        }
        String msg = longMsg.toString();
        byte[] input = msg.getBytes();

        /*
            Compress in chunks of 1024 bytes
         */
        boolean finished = false;
        int start = 0;
        int end;
        int compressed;
        int total = 0;
        while (!finished) {
            end = (start + 1024) < input.length ? 1024 : input.length - start;
            compress.setInput(input, start, end);

            start += 1024;
            finished = start >= input.length;

            if (finished) {
                compress.finish();
            }

            compressed = compress.deflate(longbuf, total, longbuf.length - total, Deflater.SYNC_FLUSH);
            total += compressed;
        }

        /*
            Decompress whole message
         */
        int decompressed = 0;
        decompress.setInput(longbuf, 0, total);
        decompress.finished();
        decompressed = decompress.inflate(output, 0, output.length);

        Assert.assertEquals(longMsg.toString(), new String(output, 0, decompressed, "UTF-8"));
    }

    @Test
    public void testEmptyFrames() throws Exception {
        decompress.reset();

        byte[] compressedFrame1 = { (byte)0xf2, (byte)0x48, (byte)0xcd };
        byte[] compressedFrame2 = { (byte)0xc9, (byte)0xc9, (byte)0x07, (byte)0x00 };
        byte[] compressedFrame3 = { (byte)0x00, (byte)0x00, (byte)0xff, (byte)0xff };

        byte[] output = new byte[1024];

        int decompressed = 0;
        decompress.setInput(compressedFrame1);
        decompressed = decompress.inflate(output, 0, output.length);
        Assert.assertEquals(2, decompressed);
        Assert.assertEquals("He", new String(output, 0, decompressed, "UTF-8"));

        decompress.setInput(compressedFrame2);
        decompressed = decompress.inflate(output, 0, output.length);
        Assert.assertEquals(3, decompressed);
        Assert.assertEquals("llo", new String(output, 0, decompressed, "UTF-8"));

        decompress.setInput(compressedFrame3);
        decompressed = decompress.inflate(output, 0, output.length);
        Assert.assertEquals(0, decompressed);

        decompress.setInput(compressedFrame1);
        decompressed = decompress.inflate(output, 0, output.length);
        Assert.assertEquals(2, decompressed);
        Assert.assertEquals("He", new String(output, 0, decompressed, "UTF-8"));

        decompress.setInput(compressedFrame2);
        decompressed = decompress.inflate(output, 0, output.length);
        Assert.assertEquals(3, decompressed);
        Assert.assertEquals("llo", new String(output, 0, decompressed, "UTF-8"));
    }

    @Test
    public void testPadding() throws Exception {
        String original = "This is a long message - This is a long message - This is a long message";
        byte[] compressed = new byte[1024];
        int nCompressed;

        compress.setInput(original.getBytes());
        nCompressed = compress.deflate(compressed, 0, compressed.length, Deflater.SYNC_FLUSH);

        /*
            Padding
         */
        byte[] padding = {0, 0, 0, (byte)0xff, (byte)0xff, 0, 0, 0, (byte)0xff, (byte)0xff, 0, 0, 0, (byte)0xff, (byte)0xff};
        int nPadding = padding.length;

        for (int i = 0; i < padding.length; i++) {
            compressed[nCompressed + i] = padding[i];
        }

        byte[] uncompressed = new byte[1024];
        int nUncompressed;

        decompress.setInput(compressed, 0, nCompressed + nPadding);
        nUncompressed = decompress.inflate(uncompressed);

        Assert.assertEquals(original, new String(uncompressed, 0, nUncompressed, "UTF-8"));
    }
}
