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

package io.undertow.servlet.core;

import java.util.List;

import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.api.ThreadSetupAction;

/**
 * @author Stuart Douglas
 */
public class CompositeThreadSetupAction implements ThreadSetupAction {

    private final ThreadSetupAction[] actions;

    public CompositeThreadSetupAction(final List<ThreadSetupAction> actions) {
        this.actions = actions.toArray(new ThreadSetupAction[actions.size()]);
    }

    @Override
    public Handle setup(final HttpServerExchange exchange) {
        final Handle[] handles = new Handle[actions.length];
        try {
            for (int i = 0; i < handles.length; ++i) {
                handles[handles.length - i - 1] = actions[i].setup(exchange); //add them in reverse order
            }
            return new Handle() {
                @Override
                public void tearDown() {
                    Throwable problem = null;
                    for (int i = 0; i < handles.length; ++i) {
                        Handle handle = handles[i];
                        if (handle != null) {
                            try {
                                handle.tearDown();
                            } catch (Throwable e) {
                                problem = e;
                            }
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
