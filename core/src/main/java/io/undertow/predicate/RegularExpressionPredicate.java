package io.undertow.predicate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.undertow.attribute.ExchangeAttribute;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.server.HttpServerExchange;

/**
 * A predicate that does a regex match against an exchange.
 * <p/>
 * <p/>
 * By default this match is done against the relative URI, however it is possible to set it to match against other
 * exchange attributes.
 *
 * @author Stuart Douglas
 */
public class RegularExpressionPredicate implements Predicate {

    private final Pattern pattern;
    private final ExchangeAttribute matchAttribute;
    private final boolean requireFullMatch;

    public RegularExpressionPredicate(final String regex, final ExchangeAttribute matchAttribute, final boolean requireFullMatch) {
        this.requireFullMatch = requireFullMatch;
        pattern = Pattern.compile(regex);
        this.matchAttribute = matchAttribute;
    }

    public RegularExpressionPredicate(final String regex, final ExchangeAttribute matchAttribute) {
        this(regex, matchAttribute, false);
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        Matcher matcher = pattern.matcher(matchAttribute.readAttribute(value));
        final boolean matches;
        if (requireFullMatch) {
            matches = matcher.matches();
        } else {
            matches = matcher.find();
        }

        if (matches) {
            Map<String, Object> context = value.getAttachment(PREDICATE_CONTEXT);
            if (context != null) {
                int count = matcher.groupCount();
                for (int i = 0; i <= count; ++i) {
                    context.put(Integer.toString(i), matcher.group(i));
                }
            }
        }
        return matches;
    }

    public static class Builder implements PredicateBuilder {

        @Override
        public String name() {
            return "regex";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            final Map<String, Class<?>> params = new HashMap<String, Class<?>>();
            params.put("pattern", String.class);
            params.put("value", ExchangeAttribute.class);
            params.put("full-match", Boolean.class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            final Set<String> params = new HashSet<String>();
            params.add("pattern");
            return params;
        }

        @Override
        public String defaultParameter() {
            return "pattern";
        }

        @Override
        public Predicate build(final Map<String, Object> config) {
            ExchangeAttribute value = (ExchangeAttribute) config.get("value");
            if(value == null) {
                value = ExchangeAttributes.relativePath();
            }
            Boolean fullMatch = (Boolean) config.get("full-match");
            String pattern = (String) config.get("pattern");
            return new RegularExpressionPredicate(pattern, value, fullMatch == null ? false : fullMatch);
        }
    }
}
