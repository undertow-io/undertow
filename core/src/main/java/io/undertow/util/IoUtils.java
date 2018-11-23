/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.util;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;

import io.undertow.UndertowLogger;

/**
 * General I/O utility methods.
 *
 * @apiviz.exclude
 */
public final class IoUtils {

    /**
     * Close a series of resources, logging errors if they occur.
     *
     * @param resources the resources to close
     */
    public static void safeClose(final Closeable... resources) {
        for (Closeable resource : resources) {
            safeClose(resource);
        }
    }

    /**
     * Close a resource, logging an error if an error occurs.
     *
     * @param resource the resource to close
     */
    public static void safeClose(final Closeable resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        } catch (ClosedChannelException ignored) {
        } catch (IOException t) {
            UndertowLogger.REQUEST_IO_LOGGER.ioException(t);
        } catch (Exception t) {
            UndertowLogger.REQUEST_IO_LOGGER.ioException(new IOException(t));
        }
    }


}
