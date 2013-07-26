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

/**
 * @author Stuart Douglas
 */
public class ServletPathMatch extends ServletChain {

    private final String matched;
    private final String remaining;
    private final boolean defaultServletMatch;

    public ServletPathMatch(final ServletChain target, final String uri, boolean defaultServletMatch) {
        super(target);
        this.defaultServletMatch = defaultServletMatch;
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


    public String getMatched() {
        return matched;
    }

    public String getRemaining() {
        return remaining;
    }

    public boolean isDefaultServletMatch() {
        return defaultServletMatch;
    }
}
