package io.undertow.util;

import java.util.Map;

/**
 * The result of a path template match.
 *
 * @author Stuart Douglas
 */
public class PathTemplateMatch {

    public static final AttachmentKey<PathTemplateMatch> ATTACHMENT_KEY = AttachmentKey.create(PathTemplateMatch.class);

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
