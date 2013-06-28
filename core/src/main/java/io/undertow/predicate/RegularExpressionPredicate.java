package io.undertow.predicate;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.undertow.attribute.ExchangeAttribute;
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
                for(int i = 0; i <= count; ++i) {
                    context.put("$" + i, matcher.group(i));
                }
            }
        }
        return matches;
    }

}
