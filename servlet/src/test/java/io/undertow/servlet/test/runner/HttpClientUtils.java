/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.servlet.test.runner;

import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpResponse;

/**
 * @author Stuart Douglas
 */
public class HttpClientUtils {

    private HttpClientUtils() {

    }

    public static String readResponse(final HttpResponse response) throws IOException {
        return readResponse(response.getEntity().getContent());
    }

    public static String readResponse(InputStream stream) throws IOException {
        final StringBuilder builder = new StringBuilder();
        byte[] data = new byte[100];
        int read;
        while ((read = stream.read(data)) != -1) {
            builder.append(new String(data,0,read));
        }
        return builder.toString();
    }
}
