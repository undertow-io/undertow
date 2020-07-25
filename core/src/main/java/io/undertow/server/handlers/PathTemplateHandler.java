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
package io.undertow.server.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.PathTemplate;
import io.undertow.util.PathTemplateMatcher;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A handler that matches URI templates
 *
 * @author Stuart Douglas
 * @see PathTemplateMatcher
 */
public class PathTemplateHandler implements HttpHandler {

    private final boolean rewriteQueryParameters;

    private final HttpHandler next;

    /**
     * @see io.undertow.util.PathTemplateMatch#ATTACHMENT_KEY
     */
    @Deprecated
    public static final AttachmentKey<PathTemplateMatch> PATH_TEMPLATE_MATCH = AttachmentKey.create(PathTemplateMatch.class);

    private final PathTemplateMatcher<HttpHandler> pathTemplateMatcher = new PathTemplateMatcher<>();

    public PathTemplateHandler() {
        this(true);
    }

    public PathTemplateHandler(boolean rewriteQueryParameters) {
        this(ResponseCodeHandler.HANDLE_404, rewriteQueryParameters);
    }

    public PathTemplateHandler(HttpHandler next) {
        this(next, true);
    }

    public PathTemplateHandler(HttpHandler next, boolean rewriteQueryParameters) {
        this.rewriteQueryParameters = rewriteQueryParameters;
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        PathTemplateMatcher.PathMatchResult<HttpHandler> match = pathTemplateMatcher.match(exchange.getRelativePath());
        if (match == null) {
            next.handleRequest(exchange);
            return;
        }
        exchange.putAttachment(PATH_TEMPLATE_MATCH, new PathTemplateMatch(match.getMatchedTemplate(), match.getParameters()));
        exchange.putAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY, new io.undertow.util.PathTemplateMatch(match.getMatchedTemplate(), match.getParameters()));
        if (rewriteQueryParameters) {
            for (Map.Entry<String, String> entry : match.getParameters().entrySet()) {
                exchange.addQueryParam(entry.getKey(), entry.getValue());
            }
        }
        match.getValue().handleRequest(exchange);
    }

    public PathTemplateHandler add(final String uriTemplate, final HttpHandler handler) {
        pathTemplateMatcher.add(uriTemplate, handler);
        return this;
    }

    public PathTemplateHandler remove(final String uriTemplate) {
        pathTemplateMatcher.remove(uriTemplate);
        return this;
    }

    @Override
    public String toString() {
        Set<PathTemplate> paths = pathTemplateMatcher.getPathTemplates();
        if (paths.size() == 1) {
            return "path-template( " + paths.toArray()[0] + " )";
        } else {
            return "path-template( {" + paths.stream().map(s -> s.getTemplateString().toString()).collect(Collectors.joining(", ")) + "} )";
        }
    }

    /**
     * @see io.undertow.util.PathTemplateMatch
     */
    @Deprecated
    public static final class PathTemplateMatch {

        private final String matchedTemplate;
        private final Map<String, String> parameters;

        public PathTemplateMatch(String matchedTemplate, Map<String, String> parameters) {
            this.matchedTemplate = matchedTemplate;
            this.parameters = parameters;
        }

        public String getMatchedTemplate() {
            return matchedTemplate;
        }

        public Map<String, String> getParameters() {
            return parameters;
        }
    }
}
