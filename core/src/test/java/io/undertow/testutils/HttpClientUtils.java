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

package io.undertow.testutils;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @author Stuart Douglas
 */
public class HttpClientUtils {

    private HttpClientUtils() {

    }

    public static String readResponse(final HttpResponse response) throws IOException {
        return readResponse(response, StandardCharsets.UTF_8);
    }

    public static String readResponse(final HttpResponse response, final Charset charset) throws IOException {
        HttpEntity entity = response.getEntity();
        if(entity == null) {
            return "";
        }
        return readResponse(entity.getContent(), charset);
    }

    public static String readResponse(InputStream stream) throws IOException {

        byte[] data = new byte[100];
        int read;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while ((read = stream.read(data)) != -1) {
            out.write(data, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    public static String readResponse(final InputStream stream, final Charset charset) throws IOException {

        byte[] data = new byte[100];
        int read;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while ((read = stream.read(data)) != -1) {
            out.write(data, 0, read);
        }
        return new String(out.toByteArray(), charset);
    }

    public static byte[] readRawResponse(final HttpResponse response) throws IOException {
        return readRawResponse(response.getEntity().getContent());
    }

    public static byte[] readRawResponse(InputStream stream) throws IOException {
        final ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] data = new byte[100];
        int read;
        while ((read = stream.read(data)) != -1) {
            b.write(data, 0, read);
        }
        return b.toByteArray();
    }

    public static String toString(InputStream is, Charset charset) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            transfer(is, out);
            return new String(out.toByteArray(), charset);
        }
    }

    public static void transfer(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[8192];
        int length = is.read(buffer);
        while (length != -1) {
            os.write(buffer, 0, length);
            length = is.read(buffer);
        }
    }
}
