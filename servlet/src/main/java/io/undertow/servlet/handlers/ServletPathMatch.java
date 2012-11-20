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

import io.undertow.util.AttachmentKey;

/**
 * @author Stuart Douglas
 */
public class ServletPathMatch {

    public static final AttachmentKey<ServletPathMatch> ATTACHMENT_KEY = AttachmentKey.create(ServletPathMatch.class);

    private final ServletInitialHandler handler;
    private final String matched;
    private final String remaining;

    public ServletPathMatch(final ServletInitialHandler handler, final String matched, final String remaining) {
        this.handler = handler;
        this.matched = matched;
        if(remaining == null || remaining.equals("")) {
            this.remaining = null;
        } else {
            this.remaining = remaining;
        }
    }

    public ServletInitialHandler getHandler() {
        return handler;
    }

    public String getMatched() {
        return matched;
    }

    public String getRemaining() {
        return remaining;
    }
}
