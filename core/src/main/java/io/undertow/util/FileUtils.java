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

package io.undertow.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

/**
 * @author Stuart Douglas
 */
public class FileUtils {

    private FileUtils() {

    }

    public static String readFile(Class<?> testClass, String fileName) {
        final URL res = testClass.getResource(fileName);
        return readFile(res);
    }

    public static String readFile(URL url) {
        try {
            return readFile(url.openStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String readFile(final File file) {
        try {
            return readFile(new FileInputStream(file));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String readFile(InputStream file) {
        BufferedInputStream stream = null;
        try {
            stream = new BufferedInputStream(file);
            byte[] buff = new byte[1024];
            StringBuilder builder = new StringBuilder();
            int read = -1;
            while ((read = stream.read(buff)) != -1) {
                builder.append(new String(buff, 0, read));
            }
            return builder.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    //ignore
                }
            }
        }
    }


    public static File getFileOrCheckParentsIfNotFound(String baseStr, String path) throws FileNotFoundException {
        //File f = new File( System.getProperty("jbossas.project.dir", "../../..") );
        File base = new File(baseStr);
        if (!base.exists()) {
            throw new FileNotFoundException("Base path not found: " + base.getPath());
        }
        base = base.getAbsoluteFile();

        File f = new File(base, path);
        if (f.exists())
            return f;

        File fLast = f;
        while (!f.exists()) {
            int slash = path.lastIndexOf(File.separatorChar);
            if (slash <= 0)  // no slash or "/xxx"
                throw new FileNotFoundException("Path not found: " + f.getPath());
            path = path.substring(0, slash);
            fLast = f;
            f = new File(base, path);
        }
        // When first existing is found, report the last non-existent.
        throw new FileNotFoundException("Path not found: " + fLast.getPath());
    }


    public static void copyFile(final File src, final File dest) throws IOException {
        final InputStream in = new BufferedInputStream(new FileInputStream(src));
        try {
            copyFile(in, dest);
        } finally {
            close(in);
        }
    }

    public static void copyFile(final InputStream in, final File dest) throws IOException {
        dest.getParentFile().mkdirs();
        final OutputStream out = new BufferedOutputStream(new FileOutputStream(dest));
        try {
            int i = in.read();
            while (i != -1) {
                out.write(i);
                i = in.read();
            }
        } finally {
            close(out);
        }
    }


    public static void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignore) {
        }
    }

    public static void deleteRecursive(final File file) {
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                deleteRecursive(f);
            }
        }
        file.delete();
    }

}
