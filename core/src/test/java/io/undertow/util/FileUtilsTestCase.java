/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025 Red Hat, Inc., and individual contributors
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

import io.undertow.testutils.category.UnitTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author Park Jaeon
 */
@Category(UnitTest.class)
public class FileUtilsTestCase {

    @Test
    public void testMultiByteCharactersAtBufferBoundary() {
        StringBuilder sb = new StringBuilder();

        // Create content larger than 1024 bytes (the old buffer size)
        // Fill with ASCII 'a' characters up to position 1023
        for (int i = 0; i < 1023; i++) {
            sb.append('a');
        }

        // Add a 3-byte UTF-8 character (Chinese character) at position 1023-1025
        // This would span across the 1024-byte boundary in the old implementation
        sb.append('世');  // 3-byte UTF-8 character

        // Add more content to ensure we're reading beyond the first buffer
        for (int i = 0; i < 2000; i++) {
            sb.append('b');
        }

        // Add some more multi-byte characters
        sb.append(" Hello 世界 Testing 🎉");

        String expected = sb.toString();
        InputStream stream = new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8));

        String result = FileUtils.readFile(stream);

        // The bug would cause replacement character (�) to appear instead of the correct character
        Assert.assertFalse("Result should not contain replacement character (�)",
                          result.contains("\uFFFD"));
        Assert.assertEquals("Content should be read correctly without corruption",
                          expected, result);
    }

    @Test
    public void testEmojisAtBufferBoundary() {
        StringBuilder sb = new StringBuilder();

        // Fill up to just before 1024 bytes
        for (int i = 0; i < 1022; i++) {
            sb.append('x');
        }

        // Add 4-byte emoji that would span the boundary
        sb.append("🎉");  // 4-byte UTF-8 character

        // Add more content
        for (int i = 0; i < 500; i++) {
            sb.append('y');
        }

        String expected = sb.toString();
        InputStream stream = new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8));

        String result = FileUtils.readFile(stream);

        Assert.assertFalse("Result should not contain replacement character",
                          result.contains("\uFFFD"));
        Assert.assertEquals("Emoji should be preserved correctly", expected, result);
    }

    @Test
    public void testLargeContentWithMultiByteCharacters() {
        StringBuilder sb = new StringBuilder();

        // Create content that's definitely larger than 1024 bytes and includes
        // various multi-byte characters throughout
        String testPattern = "Hello 世界! Testing 🎉 multi-byte encoding. ";

        // Repeat pattern to create large content (each pattern is ~50 bytes)
        for (int i = 0; i < 100; i++) {
            sb.append(testPattern);
            sb.append(i).append(" ");
        }

        String expected = sb.toString();
        Assert.assertTrue("Content should be larger than 1024 bytes",
                         expected.getBytes(StandardCharsets.UTF_8).length > 1024);

        InputStream stream = new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8));
        String result = FileUtils.readFile(stream);

        Assert.assertEquals("Large content with multi-byte characters should be read correctly",
                          expected, result);
        Assert.assertFalse("No replacement characters should be present",
                          result.contains("\uFFFD"));
    }
}
