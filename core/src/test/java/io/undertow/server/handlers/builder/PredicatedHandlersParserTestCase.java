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

import io.undertow.predicate.ContainsPredicate;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.AllowedMethodsHandler;
import io.undertow.server.handlers.RequestDumpingHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.SetHeaderHandler;
import io.undertow.server.handlers.builder.PredicatedHandlersParser.BlockNode;
import io.undertow.server.handlers.builder.PredicatedHandlersParser.Node;
import io.undertow.server.handlers.builder.PredicatedHandlersParser.PredicateOperatorNode;
import io.undertow.util.HttpString;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * @author Stuart Douglas
 */
public class PredicatedHandlersParserTestCase {

    @Test
    public void testAstRepresentation1() {
        String value = "path(/foo) -> rewrite(/bar)";
        Node node = PredicatedHandlersParser.parse(value, PredicatedHandlersParser.tokenize(value));
        Assert.assertTrue(node instanceof PredicateOperatorNode);
        PredicateOperatorNode op = (PredicateOperatorNode) node;
        Assert.assertEquals("->", op.getToken().getToken());
        Assert.assertEquals("path", op.getLeft().getToken().getToken());
        Assert.assertEquals("/foo", ((PredicatedHandlersParser.ExpressionNode) op.getLeft()).getValues().get(null).toString());
    }

    @Test
    public void testAstRepresentation2() {
        String value = "path(/foo) -> rewrite(/bar)\npath(/foo) -> rewrite(/bar)";
        Node node = PredicatedHandlersParser.parse(value, PredicatedHandlersParser.tokenize(value));
        Assert.assertTrue(node instanceof BlockNode);
        BlockNode block = (BlockNode) node;
        PredicateOperatorNode op = (PredicateOperatorNode) block.getBlock().get(1);
        Assert.assertEquals("->", op.getToken().getToken());
        Assert.assertEquals("path", op.getLeft().getToken().getToken());
        Assert.assertEquals("/foo", ((PredicatedHandlersParser.ExpressionNode) op.getLeft()).getValues().get(null).toString());
    }

    @Test
    public void testAstRepresentation3() {
        String value = "path(/foo) -> { rewrite(/bar); path(/x) -> rewrite(/x)}";
        Node node = PredicatedHandlersParser.parse(value, PredicatedHandlersParser.tokenize(value));
        Assert.assertTrue(node instanceof PredicateOperatorNode);

        PredicateOperatorNode op = (PredicateOperatorNode) node;
        Assert.assertEquals("->", op.getToken().getToken());
        Assert.assertEquals("path", op.getLeft().getToken().getToken());
        Assert.assertEquals("/foo", ((PredicatedHandlersParser.ExpressionNode) op.getLeft()).getValues().get(null).toString());

        BlockNode block = (BlockNode) op.getRight();
        op = (PredicateOperatorNode) block.getBlock().get(1);
        Assert.assertEquals("->", op.getToken().getToken());
        Assert.assertEquals("path", op.getLeft().getToken().getToken());
        Assert.assertEquals("/x", ((PredicatedHandlersParser.ExpressionNode) op.getLeft()).getValues().get(null).toString());
    }


    @Test
    public void testParsedHandler1() {
        String value = "dump-request";
        List<PredicatedHandler> ret = PredicatedHandlersParser.parse(value, getClass().getClassLoader());
        Assert.assertEquals(1, ret.size());
        HttpHandler handler = ret.get(0).getHandler().wrap(ResponseCodeHandler.HANDLE_200);
        Assert.assertTrue(handler instanceof RequestDumpingHandler);
    }

    @Test
    public void testParsedHandler2() {
        String value = "header(header=a, value='a%%lb')";
        List<PredicatedHandler> ret = PredicatedHandlersParser.parse(value, getClass().getClassLoader());
        Assert.assertEquals(1, ret.size());
        SetHeaderHandler handler = (SetHeaderHandler) ret.get(0).getHandler().wrap(ResponseCodeHandler.HANDLE_200);
        Assert.assertEquals("a", handler.getHeader().toString());
        Assert.assertEquals("a%lb", handler.getValue().readAttribute(null));
    }

    @Test
    public void testParsedHandler3() {
        String value = "allowed-methods(GET)";
        List<PredicatedHandler> ret = PredicatedHandlersParser.parse(value, getClass().getClassLoader());
        Assert.assertEquals(1, ret.size());
        AllowedMethodsHandler handler = (AllowedMethodsHandler) ret.get(0).getHandler().wrap(ResponseCodeHandler.HANDLE_200);
        Assert.assertEquals(new HashSet<>(Arrays.asList(HttpString.tryFromString("GET"))), handler.getAllowedMethods());

        value = "allowed-methods(methods=GET)";
        ret = PredicatedHandlersParser.parse(value, getClass().getClassLoader());
        Assert.assertEquals(1, ret.size());
        handler = (AllowedMethodsHandler) ret.get(0).getHandler().wrap(ResponseCodeHandler.HANDLE_200);
        Assert.assertEquals(new HashSet<>(Arrays.asList(HttpString.tryFromString("GET"))), handler.getAllowedMethods());

        value = "allowed-methods(methods={GET})";
        ret = PredicatedHandlersParser.parse(value, getClass().getClassLoader());
        Assert.assertEquals(1, ret.size());
        handler = (AllowedMethodsHandler) ret.get(0).getHandler().wrap(ResponseCodeHandler.HANDLE_200);
        Assert.assertEquals(new HashSet<>(Arrays.asList(HttpString.tryFromString("GET"))), handler.getAllowedMethods());

        value = "allowed-methods({GET})";
        ret = PredicatedHandlersParser.parse(value, getClass().getClassLoader());
        Assert.assertEquals(1, ret.size());
        handler = (AllowedMethodsHandler) ret.get(0).getHandler().wrap(ResponseCodeHandler.HANDLE_200);
        Assert.assertEquals(new HashSet<>(Arrays.asList(HttpString.tryFromString("GET"))), handler.getAllowedMethods());


        value = "allowed-methods({GET, POST})";
        ret = PredicatedHandlersParser.parse(value, getClass().getClassLoader());
        Assert.assertEquals(1, ret.size());
        handler = (AllowedMethodsHandler) ret.get(0).getHandler().wrap(ResponseCodeHandler.HANDLE_200);
        Assert.assertEquals(new HashSet<>(Arrays.asList(HttpString.tryFromString("GET"), HttpString.tryFromString("POST"))), handler.getAllowedMethods());

        value = "allowed-methods(methods={GET, POST})";
        ret = PredicatedHandlersParser.parse(value, getClass().getClassLoader());
        Assert.assertEquals(1, ret.size());
        handler = (AllowedMethodsHandler) ret.get(0).getHandler().wrap(ResponseCodeHandler.HANDLE_200);
        Assert.assertEquals(new HashSet<>(Arrays.asList(HttpString.tryFromString("GET"), HttpString.tryFromString("POST"))), handler.getAllowedMethods());

        value = "allowed-methods(GET, POST)";
        ret = PredicatedHandlersParser.parse(value, getClass().getClassLoader());
        Assert.assertEquals(1, ret.size());
        handler = (AllowedMethodsHandler) ret.get(0).getHandler().wrap(ResponseCodeHandler.HANDLE_200);
        Assert.assertEquals(new HashSet<>(Arrays.asList(HttpString.tryFromString("GET"), HttpString.tryFromString("POST"))), handler.getAllowedMethods());
    }


    @Test
    public void testParsedPredicatedHandler1() {
        String value = "contains(value='a', search=b) -> dump-request";
        List<PredicatedHandler> ret = PredicatedHandlersParser.parse(value, getClass().getClassLoader());
        Assert.assertEquals(1, ret.size());
        HttpHandler handler = ret.get(0).getHandler().wrap(ResponseCodeHandler.HANDLE_200);
        Assert.assertTrue(handler instanceof RequestDumpingHandler);

        ContainsPredicate predicate = (ContainsPredicate) ret.get(0).getPredicate();
        Assert.assertEquals("a", predicate.getAttribute().readAttribute(null));
        Assert.assertArrayEquals(new String[]{"b"}, predicate.getValues());

        value = "contains(value='a', search={b}) -> dump-request";
        ret = PredicatedHandlersParser.parse(value, getClass().getClassLoader());
        Assert.assertEquals(1, ret.size());
        handler = ret.get(0).getHandler().wrap(ResponseCodeHandler.HANDLE_200);
        Assert.assertTrue(handler instanceof RequestDumpingHandler);

        predicate = (ContainsPredicate) ret.get(0).getPredicate();
        Assert.assertEquals("a", predicate.getAttribute().readAttribute(null));
        Assert.assertArrayEquals(new String[]{"b"}, predicate.getValues());

        value = "contains[value='a', search={b, c}] -> dump-request";
        ret = PredicatedHandlersParser.parse(value, getClass().getClassLoader());
        Assert.assertEquals(1, ret.size());
        handler = ret.get(0).getHandler().wrap(ResponseCodeHandler.HANDLE_200);
        Assert.assertTrue(handler instanceof RequestDumpingHandler);

        predicate = (ContainsPredicate) ret.get(0).getPredicate();
        Assert.assertEquals("a", predicate.getAttribute().readAttribute(null));
        Assert.assertArrayEquals(new String[]{"b", "c"}, predicate.getValues());
    }

}
