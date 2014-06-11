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

package io.undertow.predicate;

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

import io.undertow.UndertowMessages;
import io.undertow.attribute.ExchangeAttribute;
import io.undertow.attribute.ExchangeAttributeParser;
import io.undertow.attribute.ExchangeAttributes;

/**
 * Parser that can build a predicate from a string representation. The underlying syntax is quite simple, and example is
 * shown below:
 * <p/>
 * <code>
 * path["/MyPath"] or (method[value="POST"] and not headersPresent[value={Content-Type, "Content-Encoding"}, ignoreTrailer=true]
 * </code>
 * <p/>
 * The following boolean operators are built in, listed in order or precedence:
 * - not
 * - and
 * - or
 * <p/>
 * They work pretty much as you would expect them to. All other tokens are taken
 * to be predicate names. If the predicate does not require any parameters then the
 * brackets can be omitted, otherwise they are mandatory.
 * <p/>
 * If a predicate is only being passed a single parameter then the parameter name can be omitted.
 * Strings can be enclosed in optional double or single quotations marks, and quotation marks can be escaped using
 * <code>\"</code>.
 * <p/>
 * Array types are represented via a comma separated list of values enclosed in curly braces.
 * <p/>
 * TODO: should we use antlr (or whatever) here? I don't really want an extra dependency just for this...
 *
 * @author Stuart Douglas
 */
public class PredicateParser {


    public static final Predicate parse(String string, final ClassLoader classLoader) {
        final Map<String, PredicateBuilder> builders = loadBuilders(classLoader);
        final ExchangeAttributeParser attributeParser = ExchangeAttributes.parser(classLoader);
        return parse(string, builders, attributeParser);
    }

    private static Map<String, PredicateBuilder> loadBuilders(final ClassLoader classLoader) {
        ServiceLoader<PredicateBuilder> loader = ServiceLoader.load(PredicateBuilder.class, classLoader);
        final Map<String, PredicateBuilder> ret = new HashMap<>();
        for (PredicateBuilder builder : loader) {
            if (ret.containsKey(builder.name())) {
                if (ret.get(builder.name()).getClass() != builder.getClass()) {
                    throw UndertowMessages.MESSAGES.moreThanOnePredicateWithName(builder.name(), builder.getClass(), ret.get(builder.name()).getClass());
                }
            } else {
                ret.put(builder.name(), builder);
            }
        }
        return ret;
    }

    private static IllegalStateException error(final String string, int pos, String reason) {
        StringBuilder b = new StringBuilder();
        b.append(string);
        b.append('\n');
        for (int i = 0; i < pos; ++i) {
            b.append(' ');
        }
        b.append('^');
        throw UndertowMessages.MESSAGES.errorParsingPredicateString(reason, b.toString());
    }

    static Predicate parse(final String string, final Map<String, PredicateBuilder> builders, final ExchangeAttributeParser attributeParser) {

        //shunting yard algorithm
        //gets rid or parentheses and fixes up operator ordering
        Deque<Token> tokens = tokenize(string);
        Deque<String> operatorStack = new ArrayDeque<>();

        //the output, consisting of predicate nodes and string representations of operators
        //it is a bit yuck mixing up the types, but whatever
        Deque<Object> output = new ArrayDeque<>();

        while (!tokens.isEmpty()) {
            Token token = tokens.poll();
            if (isSpecialChar(token.token)) {
                if (token.token.equals("(")) {
                    operatorStack.push("(");
                } else if (token.token.equals(")")) {
                    for (; ; ) {
                        String op = operatorStack.pop();
                        if (op == null) {
                            throw error(string, token.position, "Unexpected end of input");
                        } else if (op.equals("(")) {
                            break;
                        } else {
                            output.push(op);
                        }
                    }
                } else {
                    throw error(string, token.position, "Mismatched parenthesis");
                }
            } else {
                if (isOperator(token.token)) {
                    int prec = precedence(token.token);
                    String top = operatorStack.peek();
                    while (top != null) {
                        if (top.equals("(")) {
                            break;
                        }
                        int exitingPrec = precedence(top);
                        if (prec <= exitingPrec) {
                            output.push(operatorStack.pop());
                        } else {
                            break;
                        }
                        top = operatorStack.peek();
                    }
                    operatorStack.push(token.token);
                } else {
                    output.push(parsePredicate(string, token, tokens, builders, attributeParser));
                }
            }
        }
        while (!operatorStack.isEmpty()) {
            String op = operatorStack.pop();
            if (op.equals(")")) {
                throw error(string, string.length(), "Mismatched parenthesis");
            }
            output.push(op);
        }
        //now we have our tokens
        Predicate predicate = collapseOutput(output.pop(), output).resolve();
        if (!output.isEmpty()) {
            throw error(string, 0, "Invalid expression");
        }
        return predicate;

    }

    private static Node collapseOutput(final Object token, final Deque<Object> tokens) {
        if (token instanceof Node) {
            return (Node) token;
        } else if (token.equals("and")) {
            Node n1 = collapseOutput(tokens.pop(), tokens);
            Node n2 = collapseOutput(tokens.pop(), tokens);
            return new AndNode(n2, n1);
        } else if (token.equals("or")) {
            Node n1 = collapseOutput(tokens.pop(), tokens);
            Node n2 = collapseOutput(tokens.pop(), tokens);
            return new OrNode(n2, n1);
        } else if (token.equals("not")) {
            Node n1 = collapseOutput(tokens.pop(), tokens);
            return new NotNode(n1);
        } else {
            throw new IllegalStateException("Invalid operator " + token);
        }

    }

    private static Object parsePredicate(final String string, final Token token, final Deque<Token> tokens, final Map<String, PredicateBuilder> builders, final ExchangeAttributeParser attributeParser) {
        if (token.token.equals("true")) {
            return new PredicateNode(TruePredicate.instance());
        } else if (token.token.equals("false")) {
            return new PredicateNode(FalsePredicate.instance());
        } else {
            PredicateBuilder builder = builders.get(token.token);
            if (builder == null) {

                throw error(string, token.position, "no predicate named " + token.token + " known predicates: " + builders.keySet());
            }
            Token next = tokens.peek();
            if (next.token.equals("[")) {
                final Map<String, Object> values = new HashMap<>();

                tokens.poll();
                next = tokens.poll();
                if (next == null) {
                    throw error(string, string.length(), "Unexpected end of input");
                }
                if (next.token.equals("{")) {
                    return handleSingleArrayValue(string, builder, tokens, next, attributeParser);
                }
                while (!next.token.equals("]")) {
                    Token equals = tokens.poll();
                    if (!equals.token.equals("=")) {
                        if (equals.token.equals("]") && values.isEmpty()) {
                            //single value case
                            return handleSingleValue(string, builder, next, attributeParser);
                        } else if (equals.token.equals(",")) {
                            tokens.push(equals);
                            tokens.push(next);
                            return handleSingleVarArgsValue(string, builder, tokens, next, attributeParser);
                        }
                        throw error(string, equals.position, "Unexpected token");
                    }
                    Token value = tokens.poll();
                    if (value == null) {
                        throw error(string, string.length(), "Unexpected end of input");
                    }
                    if (value.token.equals("{")) {
                        values.put(next.token, readArrayType(string, tokens, next, builder, attributeParser, "}"));
                    } else {
                        if (isOperator(value.token) || isSpecialChar(value.token)) {
                            throw error(string, value.position, "Unexpected token");
                        }

                        Class<?> type = builder.parameters().get(next.token);
                        if (type == null) {
                            throw error(string, next.position, "Unexpected parameter " + next.token);
                        }
                        values.put(next.token, coerceToType(string, value, type, attributeParser));
                    }

                    next = tokens.poll();
                    if (next == null) {
                        throw error(string, string.length(), "Unexpected end of input");
                    }
                    if (!next.token.equals("]")) {
                        if (!next.token.equals(",")) {
                            throw error(string, string.length(), "Expecting , or ]");
                        }
                        next = tokens.poll();
                        if (next == null) {
                            throw error(string, string.length(), "Unexpected end of input");
                        }
                    }
                }
                checkParameters(string, next.position, values, builder);
                return new BuilderNode(builder, values);

            } else {
                if (isSpecialChar(next.token)) {
                    throw error(string, next.position, "Unexpected character");
                }
                return new BuilderNode(builder);
            }
        }
    }

    private static Node handleSingleArrayValue(final String string, final PredicateBuilder builder, final Deque<Token> tokens, final Token token, final ExchangeAttributeParser attributeParser) {
        String sv = builder.defaultParameter();
        if (sv == null) {
            throw error(string, token.position, "default parameter not supported");
        }
        Object array = readArrayType(string, tokens, new Token(sv, token.position), builder, attributeParser, "}");
        Token close = tokens.poll();
        if (!close.token.equals("]")) {
            throw error(string, close.position, "expected ]");
        }
        return new BuilderNode(builder, Collections.singletonMap(sv, array));
    }

    private static Node handleSingleVarArgsValue(final String string, final PredicateBuilder builder, final Deque<Token> tokens, final Token token, final ExchangeAttributeParser attributeParser) {
        String sv = builder.defaultParameter();
        if (sv == null) {
            throw error(string, token.position, "default parameter not supported");
        }
        Object array = readArrayType(string, tokens, new Token(sv, token.position), builder, attributeParser, "]");
        return new BuilderNode(builder, Collections.singletonMap(sv, array));
    }

    private static Object readArrayType(final String string, final Deque<Token> tokens, Token paramName, PredicateBuilder builder, final ExchangeAttributeParser attributeParser, String expectedEndToken) {
        Class<?> type = builder.parameters().get(paramName.token);
        if (type == null) {
            throw error(string, paramName.position, "no parameter called " + paramName.token);
        } else if (!type.isArray()) {
            throw error(string, paramName.position, "parameter is not an array type " + paramName.token);
        }

        Class<?> componentType = type.getComponentType();
        final List<Object> values = new ArrayList<>();
        Token token = tokens.poll();
        while (token != null) {
            Token commaOrEnd = tokens.poll();
            values.add(coerceToType(string, token, componentType, attributeParser));
            if (commaOrEnd.token.equals(expectedEndToken)) {
                Object array = Array.newInstance(componentType, values.size());
                for (int i = 0; i < values.size(); ++i) {
                    Array.set(array, i, values.get(i));
                }
                return array;
            } else if (!commaOrEnd.token.equals(",")) {
                throw error(string, commaOrEnd.position, "expected either , or }");
            }
            token = tokens.poll();
        }
        throw error(string, string.length(), "unexpected end of input in array");
    }


    private static Object handleSingleValue(final String string, final PredicateBuilder builder, final Token next, final ExchangeAttributeParser attributeParser) {
        String sv = builder.defaultParameter();
        if (sv == null) {
            throw error(string, next.position, "default parameter not supported");
        }
        Map<String, Object> values = Collections.singletonMap(sv, coerceToType(string, next, builder.parameters().get(sv), attributeParser));
        checkParameters(string, next.position, values, builder);
        return new BuilderNode(builder, values);
    }

    private static void checkParameters(final String string, int pos, final Map<String, Object> values, final PredicateBuilder builder) {
        final Set<String> required = new HashSet<>(builder.requiredParameters());
        for (String key : values.keySet()) {
            required.remove(key);
        }
        if (!required.isEmpty()) {
            throw error(string, pos, "Missing required parameters " + required);
        }
    }


    private static Object coerceToType(final String string, final Token token, final Class<?> type, final ExchangeAttributeParser attributeParser) {
        if (type.isArray()) {
            Object array = Array.newInstance(type.getComponentType(), 1);
            Array.set(array, 0, coerceToType(string, token, type.getComponentType(), attributeParser));
            return array;
        }

        if (type == String.class) {
            return token.token;
        } else if (type.equals(Boolean.class) || type.equals(boolean.class)) {
            return Boolean.valueOf(token.token);
        } else if (type.equals(Byte.class) || type.equals(byte.class)) {
            return Byte.valueOf(token.token);
        } else if (type.equals(Character.class) || type.equals(char.class)) {
            if (token.token.length() != 1) {
                throw error(string, token.position, "Cannot coerce " + token.token + " to a Character");
            }
            return Character.valueOf(token.token.charAt(0));
        } else if (type.equals(Short.class) || type.equals(short.class)) {
            return Short.valueOf(token.token);
        } else if (type.equals(Integer.class) || type.equals(int.class)) {
            return Integer.valueOf(token.token);
        } else if (type.equals(Long.class) || type.equals(long.class)) {
            return Long.valueOf(token.token);
        } else if (type.equals(Float.class) || type.equals(float.class)) {
            return Float.valueOf(token.token);
        } else if (type.equals(Double.class) || type.equals(double.class)) {
            return Double.valueOf(token.token);
        } else if (type.equals(ExchangeAttribute.class)) {
            return attributeParser.parse(token.token);
        }

        return token.token;
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
                            throw error(string, pos, "Unexpected token");
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


    static final class Token {
        final String token;
        final int position;

        private Token(final String token, final int position) {
            this.token = token;
            this.position = position;
        }
    }

    private interface Node {

        Predicate resolve();
    }

    private static class AndNode implements Node {

        private final Node node1, node2;

        private AndNode(final Node node1, final Node node2) {
            this.node1 = node1;
            this.node2 = node2;
        }

        @Override
        public Predicate resolve() {
            return new AndPredicate(node1.resolve(), node2.resolve());
        }
    }


    private static class OrNode implements Node {

        private final Node node1, node2;

        private OrNode(final Node node1, final Node node2) {
            this.node1 = node1;
            this.node2 = node2;
        }

        @Override
        public Predicate resolve() {
            return new OrPredicate(node1.resolve(), node2.resolve());
        }
    }


    private static class NotNode implements Node {

        private final Node node;

        private NotNode(final Node node) {
            this.node = node;
        }

        @Override
        public Predicate resolve() {
            return new NotPredicate(node.resolve());
        }
    }

    private static class BuilderNode implements Node {

        private final PredicateBuilder builder;
        private final Map<String, Object> parameters;

        private BuilderNode(final PredicateBuilder builder) {
            this.builder = builder;
            this.parameters = Collections.emptyMap();
        }

        private BuilderNode(final PredicateBuilder builder, final Map<String, Object> parameters) {
            this.builder = builder;
            this.parameters = parameters;
        }

        @Override
        public Predicate resolve() {
            return builder.build(parameters);
        }
    }

    private static class PredicateNode implements Node {

        private final Predicate predicate;

        private PredicateNode(final Predicate predicate) {
            this.predicate = predicate;
        }

        @Override
        public Predicate resolve() {
            return predicate;
        }
    }

}
