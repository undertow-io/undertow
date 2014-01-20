package io.undertow.attribute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;

/**
 * Attribute parser for exchange attributes. This builds an attribute from a string definition.
 * <p/>
 * This uses a service loader mechanism to allow additional token types to be loaded. Token definitions are loaded
 * from the provided class loader.
 *
 * @author Stuart Douglas
 * @see ExchangeAttributes#parser(ClassLoader)
 */
public class ExchangeAttributeParser {

    private final List<ExchangeAttributeBuilder> builders;

    ExchangeAttributeParser(final ClassLoader classLoader) {
        ServiceLoader<ExchangeAttributeBuilder> loader = ServiceLoader.load(ExchangeAttributeBuilder.class, classLoader);
        final List<ExchangeAttributeBuilder> builders = new ArrayList<ExchangeAttributeBuilder>();
        for (ExchangeAttributeBuilder instance : loader) {
            builders.add(instance);
        }
        this.builders = Collections.unmodifiableList(builders);

    }

    /**
     * Parses the provided value string, and turns it into a list of exchange attributes.
     * <p/>
     * Tokens are created according to the following rules:
     * <p/>
     * %a - % followed by single character. %% is an escape for a literal %
     * %{.*}a? - % plus curly braces with any amount of content inside, followed by an optional character
     * ${.*} - $ followed by a curly braces to reference an item from the predicate context
     *
     * @param valueString
     * @return
     */
    public ExchangeAttribute parse(final String valueString) {
        final List<ExchangeAttribute> attributes = new ArrayList<ExchangeAttribute>();
        int pos = 0;
        int state = 0; //0 = literal, 1 = %, 2 = %{, 3 = $, 4 = ${
        for (int i = 0; i < valueString.length(); ++i) {
            char c = valueString.charAt(i);
            switch (state) {
                case 0: {
                    if (c == '%' || c == '$') {
                        if (pos != i) {
                            attributes.add(parseSingleToken(valueString.substring(pos, i)));
                            pos = i;
                        }
                        if (c == '%') {
                            state = 1;
                        } else {
                            state = 3;
                        }
                    }
                    break;
                }
                case 1: {
                    if (c == '{') {
                        state = 2;
                    } else if (c == '%') {
                        //literal percent
                        attributes.add(new ConstantExchangeAttribute("%"));
                        pos = i + 1;
                        state = 0;
                    } else {
                        attributes.add(parseSingleToken(valueString.substring(pos, i + 1)));
                        pos = i + 1;
                        state = 0;
                    }
                    break;
                }
                case 2: {
                    if (c == '}') {
                        attributes.add(parseSingleToken(valueString.substring(pos, i + 1)));
                        pos = i + 1;
                        state = 0;
                    }
                    break;
                }
                case 3: {
                    if (c == '{') {
                        state = 4;
                    } else {
                        state = 0;
                    }
                    break;
                }
                case 4: {
                    if (c == '}') {
                        attributes.add(parseSingleToken(valueString.substring(pos, i + 1)));
                        pos = i + 1;
                        state = 0;
                    }
                    break;
                }

            }
        }
        switch (state) {
            case 0:
            case 1:
            case 3:{
                if(pos != valueString.length()) {
                    attributes.add(parseSingleToken(valueString.substring(pos)));
                }
                break;
            }
            case 2:
            case 4: {
                throw UndertowMessages.MESSAGES.mismatchedBraces(valueString);
            }
        }
        if(attributes.size() == 1) {
            return attributes.get(0);
        }
        return new CompositeExchangeAttribute(attributes.toArray(new ExchangeAttribute[attributes.size()]));
    }

    public ExchangeAttribute parseSingleToken(final String token) {
        for (final ExchangeAttributeBuilder builder : builders) {
            ExchangeAttribute res = builder.build(token);
            if (res != null) {
                return res;
            }
        }
        if (token.startsWith("%")) {
            UndertowLogger.ROOT_LOGGER.unknownVariable(token);
        }
        return new ConstantExchangeAttribute(token);
    }

}
