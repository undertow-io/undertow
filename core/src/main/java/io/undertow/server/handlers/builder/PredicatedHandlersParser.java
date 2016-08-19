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
import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateBuilder;
import io.undertow.predicate.Predicates;
import io.undertow.predicate.PredicatesHandler;
import io.undertow.server.HandlerWrapper;
import io.undertow.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Parser for the undertow-handlers.conf file.
 * <p/>
 * This file has a line by line syntax, specifying predicate -&gt; handler. If no predicate is specified then
 * the line is assumed to just contain a handler.
 *
 * @author Stuart Douglas
 */
public class PredicatedHandlersParser {

    public static final String ELSE = "else";
    public static final String ARROW = "->";
    public static final String NOT = "not";
    public static final String OR = "or";
    public static final String AND = "and";
    public static final String TRUE = "true";
    public static final String FALSE = "false";

    public static List<PredicatedHandler> parse(final File file, final ClassLoader classLoader) {
        return parse(file.toPath(), classLoader);
    }

    public static List<PredicatedHandler> parse(final Path file, final ClassLoader classLoader) {
        try {
            return parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8), classLoader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<PredicatedHandler> parse(final InputStream inputStream, final ClassLoader classLoader) {
        return parse(FileUtils.readFile(inputStream), classLoader);
    }

    public static List<PredicatedHandler> parse(final String contents, final ClassLoader classLoader) {
        Deque<Token> tokens = tokenize(contents);

        Node node = parse(contents, tokens);
        Map<String, PredicateBuilder> predicateBuilders = loadPredicateBuilders(classLoader);
        Map<String, HandlerBuilder> handlerBuilders = loadHandlerBuilders(classLoader);

        final ExchangeAttributeParser attributeParser = ExchangeAttributes.parser(classLoader);
        return handleNode(contents, node, predicateBuilders, handlerBuilders, attributeParser);
    }


    public static Predicate parsePredicate(String string, ClassLoader classLoader) {
        Deque<Token> tokens = tokenize(string);
        Node node = parse(string, tokens);
        Map<String, PredicateBuilder> predicateBuilders = loadPredicateBuilders(classLoader);
        final ExchangeAttributeParser attributeParser = ExchangeAttributes.parser(classLoader);
        return handlePredicateNode(string, node, predicateBuilders, attributeParser);
    }

    public static HandlerWrapper parseHandler(String string, ClassLoader classLoader) {
        Deque<Token> tokens = tokenize(string);
        Node node = parse(string, tokens);
        Map<String, HandlerBuilder> handlerBuilders = loadHandlerBuilders(classLoader);
        final ExchangeAttributeParser attributeParser = ExchangeAttributes.parser(classLoader);
        return handleHandlerNode(string, (ExpressionNode)node, handlerBuilders, attributeParser);
    }
    private static List<PredicatedHandler> handleNode(String contents, Node node, Map<String, PredicateBuilder> predicateBuilders, Map<String, HandlerBuilder> handlerBuilders, ExchangeAttributeParser attributeParser) {
        if(node instanceof BlockNode) {
            return handleBlockNode(contents, (BlockNode) node, predicateBuilders, handlerBuilders, attributeParser);
        } else if(node instanceof ExpressionNode) {
            HandlerWrapper handler =  handleHandlerNode(contents, (ExpressionNode) node, handlerBuilders, attributeParser);
            return Collections.singletonList(new PredicatedHandler(Predicates.truePredicate(), handler));
        } else if(node instanceof PredicateOperatorNode) {
            return Collections.singletonList(handlePredicateOperatorNode(contents, (PredicateOperatorNode)node, predicateBuilders, handlerBuilders, attributeParser));
        } else {
            throw error(contents, node.getToken().getPosition(), "unexpected token " + node.getToken());
        }
    }

    private static PredicatedHandler handlePredicateOperatorNode(String contents, PredicateOperatorNode node, Map<String, PredicateBuilder> predicateBuilders, Map<String, HandlerBuilder> handlerBuilders, ExchangeAttributeParser parser) {
        Predicate predicate = handlePredicateNode(contents, node.getLeft(), predicateBuilders, parser);
        HandlerWrapper ret = handlePredicatedAction(contents, node.getRight(), predicateBuilders, handlerBuilders, parser);
        HandlerWrapper elseBranch = null;
        if(node.getElseBranch() != null) {
            elseBranch = handlePredicatedAction(contents, node.getElseBranch(), predicateBuilders, handlerBuilders, parser);
        }
        return new PredicatedHandler(predicate, ret, elseBranch);
    }

    private static HandlerWrapper handlePredicatedAction(String contents, Node node, Map<String, PredicateBuilder> predicateBuilders, Map<String, HandlerBuilder> handlerBuilders, ExchangeAttributeParser parser) {
        if(node instanceof ExpressionNode) {
            return handleHandlerNode(contents, (ExpressionNode) node, handlerBuilders, parser);
        } else if(node instanceof BlockNode) {
            List<PredicatedHandler> handlers = handleBlockNode(contents, (BlockNode) node, predicateBuilders, handlerBuilders, parser);
            return  new PredicatesHandler.Wrapper(handlers, false);
        } else {
            throw error(contents, node.getToken().getPosition(), "unexpected token " + node.getToken());
        }
    }

    private static List<PredicatedHandler> handleBlockNode(String contents, BlockNode node, Map<String, PredicateBuilder> predicateBuilders, Map<String, HandlerBuilder> handlerBuilders, ExchangeAttributeParser parser) {
        List<PredicatedHandler> ret = new ArrayList<>();
        for(Node line : node.getBlock()) {
            ret.addAll(handleNode(contents, line, predicateBuilders, handlerBuilders, parser));
        }
        return ret;

    }

    private static HandlerWrapper handleHandlerNode(String contents, ExpressionNode node, Map<String, HandlerBuilder> handlerBuilders, ExchangeAttributeParser parser) {
        Token token = node.getToken();
        HandlerBuilder builder = handlerBuilders.get(token.getToken());
        if (builder == null) {
            throw error(contents, token.getPosition(), "no handler named " + token.getToken() + " known handlers are " + handlerBuilders.keySet());
        }
        Map<String, Object> parameters = new HashMap<>();

        for(Map.Entry<String, Node> val : node.getValues().entrySet()) {
            String name = val.getKey();
            if(name == null) {
                if(builder.defaultParameter() == null) {
                    throw error(contents, token.getPosition(), "default parameter not supported");
                }
                name = builder.defaultParameter();
            }
            Class<?> type = builder.parameters().get(name);
            if(type == null) {
                throw error(contents, val.getValue().getToken().getPosition(), "unknown parameter " + name);
            }
            if(val.getValue() instanceof ValueNode) {
                parameters.put(name, coerceToType(contents, val.getValue().getToken(), type, parser));
            } else if(val.getValue() instanceof ArrayNode) {
                parameters.put(name, readArrayType(contents, name, (ArrayNode)val.getValue(), parser, type));
            } else {
                throw error(contents, val.getValue().getToken().getPosition(), "unexpected node " + val.getValue());
            }
        }
        return builder.build(parameters);
    }

    private static Predicate handlePredicateNode(String contents, Node node, Map<String, PredicateBuilder> handlerBuilders, ExchangeAttributeParser parser) {
        if(node instanceof AndNode) {
            AndNode andNode = (AndNode)node;
            return Predicates.and(handlePredicateNode(contents, andNode.getLeft(), handlerBuilders, parser), handlePredicateNode(contents, andNode.getRight(), handlerBuilders, parser));
        } else if(node instanceof OrNode) {
            OrNode orNode = (OrNode)node;
            return Predicates.or(handlePredicateNode(contents, orNode.getLeft(), handlerBuilders, parser), handlePredicateNode(contents, orNode.getRight(), handlerBuilders, parser));
        } else if(node instanceof NotNode) {
            NotNode orNode = (NotNode)node;
            return Predicates.not(handlePredicateNode(contents, orNode.getNode(), handlerBuilders, parser));
        } else if(node instanceof ExpressionNode) {
            return handlePredicateExpressionNode(contents, (ExpressionNode) node, handlerBuilders, parser);
        }else if(node instanceof OperatorNode) {
            switch (node.getToken().getToken()) {
                case TRUE: {
                    return Predicates.truePredicate();
                }
                case FALSE: {
                    return Predicates.falsePredicate();
                }
            }
        }
        throw error(contents, node.getToken().getPosition(), "unexpected node " + node);
    }

    private static Predicate handlePredicateExpressionNode(String contents, ExpressionNode node, Map<String, PredicateBuilder> handlerBuilders, ExchangeAttributeParser parser) {
        Token token = node.getToken();
        PredicateBuilder builder = handlerBuilders.get(token.getToken());
        if (builder == null) {
            throw error(contents, token.getPosition(), "no predicate named " + token.getToken() + " known predicates are " + handlerBuilders.keySet());
        }
        Map<String, Object> parameters = new HashMap<>();

        for(Map.Entry<String, Node> val : node.getValues().entrySet()) {
            String name = val.getKey();
            if(name == null) {
                if(builder.defaultParameter() == null) {
                    throw error(contents, token.getPosition(), "default parameter not supported");
                }
                name = builder.defaultParameter();
            }
            Class<?> type = builder.parameters().get(name);
            if(type == null) {
                throw error(contents, val.getValue().getToken().getPosition(), "unknown parameter " + name);
            }
            if(val.getValue() instanceof ValueNode) {
                parameters.put(name, coerceToType(contents, val.getValue().getToken(), type, parser));
            } else if(val.getValue() instanceof ArrayNode) {
                parameters.put(name, readArrayType(contents, name, (ArrayNode)val.getValue(), parser, type));
            } else {
                throw error(contents, val.getValue().getToken().getPosition(), "unexpected node " + val.getValue());
            }
        }
        return builder.build(parameters);
    }

    private static Object readArrayType(final String string, String paramName, ArrayNode value, ExchangeAttributeParser parser, Class type) {
        if (!type.isArray()) {
            throw error(string, value.getToken().getPosition(), "parameter is not an array type " + paramName);
        }

        Class<?> componentType = type.getComponentType();
        final List<Object> values = new ArrayList<>();
        for(Token token : value.getValues()) {
            values.add(coerceToType(string, token, componentType, parser));
        }
        Object array = Array.newInstance(componentType, values.size());
        for (int i = 0; i < values.size(); ++i) {
            Array.set(array, i, values.get(i));
        }
        return array;
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
                throw error(string, token.getPosition(), "Cannot coerce " + token.getToken() + " to a Character");
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

    private static Map<String, PredicateBuilder> loadPredicateBuilders(final ClassLoader classLoader) {
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

    private static Map<String, HandlerBuilder> loadHandlerBuilders(final ClassLoader classLoader) {
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

    static Node parse(final String string, Deque<Token> tokens) {
        return parse(string, tokens, true);
    }

    static Node parse(final String string, Deque<Token> tokens, boolean topLevel) {

        //shunting yard algorithm
        //gets rid or parentheses and fixes up operator ordering
        Deque<Token> operatorStack = new ArrayDeque<>();

        Deque<Node> output = new ArrayDeque<>();
        List<Node> blocks = new ArrayList<>();


        while (!tokens.isEmpty()) {
            Token token = tokens.poll();
            if(token.getToken().equals("{")) {
                output.push(parse(string, tokens, false));
            } else if(token.getToken().equals("}")) {
                if(topLevel) {
                    throw error(string, token.getPosition(), "Unexpected token");
                }
                break;
            } else if(token.getToken().equals("\n") || token.getToken().equals(";")) {
                handleLineEnd(string, operatorStack, output, blocks);
            } else if (isSpecialChar(token.getToken())) {
                if (token.getToken().equals("(")) {
                    operatorStack.push(token);
                } else if (token.getToken().equals(")")) {
                    for (; ; ) {
                        Token op = operatorStack.pop();
                        if (op == null) {
                            throw error(string, token.getPosition(), "Unexpected end of input");
                        } else if (op.getToken().equals("(")) {
                            break;
                        } else {
                            output.push(new OperatorNode(op));
                        }
                    }
                } else {
                    output.push(new OperatorNode(token));
                }
            } else {
                if (isOperator(token.getToken()) && !token.getToken().equals(ELSE)) {
                    int prec = precedence(token.getToken());
                    Token top = operatorStack.peek();
                    while (top != null) {
                        if (top.getToken().equals("(")) {
                            break;
                        }
                        int exitingPrec = precedence(top.getToken());
                        if (prec <= exitingPrec) {
                            output.push(new OperatorNode(operatorStack.pop()));
                        } else {
                            break;
                        }
                        top = operatorStack.peek();
                    }
                    operatorStack.push(token);
                } else {
                    output.push(parseExpression(string, token, tokens));
                }
            }
        }
        handleLineEnd(string, operatorStack, output, blocks);
        if(blocks.size() == 1) {
            return blocks.get(0);
        } else {
            return new BlockNode(new Token("", 0), blocks);
        }
    }

    private static void handleLineEnd(String string, Deque<Token> operatorStack, Deque<Node> output, List<Node> blocks) {
        while (!operatorStack.isEmpty()) {
            Token op = operatorStack.pop();
            if (op.getToken().equals(")")) {
                throw error(string, string.length(), "Mismatched parenthesis");
            }
            output.push(new OperatorNode(op));
        }
        if(output.isEmpty()) {
            return;
        }
        //now we have our tokens for this line
        Node predicate = collapseOutput(output.pop(), output);
        if (!output.isEmpty()) {
            throw error(string, output.getFirst().getToken().getPosition(), "Invalid expression");
        }
        blocks.add(predicate);
    }

    private static Node collapseOutput(final Node token, final Deque<Node> tokens) {
        if (token instanceof OperatorNode) {
            OperatorNode node = (OperatorNode) token;
            if (node.token.getToken().equals(AND)) {
                Node n1 = collapseOutput(tokens.pop(), tokens);
                Node n2 = collapseOutput(tokens.pop(), tokens);
                return new AndNode(token.getToken(), n2, n1);
            } else if (node.token.getToken().equals(OR)) {
                Node n1 = collapseOutput(tokens.pop(), tokens);
                Node n2 = collapseOutput(tokens.pop(), tokens);
                return new OrNode(token.getToken(), n2, n1);
            } else if (node.token.getToken().equals(NOT)) {
                Node n1 = collapseOutput(tokens.pop(), tokens);
                return new NotNode(token.getToken(), n1);
            } else if (node.token.getToken().equals(ARROW)) {
                Node n1 = collapseOutput(tokens.pop(), tokens);
                Node n2 = null;
                Node elseBranch = null;
                final Node popped = tokens.pop();
                if(popped.getToken().getToken().equals(ELSE)) {
                    elseBranch = n1;
                    n1 = collapseOutput(tokens.pop(), tokens);
                    n2 = collapseOutput(tokens.pop(), tokens);
                } else {
                    n2 = collapseOutput(popped, tokens);
                }
                return new PredicateOperatorNode(token.getToken(), n2, n1, elseBranch);
            }  else {
                return token;
            }
        } else {
            return token;
        }

    }

    private static Node parseExpression(final String string, final Token token, final Deque<Token> tokens) {
        if (token.getToken().equals(TRUE)) {
            return new OperatorNode(token);
        } else if (token.getToken().equals(FALSE)) {
            return new OperatorNode(token);
        } else {
            Token next = tokens.peek();
            String endChar = ")";
            if (next != null && (next.getToken().equals("[") || next.getToken().equals("("))) {
                if (next.getToken().equals("[")) {
                    endChar = "]";
                    UndertowLogger.ROOT_LOGGER.oldStylePredicateSyntax(string);
                }
                final Map<String, Node> values = new HashMap<>();

                tokens.poll();
                next = tokens.poll();
                if (next == null) {
                    throw error(string, string.length(), "Unexpected end of input");
                }
                if (next.getToken().equals("{")) {
                    return handleSingleArrayValue(string, token, tokens, endChar);
                }
                while (!next.getToken().equals(endChar)) {
                    Token equals = tokens.poll();
                    if (equals == null) {
                        throw error(string, string.length(), "Unexpected end of input");
                    }
                    if (!equals.getToken().equals("=")) {
                        if (equals.getToken().equals(endChar) && values.isEmpty()) {
                            //single value case
                            return handleSingleValue(token, next);
                        } else if (equals.getToken().equals(",")) {
                            tokens.push(equals);
                            tokens.push(next);
                            return handleSingleVarArgsValue(string, token, tokens, endChar);
                        }
                        throw error(string, equals.getPosition(), "Unexpected token");
                    }
                    Token value = tokens.poll();
                    if (value == null) {
                        throw error(string, string.length(), "Unexpected end of input");
                    }
                    if (value.getToken().equals("{")) {
                        values.put(next.getToken(), new ArrayNode(value, readArrayType(string, tokens,"}")));
                    } else {
                        if (isOperator(value.getToken()) || isSpecialChar(value.getToken())) {
                            throw error(string, value.getPosition(), "Unexpected token");
                        }
                        values.put(next.getToken(), new ValueNode(value));
                    }

                    next = tokens.poll();
                    if (next == null) {
                        throw error(string, string.length(), "Unexpected end of input");
                    }
                    if (!next.getToken().equals(endChar)) {
                        if (!next.getToken().equals(",")) {
                            throw error(string, string.length(), "Expecting , or " + endChar);
                        }
                        next = tokens.poll();
                        if (next == null) {
                            throw error(string, string.length(), "Unexpected end of input");
                        }
                    }
                }
                return new ExpressionNode(token, values);

            } else {
                if (next != null && isSpecialChar(next.getToken())) {
                    throw error(string, next.getPosition(), "Unexpected character");
                }
                return new ExpressionNode(token, Collections.<String, Node>emptyMap());
            }
        }
    }

    private static Node handleSingleArrayValue(final String string, final Token builder, final Deque<Token> tokens, String endChar) {
        List<Token> array = readArrayType(string, tokens, "}");
        Token close = tokens.poll();
        if (!close.getToken().equals(endChar)) {
            throw error(string, close.getPosition(), "expected " + endChar);
        }
        return new ExpressionNode(builder, Collections.<String, Node>singletonMap(null, new ArrayNode(builder, array)));
    }

    private static Node handleSingleVarArgsValue(final String string, final Token expressionName, final Deque<Token> tokens, String endChar) {
        List<Token> array = readArrayType(string, tokens, endChar);
        return new ExpressionNode(expressionName, Collections.<String, Node>singletonMap(null, new ArrayNode(expressionName, array)));
    }

    private static List<Token> readArrayType(final String string, final Deque<Token> tokens, String expectedEndToken) {
        final List<Token> values = new ArrayList<>();
        Token token = tokens.poll();
        if(token.getToken().equals(expectedEndToken)) {
            return Collections.emptyList();
        }
        while (token != null) {
            Token commaOrEnd = tokens.poll();
            values.add(token);
            if (commaOrEnd.getToken().equals(expectedEndToken)) {
                return values;
            } else if (!commaOrEnd.getToken().equals(",")) {
                throw error(string, commaOrEnd.getPosition(), "expected either , or " + expectedEndToken);
            }
            token = tokens.poll();
        }
        throw error(string, string.length(), "unexpected end of input in array");
    }


    private static Node handleSingleValue(final Token token, final Token next) {
        return new ExpressionNode(token, Collections.<String, Node>singletonMap(null, new ValueNode(next)));
    }

    private static int precedence(String operator) {
        if (operator.equals(NOT)) {
            return 3;
        } else if (operator.equals(AND)) {
            return 2;
        } else if (operator.equals(OR)) {
            return 1;
        } else if (operator.equals(ARROW)) {
            return -1000;
        }
        throw new IllegalStateException();
    }


    private static boolean isOperator(final String op) {
        return op.equals(AND) || op.equals(OR) || op.equals(NOT) || op.equals(ARROW);
    }

    private static boolean isSpecialChar(String token) {
        if (token.length() == 1) {
            char c = token.charAt(0);
            switch (c) {
                case '(':
                case ')':
                case ',':
                case '=':
                case '[':
                case ']':
                    return true;
                default:
                    return false;
            }
        }
        return false;
    }

    public static Deque<Token> tokenize(final String string) {
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
                } else if (c == '\n' || c == '\r') {
                    ret.add(new Token(current.toString(), pos));
                    current.setLength(0);
                    currentStringDelim = 0;
                    ret.add(new Token("\n", pos));
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
                    case '\r':
                    case '\n': {
                        if (current.length() != 0) {
                            ret.add(new Token(current.toString(), pos));
                            current.setLength(0);
                        }
                        ret.add(new Token("\n", pos));
                        break;
                    }
                    case ';':
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
                    case '%':
                    case '$': {
                        current.append(c);
                        if (string.charAt(pos + 1) == '{') {
                            inVariable = true;
                        }
                        break;
                    }
                    case '-':
                        if (inVariable) {
                            current.append(c);
                        } else {
                            if (pos != string.length() && string.charAt(pos + 1) == '>') {
                                pos++;
                                if (current.length() != 0) {
                                    ret.add(new Token(current.toString(), pos));
                                    current.setLength(0);
                                }
                                ret.add(new Token(ARROW, pos));
                            } else {
                                current.append(c);
                            }
                        }
                        break;
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

    private static IllegalStateException error(final String string, int pos, String reason) {
        StringBuilder b = new StringBuilder();
        int linePos = 0;
        for (int i = 0; i < string.length(); ++i) {
            if (string.charAt(i) == '\n') {
                if (i >= pos) {
                    //truncate the string at the error line
                    break;
                } else {
                    linePos = 0;
                }
            } else if (i < pos) {
                linePos++;
            }
            b.append(string.charAt(i));
        }
        b.append('\n');
        for (int i = 0; i < linePos; ++i) {
            b.append(' ');
        }
        b.append('^');
        throw UndertowMessages.MESSAGES.errorParsingPredicateString(reason, b.toString());
    }

    public interface Node {

        Token getToken();

    }


    /**
     * A parsed expression
     */
    static class ExpressionNode implements Node {

        private final Token token;
        private final Map<String, Node> values;

        private ExpressionNode(Token token, Map<String, Node> values) {
            this.token = token;
            this.values = values;
        }

        public Token getToken() {
            return token;
        }

        public Map<String, Node> getValues() {
            return values;
        }
    }

    static class ArrayNode implements Node {
        private final Token start;
        private final List<Token> values;

        private ArrayNode(Token start, List<Token> tokens) {
            this.start = start;
            this.values = tokens;
        }

        public List<Token> getValues() {
            return values;
        }

        @Override
        public Token getToken() {
            return start;
        }
    }

    static class ValueNode implements Node {
        private final Token value;

        private ValueNode(Token value) {
            this.value = value;
        }

        public Token getValue() {
            return value;
        }

        @Override
        public String toString() {
            return value.getToken();
        }

        @Override
        public Token getToken() {
            return value;
        }
    }

    static class OperatorNode implements Node {

        private final Token token;

        private OperatorNode(Token token) {
            this.token = token;
        }

        public Token getToken() {
            return token;
        }
    }


    static class AndNode implements Node {
        private final Token token;
        private final Node left;
        private final Node right;

        AndNode(Token token, Node left, Node right) {
            this.token = token;
            this.left = left;
            this.right = right;
        }

        public Node getLeft() {
            return left;
        }

        public Node getRight() {
            return right;
        }

        public Token getToken() {
            return token;
        }
    }

    static class OrNode implements Node {
        private final Token token;
        private final Node left;
        private final Node right;

        OrNode(Token token, Node left, Node right) {
            this.token = token;
            this.left = left;
            this.right = right;
        }

        public Node getLeft() {
            return left;
        }

        public Node getRight() {
            return right;
        }

        public Token getToken() {
            return token;
        }
    }


    static class PredicateOperatorNode implements Node {
        private final Token token;
        private final Node left;
        private final Node right;
        private final Node elseBranch;

        PredicateOperatorNode(Token token, Node left, Node right, Node elseBranch) {
            this.token = token;
            this.left = left;
            this.right = right;
            this.elseBranch = elseBranch;
        }

        public Node getLeft() {
            return left;
        }

        public Node getRight() {
            return right;
        }

        public Node getElseBranch() {
            return elseBranch;
        }

        @Override
        public Token getToken() {
            return token;
        }
    }

    static class NotNode implements Node {

        private final Token token;
        private final Node node;

        NotNode(Token token, Node node) {
            this.token = token;
            this.node = node;
        }

        public Node getNode() {
            return node;
        }

        public Token getToken() {
            return token;
        }
    }

    static class BlockNode implements Node {
        private final Token token;
        private final List<Node> block;

        BlockNode(Token token, List<Node> block) {
            this.token = token;
            this.block = block;
        }

        public List<Node> getBlock() {
            return block;
        }

        @Override
        public Token getToken() {
            return token;
        }
    }


    static final class Token {
        private final String token;
        private final int position;

        Token(final String token, final int position) {
            this.token = token;
            this.position = position;
        }

        public String getToken() {
            return token;
        }

        public int getPosition() {
            return position;
        }

        @Override
        public String toString() {
            return token + " <" + position + ">";
        }
    }
}
