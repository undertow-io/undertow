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

package io.undertow.servlet.api;

/**
 * @author Stuart Douglas
 */
public class CrawlerSessionManagerConfig {

    public static final String DEFAULT_CRAWLER_REGEX = ".*[bB]ot.*|.*Yahoo! Slurp.*|.*Feedfetcher-Google.*";

    private final String crawlerUserAgents;
    private final int sessionInactiveInterval;

    public CrawlerSessionManagerConfig() {
        this(60, DEFAULT_CRAWLER_REGEX);
    }

    public CrawlerSessionManagerConfig(int sessionInactiveInterval) {
        this(sessionInactiveInterval, DEFAULT_CRAWLER_REGEX);
    }

    public CrawlerSessionManagerConfig(final String crawlerUserAgents) {
        this(60, crawlerUserAgents);
    }

    public CrawlerSessionManagerConfig(int sessionInactiveInterval, String crawlerUserAgents) {
        this.sessionInactiveInterval = sessionInactiveInterval;
        this.crawlerUserAgents = crawlerUserAgents;
    }

    public String getCrawlerUserAgents() {
        return crawlerUserAgents;
    }

    public int getSessionInactiveInterval() {
        return sessionInactiveInterval;
    }
}
