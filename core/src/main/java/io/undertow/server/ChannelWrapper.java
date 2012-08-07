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

package io.undertow.server;

import java.nio.channels.Channel;

/**
 * Interface that provides a means of wrapping a {@link java.nio.channels.Channel}.  Every channel wrapper has a chance
 * to replace the channel with a channel which either wraps or replaces the passed in channel.  However it is the responsibility
 * of either the channel wrapper instance or the channel it creates to ensure that the original channel is eventually
 * cleaned up and shut down properly when the request is terminated.
 *
 * @author Stuart Douglas
 */
public interface ChannelWrapper<T extends Channel> {

    /**
     * Wrap the channel.  The wrapper should not return {@code null}.  If no wrapping is desired, the original
     * channel should be returned.
     *
     * @param channel the original channel
     * @param exchange the in-flight HTTP exchange
     * @return the replacement channel
     */
    T wrap(final T channel, final HttpServerExchange exchange);
}
