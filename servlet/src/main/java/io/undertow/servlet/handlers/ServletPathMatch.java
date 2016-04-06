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

package io.undertow.servlet.handlers;

import javax.servlet.http.MappingMatch;

/**
 * @author Stuart Douglas
 */
public class ServletPathMatch {

    private final String matched;
    private final String remaining;
    private final boolean requiredWelcomeFileMatch;
    private final ServletChain servletChain;
    private final String rewriteLocation;
    private final Type type;

    public ServletPathMatch(final ServletChain target, final String uri, boolean requiredWelcomeFileMatch) {
        this.servletChain = target;
        this.requiredWelcomeFileMatch = requiredWelcomeFileMatch;
        this.type = Type.NORMAL;
        this.rewriteLocation = null;
        if (target.getServletPath() == null) {
            //the default servlet is always considered to have matched the full path.
            this.matched = uri;
            this.remaining = null;
        } else {
            this.matched = target.getServletPath();
            if(uri.length() == matched.length()) {
                remaining = null;
            } else {
                remaining = uri.substring(matched.length());
            }
        }
    }

    public ServletPathMatch(final ServletChain target, final String matched, final String remaining, final Type type, final String rewriteLocation) {
        this.servletChain = target;
        this.matched = matched;
        this.remaining = remaining;
        this.requiredWelcomeFileMatch = false;
        this.type = type;
        this.rewriteLocation = rewriteLocation;
    }

    public String getMatched() {
        return matched;
    }

    public String getRemaining() {
        return remaining;
    }

    public boolean isRequiredWelcomeFileMatch() {
        return requiredWelcomeFileMatch;
    }

    public ServletChain getServletChain() {
        return servletChain;
    }

    public String getRewriteLocation() {
        return rewriteLocation;
    }

    public Type getType() {
        return type;
    }

    public String getMatchString() {
        return servletChain.getPattern();
    }

    public MappingMatch getMappingMatch() {
        return servletChain.getMappingMatch();
    }

    public enum Type {
        /**
         * A normal servlet match, the invocation should proceed as normal
         */
        NORMAL,
        /**
         * A redirect is required, as the path does not end with a trailing slash
         */
        REDIRECT,
        /**
         * An internal rewrite is required, because the path matched a welcome file.
         * The provided match data is the match data after the rewrite.
         */
        REWRITE;
    }
}
