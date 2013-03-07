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

package io.undertow.servlet.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.servlet.core.ManagedServlet;

/**
 * @author Stuart Douglas
 */
public class ServletPathMatch extends ServletChain {

    private final String matched;
    private final String remaining;

    public ServletPathMatch(final HttpHandler handler, final ManagedServlet managedServlet, final String matched, final String remaining) {
        super(handler, managedServlet);
        this.matched = matched;
        if (remaining == null || remaining.equals("")) {
            this.remaining = null;
        } else {
            this.remaining = remaining;
        }
    }


    public String getMatched() {
        return matched;
    }

    public String getRemaining() {
        return remaining;
    }
}
