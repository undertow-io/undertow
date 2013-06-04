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

package io.undertow.servlet.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.api.ThreadSetupAction;

/**
 * @author Stuart Douglas
 */
public class CompositeThreadSetupAction implements ThreadSetupAction {

    private final List<ThreadSetupAction> actions;

    public CompositeThreadSetupAction(final List<ThreadSetupAction> actions) {
        this.actions = actions;
    }

    @Override
    public Handle setup(final HttpServerExchange exchange) {
        final Deque<Handle> handles = new ArrayDeque<Handle>(this.actions.size());
        try {
            for (ThreadSetupAction action : this.actions) {
                handles.addFirst(action.setup(exchange));
            }
            return new Handle() {
                @Override
                public void tearDown() {
                    Throwable problem = null;
                    for (Handle handle : handles) {
                        try {
                            handle.tearDown();
                        } catch (Throwable e) {
                            problem = e;
                        }
                    }
                    if (problem != null) {
                        throw new RuntimeException(problem);
                    }
                }
            };
        } catch (RuntimeException e) {
            for (final Handle handle : handles) {
                try {
                    handle.tearDown();
                } catch (Throwable ignore) {

                }
            }
            throw e;
        } catch (Error e) {
            for (final Handle handle : handles) {
                try {
                    handle.tearDown();
                } catch (Throwable ignore) {

                }
            }
            throw e;
        }
    }
}
