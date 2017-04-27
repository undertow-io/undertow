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

package io.undertow.attribute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;

/**
 * Attribute parser for exchange attributes. This builds an attribute from a string definition.
 * <p>
 * This uses a service loader mechanism to allow additional token types to be loaded. Token definitions are loaded
 * from the provided class loader.
 *
 * @author Stuart Douglas
 * @see ExchangeAttributes#parser(ClassLoader)
 */
public class ExchangeAttributeParser {

    private final List<ExchangeAttributeBuilder> builders;
    private final List<ExchangeAttributeWrapper> wrappers;

    ExchangeAttributeParser(final ClassLoader classLoader, List<ExchangeAttributeWrapper> wrappers) {
        this.wrappers = wrappers;
        ServiceLoader<ExchangeAttributeBuilder> loader = ServiceLoader.load(ExchangeAttributeBuilder.class, classLoader);
        final List<ExchangeAttributeBuilder> builders = new ArrayList<>();
        for (ExchangeAttributeBuilder instance : loader) {
            builders.add(instance);
        }
        //sort with highest priority first
        Collections.sort(builders, new Comparator<ExchangeAttributeBuilder>() {
            @Override
            public int compare(ExchangeAttributeBuilder o1, ExchangeAttributeBuilder o2) {
                return Integer.compare(o2.priority(), o1.priority());
            }
        });
        this.builders = Collections.unmodifiableList(builders);

    }

    /**
     * Parses the provided value string, and turns it into a list of exchange attributes.
     * <p>
     * Tokens are created according to the following rules:
     * <p>
     * %a - % followed by single character. %% is an escape for a literal %
     * %{.*}a? - % plus curly braces with any amount of content inside, followed by an optional character
     * ${.*} - $ followed by a curly braces to reference an item from the predicate context
     *
     * @param valueString
     * @return
     */
    public ExchangeAttribute parse(final String valueString) {
        final List<ExchangeAttribute> attributes = new ArrayList<>();
        int pos = 0;
        int state = 0; //0 = literal, 1 = %, 2 = %{, 3 = $, 4 = ${
        for (int i = 0; i < valueString.length(); ++i) {
            char c = valueString.charAt(i);
            switch (state) {
                case 0: {
                    if (c == '%' || c == '$') {
                        if (pos != i) {
                            attributes.add(wrap(parseSingleToken(valueString.substring(pos, i))));
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
                        attributes.add(wrap(new ConstantExchangeAttribute("%")));
                        pos = i + 1;
                        state = 0;
                    } else {
                        attributes.add(wrap(parseSingleToken(valueString.substring(pos, i + 1))));
                        pos = i + 1;
                        state = 0;
                    }
                    break;
                }
                case 2: {
                    if (c == '}') {
                        attributes.add(wrap(parseSingleToken(valueString.substring(pos, i + 1))));
                        pos = i + 1;
                        state = 0;
                    }
                    break;
                }
                case 3: {
                    if (c == '{') {
                        state = 4;
                    } else if (c == '$') {
                        //literal dollars
                        attributes.add(wrap(new ConstantExchangeAttribute("$")));
                        pos = i + 1;
                        state = 0;
                    } else {
                        attributes.add(wrap(parseSingleToken(valueString.substring(pos, i + 1))));
                        pos = i + 1;
                        state = 0;
                    }
                    break;
                }
                case 4: {
                    if (c == '}') {
                        attributes.add(wrap(parseSingleToken(valueString.substring(pos, i + 1))));
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
                    attributes.add(wrap(parseSingleToken(valueString.substring(pos))));
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

    private ExchangeAttribute wrap(ExchangeAttribute attribute) {
        ExchangeAttribute res = attribute;
        for(ExchangeAttributeWrapper w : wrappers) {
            res = w.wrap(res);
        }
        return res;
    }

}
