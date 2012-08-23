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

package io.undertow.servlet.test.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.util.Set;

import io.undertow.servlet.api.ResourceLoader;
import org.xnio.FileAccess;
import org.xnio.Xnio;

/**
 * @author Stuart Douglas
 */
public class TestResourceLoader implements ResourceLoader {

    public static TestResourceLoader INSTANCE = new TestResourceLoader();

    @Override
    public URL getResource(final String resource) {
        return TestResourceLoader.class.getClassLoader().getResource(resource);
    }

    @Override
    public InputStream getResourceAsStream(final String resource) {
        return TestResourceLoader.class.getClassLoader().getResourceAsStream(resource);
    }

    @Override
    public FileChannel getResourceAsChannel(final String resource, final Xnio xnio) throws IOException {
        URL url  = TestResourceLoader.class.getClassLoader().getResource(resource);
        return xnio.openFile(url.getFile(), FileAccess.READ_ONLY);
    }

    @Override
    public Set<String> getResourcePaths(final String path) {
        return null;
    }
}
