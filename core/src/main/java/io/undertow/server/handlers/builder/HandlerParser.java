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

package io.undertow.server.handlers.builder;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.attribute.ExchangeAttribute;
import io.undertow.attribute.ExchangeAttributeParser;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.server.HandlerWrapper;
import io.undertow.util.PredicateTokeniser;
import io.undertow.util.PredicateTokeniser.Token;

import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Parser that can build a handler from a string representation. The underlying syntax is quite simple, and example is
 * shown below:
 * <p>
 * <code>
 * rewrite[value="/path"]
 * </code>
 * If a handler is only being passed a single parameter then the parameter name can be omitted.
 * Strings can be enclosed in optional double or single quotations marks, and quotation marks can be escaped using
 * <code>\"</code>.
 * <p>
 * Array types are represented via a comma separated list of values enclosed in curly braces.
 * <p>
 *
 * @author Stuart Douglas
 */
public class HandlerParser {


    public static final HandlerWrapper parse(String string, final ClassLoader classLoader) {
        final Map<String, HandlerBuilder> builders = loadBuilders(classLoader);
        final ExchangeAttributeParser attributeParser = ExchangeAttributes.parser(classLoader);
        Deque<Token> tokens = tokenize(string);
        return parse(string, tokens, builders, attributeParser);
    }


    public static final HandlerWrapper parse(String string, Deque<Token> tokens, final ClassLoader classLoader) {
        final Map<String, HandlerBuilder> builders = loadBuilders(classLoader);
        final ExchangeAttributeParser attributeParser = ExchangeAttributes.parser(classLoader);
        return parse(string, new ArrayDeque<Token>(tokens), builders, attributeParser);
    }

    private static Map<String, HandlerBuilder> loadBuilders(final ClassLoader classLoader) {
        ServiceLoader<HandlerBuilder> loader = ServiceLoader.load(HandlerBuilder.class, classLoader);
        final Map<String, HandlerBuilder> ret = new HashMap<>();
        for (HandlerBuilder builder : loader) {
            if (ret.containsKey(builder.name())) {
                if (ret.get(builder.name()).getClass() != builder.getClass()) {
                    throw UndertowMessages.MESSAGES.moreThanOneHandlerWithName(builder.name(), builder.getClass(), ret.get(builder.name()).getClass());
                }
            } else {
                ret.put(builder.name(), builder);
            }
        }
        return ret;
    }

    static HandlerWrapper parse(final String string, final Map<String, HandlerBuilder> builders, final ExchangeAttributeParser attributeParser) {

        //shunting yard algorithm
        //gets rid or parentheses and fixes up operator ordering
        Deque<Token> tokens = tokenize(string);
        return parseBuilder(string, tokens.pop(), tokens, builders, attributeParser);

    }

    static HandlerWrapper parse(final String string, Deque<Token> tokens, final Map<String, HandlerBuilder> builders, final ExchangeAttributeParser attributeParser) {
        return parseBuilder(string, tokens.pop(), tokens, builders, attributeParser);
    }

    private static HandlerWrapper parseBuilder(final String string, final Token token, final Deque<Token> tokens, final Map<String, HandlerBuilder> builders, final ExchangeAttributeParser attributeParser) {
        HandlerBuilder builder = builders.get(token.getToken());
        if (builder == null) {
            throw PredicateTokeniser.error(string, token.getPosition(), "no handler named " + token.getToken());
        }
        if(!tokens.isEmpty()) {
            Token last = tokens.isEmpty() ? token : tokens.getLast();
            Token next = tokens.peek();
            String endChar = ")";
            if (next.getToken().equals("(") || next.getToken().equals("[")) {
                if (next.getToken().equals("[")) {
                    UndertowLogger.ROOT_LOGGER.oldStylePredicateSyntax(string);
                    endChar = "]";
                }
                final Map<String, Object> values = new HashMap<>();

                tokens.poll();
                next = tokens.poll();
                if (next == null) {
                    throw PredicateTokeniser.error(string, last.getPosition(), "Unexpected end of input");
                }
                if (next.getToken().equals("{")) {
                    return handleSingleArrayValue(string, builder, tokens, next, attributeParser, endChar, last);
                }
                while (!next.getToken().equals(endChar)) {
                    Token equals = tokens.poll();
                    if (!equals.getToken().equals("=")) {
                        if (equals.getToken().equals(endChar) && values.isEmpty()) {
                            //single value case
                            return handleSingleValue(string, builder, next, attributeParser);
                        } else if (equals.getToken().equals(",")) {
                            tokens.push(equals);
                            tokens.push(next);
                            return handleSingleVarArgsValue(string, builder, tokens, next, attributeParser, endChar, last);
                        }
                        throw PredicateTokeniser.error(string, equals.getPosition(), "Unexpected token");
                    }
                    Token value = tokens.poll();
                    if (value == null) {
                        throw PredicateTokeniser.error(string, string.length(), "Unexpected end of input");
                    }
                    if (value.getToken().equals("{")) {
                        values.put(next.getToken(), readArrayType(string, tokens, next, builder, attributeParser, "}", last));
                    } else {
                        if (isOperator(value.getToken()) || isSpecialChar(value.getToken())) {
                            throw PredicateTokeniser.error(string, value.getPosition(), "Unexpected token");
                        }

                        Class<?> type = builder.parameters().get(next.getToken());
                        if (type == null) {
                            throw PredicateTokeniser.error(string, next.getPosition(), "Unexpected parameter " + next.getToken());
                        }
                        values.put(next.getToken(), coerceToType(string, value, type, attributeParser));
                    }

                    next = tokens.poll();
                    if (next == null) {
                        throw PredicateTokeniser.error(string, last.getPosition(), "Unexpected end of input");
                    }
                    if (!next.getToken().equals(endChar)) {
                        if (!next.getToken().equals(",")) {
                            throw PredicateTokeniser.error(string, next.getPosition(), "Expecting , or " + endChar);
                        }
                        next = tokens.poll();
                        if (next == null) {
                            throw PredicateTokeniser.error(string, last.getPosition(), "Unexpected end of input");
                        }
                    }
                }
                checkParameters(string, next.getPosition(), values, builder);
                return builder.build(values);

            } else {
                throw PredicateTokeniser.error(string, next.getPosition(), "Unexpected character");
            }
        } else {
            checkParameters(string, token.getPosition(), Collections.<String,Object>emptyMap(), builder);
            return builder.build(Collections.<String,Object>emptyMap());
        }
    }

    private static HandlerWrapper handleSingleArrayValue(final String string, final HandlerBuilder builder, final Deque<Token> tokens, final Token token, final ExchangeAttributeParser attributeParser, String endChar, Token last) {
        String sv = builder.defaultParameter();
        if (sv == null) {
            throw PredicateTokeniser.error(string, token.getPosition(), "default parameter not supported");
        }
        Object array = readArrayType(string, tokens, new Token(sv, token.getPosition()), builder, attributeParser, "}", last);
        Token close = tokens.poll();
        if (!close.getToken().equals(endChar)) {
            throw PredicateTokeniser.error(string, close.getPosition(), "expected " + endChar);
        }
        return builder.build(Collections.singletonMap(sv, array));
    }

    private static HandlerWrapper handleSingleVarArgsValue(final String string, final HandlerBuilder builder, final Deque<Token> tokens, final Token token, final ExchangeAttributeParser attributeParser, String endChar, Token last) {
        String sv = builder.defaultParameter();
        if (sv == null) {
            throw PredicateTokeniser.error(string, token.getPosition(), "default parameter not supported");
        }
        Object array = readArrayType(string, tokens, new Token(sv, token.getPosition()), builder, attributeParser, endChar, last);
        return builder.build(Collections.singletonMap(sv, array));
    }

    private static Object readArrayType(final String string, final Deque<Token> tokens, Token paramName, HandlerBuilder builder, final ExchangeAttributeParser attributeParser, String expectedEndToken, Token last) {
        Class<?> type = builder.parameters().get(paramName.getToken());
        if (type == null) {
            throw PredicateTokeniser.error(string, paramName.getPosition(), "no parameter called " + paramName.getToken());
        } else if (!type.isArray()) {
            throw PredicateTokeniser.error(string, paramName.getPosition(), "parameter is not an array type " + paramName.getToken());
        }

        Class<?> componentType = type.getComponentType();
        final List<Object> values = new ArrayList<>();
        Token token = tokens.poll();
        while (token != null) {
            Token commaOrEnd = tokens.poll();
            values.add(coerceToType(string, token, componentType, attributeParser));
            if (commaOrEnd.getToken().equals(expectedEndToken)) {
                Object array = Array.newInstance(componentType, values.size());
                for (int i = 0; i < values.size(); ++i) {
                    Array.set(array, i, values.get(i));
                }
                return array;
            } else if (!commaOrEnd.getToken().equals(",")) {
                throw PredicateTokeniser.error(string, commaOrEnd.getPosition(), "expected either , or }");
            }
            token = tokens.poll();
        }
        throw PredicateTokeniser.error(string, last.getPosition(), "unexpected end of input in array");
    }


    private static HandlerWrapper handleSingleValue(final String string, final HandlerBuilder builder, final Token next, final ExchangeAttributeParser attributeParser) {
        String sv = builder.defaultParameter();
        if (sv == null) {
            throw PredicateTokeniser.error(string, next.getPosition(), "default parameter not supported");
        }
        Map<String, Object> values = Collections.singletonMap(sv, coerceToType(string, next, builder.parameters().get(sv), attributeParser));
        checkParameters(string, next.getPosition(), values, builder);
        return builder.build(values);
    }

    private static void checkParameters(final String string, int pos, final Map<String, Object> values, final HandlerBuilder builder) {
        final Set<String> required = new HashSet<>(builder.requiredParameters());
        for (String key : values.keySet()) {
            required.remove(key);
        }
        if (!required.isEmpty()) {
            throw PredicateTokeniser.error(string, pos, "Missing required parameters " + required);
        }
    }


    private static Object coerceToType(final String string, final Token token, final Class<?> type, final ExchangeAttributeParser attributeParser) {
        if (type.isArray()) {
            Object array = Array.newInstance(type.getComponentType(), 1);
            Array.set(array, 0, coerceToType(string, token, type.getComponentType(), attributeParser));
            return array;
        }

        if (type == String.class) {
            return token.getToken();
        } else if (type.equals(Boolean.class) || type.equals(boolean.class)) {
            return Boolean.valueOf(token.getToken());
        } else if (type.equals(Byte.class) || type.equals(byte.class)) {
            return Byte.valueOf(token.getToken());
        } else if (type.equals(Character.class) || type.equals(char.class)) {
            if (token.getToken().length() != 1) {
                throw PredicateTokeniser.error(string, token.getPosition(), "Cannot coerce " + token.getToken() + " to a Character");
            }
            return Character.valueOf(token.getToken().charAt(0));
        } else if (type.equals(Short.class) || type.equals(short.class)) {
            return Short.valueOf(token.getToken());
        } else if (type.equals(Integer.class) || type.equals(int.class)) {
            return Integer.valueOf(token.getToken());
        } else if (type.equals(Long.class) || type.equals(long.class)) {
            return Long.valueOf(token.getToken());
        } else if (type.equals(Float.class) || type.equals(float.class)) {
            return Float.valueOf(token.getToken());
        } else if (type.equals(Double.class) || type.equals(double.class)) {
            return Double.valueOf(token.getToken());
        } else if (type.equals(ExchangeAttribute.class)) {
            return attributeParser.parse(token.getToken());
        }

        return token.getToken();
    }

    private static int precedence(String operator) {
        if (operator.equals("not")) {
            return 3;
        } else if (operator.equals("and")) {
            return 2;
        } else if (operator.equals("or")) {
            return 1;
        }
        throw new IllegalStateException();
    }


    private static boolean isOperator(final String op) {
        return op.equals("and") || op.equals("or") || op.equals("not");
    }

    private static boolean isSpecialChar(String token) {
        if (token.length() != 1) {
            return false;
        }
        char c = token.charAt(0);
        switch (c) {
            case '(':
            case ')':
            case ',':
            case '=':
            case '{':
            case '}':
            case '[':
            case ']':
                return true;
            default:
                return false;
        }
    }

    static Deque<Token> tokenize(final String string) {
        char currentStringDelim = 0;
        boolean inVariable = false;

        int pos = 0;
        StringBuilder current = new StringBuilder();
        Deque<Token> ret = new ArrayDeque<>();
        while (pos < string.length()) {
            char c = string.charAt(pos);
            if (currentStringDelim != 0) {
                if (c == currentStringDelim && current.charAt(current.length() - 1) != '\\') {
                    ret.add(new Token(current.toString(), pos));
                    current.setLength(0);
                    currentStringDelim = 0;
                } else {
                    current.append(c);
                }
            } else {
                switch (c) {
                    case ' ':
                    case '\t': {
                        if (current.length() != 0) {
                            ret.add(new Token(current.toString(), pos));
                            current.setLength(0);
                        }
                        break;
                    }
                    case '(':
                    case ')':
                    case ',':
                    case '=':
                    case '[':
                    case ']':
                    case '{':
                    case '}': {
                        if (inVariable) {
                            current.append(c);
                            if (c == '}') {
                                inVariable = false;
                            }
                        } else {
                            if (current.length() != 0) {
                                ret.add(new Token(current.toString(), pos));
                                current.setLength(0);
                            }
                            ret.add(new Token("" + c, pos));
                        }
                        break;
                    }
                    case '"':
                    case '\'': {
                        if (current.length() != 0) {
                            throw PredicateTokeniser.error(string, pos, "Unexpected token");
                        }
                        currentStringDelim = c;
                        break;
                    }
                    case '%': {
                        current.append(c);
                        if (string.charAt(pos + 1) == '{') {
                            inVariable = true;
                        }
                        break;
                    }
                    default:
                        current.append(c);
                }
            }
            ++pos;
        }
        if (current.length() > 0) {
            ret.add(new Token(current.toString(), string.length()));
        }
        return ret;
    }
}
