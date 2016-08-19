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
import java.nio.charset.StandardCharsets;

/**
 * @author Stuart Douglas
 */
public class HttpClientUtils {

    private HttpClientUtils() {

    }

    public static String readResponse(final HttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        if(entity == null) {
            return "";
        }
        return readResponse(entity.getContent());
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
}
