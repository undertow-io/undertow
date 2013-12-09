package io.undertow.server.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.PathTemplateMatcher;

import java.util.Map;

/**
 * A handler that matches URI templates
 *
 * @author Stuart Douglas
 * @see PathTemplateMatcher
 */
public class PathTemplateHandler implements HttpHandler {

    private final boolean rewriteQueryParameters;

    public static final AttachmentKey<PathTemplateMatch> PATH_TEMPLATE_MATCH = AttachmentKey.create(PathTemplateMatch.class);

    private final PathTemplateMatcher<HttpHandler> pathTemplateMatcher = new PathTemplateMatcher<HttpHandler>();

    public PathTemplateHandler(boolean rewriteQueryParameters) {
        this.rewriteQueryParameters = rewriteQueryParameters;
    }

    public PathTemplateHandler() {
        this(true);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        PathTemplateMatcher.PathMatchResult<HttpHandler> match = pathTemplateMatcher.match(exchange.getRelativePath());
        if (match == null) {
            exchange.setResponseCode(404);
            exchange.endExchange();
            return;
        }
        exchange.putAttachment(PATH_TEMPLATE_MATCH, new PathTemplateMatch(match.getMatchedTemplate(), match.getParameters()));
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
