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

import io.undertow.server.HttpCompletionHandler;
import java.io.IOException;
import java.nio.channels.Channel;
import org.xnio.ChannelExceptionHandler;

/**
 * A channel listener that triggers an HTTP completion handler.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class CompletionChannelExceptionHandler implements ChannelExceptionHandler<Channel> {
    private final HttpCompletionHandler handler;

    /**
     * Construct a new instance.
     *
     * @param handler the completion handler to invoke
     */
    public CompletionChannelExceptionHandler(final HttpCompletionHandler handler) {
        this.handler = handler;
    }

    public void handleException(final Channel channel, final IOException exception) {
        handler.handleComplete();
    }
}
