/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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
package io.undertow.server.handlers;

import io.undertow.Handlers;
import io.undertow.server.handlers.builder.PredicatedHandlersParser;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.IPv6Ignore;
import io.undertow.testutils.IPv6Only;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * @author baranowb
 */
@RunWith(DefaultServer.class)
public class IPMatchPredicateTestCase {

    @Test
    @IPv6Ignore
    public void testAllowed1() throws IOException {
        DefaultServer.setRootHandler(
                Handlers.predicates(
                        PredicatedHandlersParser.parse(
                                "ip-match[default-allow=false, acl={'127.0.0.1 allow'}] -> {set(attribute='%{o,result}', value=ALLOWED)}"
                                        + " else {set(attribute='%{o,result}', value=DENIED)}\n",
                                getClass().getClassLoader()), exchange -> exchange.getResponseSender().send(exchange.getRelativePath())));

        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/foo/a/b");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                HttpClientUtils.readResponse(result);
                Assert.assertEquals("ALLOWED", result.getHeaders("result")[0].getValue());
                return null;
            });
        }
    }

    @Test
    @IPv6Ignore
    public void testAllowed2() throws IOException {
        DefaultServer.setRootHandler(
                Handlers.predicates(
                        PredicatedHandlersParser.parse(
                                "ip-match[default-allow=true, acl={'137.0.0.0/8 allow'}] -> {set(attribute='%{o,result}', value=ALLOWED)}"
                                        + " else {set(attribute='%{o,result}', value=DENIED)}\n",
                                getClass().getClassLoader()), exchange -> exchange.getResponseSender().send(exchange.getRelativePath())));

        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/foo/a/b");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                HttpClientUtils.readResponse(result);
                Assert.assertEquals("ALLOWED", result.getHeaders("result")[0].getValue());
                return null;
            });
        }
    }

    @Test
    @IPv6Ignore
    public void testDenied1() throws IOException {
        DefaultServer.setRootHandler(
                Handlers.predicates(
                        PredicatedHandlersParser.parse(
                                "ip-match[default-allow=false, acl={'129.0.0.0/8 allow', '127.0.0.0/8 deny'}] -> {set(attribute='%{o,result}', value=ALLOWED)}"
                                        + " else {set(attribute='%{o,result}', value=DENIED)}\n",
                                getClass().getClassLoader()), exchange -> exchange.getResponseSender().send(exchange.getRelativePath())));

        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/foo/a/b");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                HttpClientUtils.readResponse(result);
                Assert.assertEquals("DENIED", result.getHeaders("result")[0].getValue());
                return null;
            });
        }
    }

    @Test
    @IPv6Ignore
    public void testDenied2() throws IOException {
        DefaultServer.setRootHandler(
                Handlers.predicates(
                        PredicatedHandlersParser.parse(
                                "ip-match[default-allow=true, acl={'127.0.0.0/8 deny'}] -> {set(attribute='%{o,result}', value=ALLOWED)}"
                                        + " else {set(attribute='%{o,result}', value=DENIED)}\n",
                                getClass().getClassLoader()), exchange -> exchange.getResponseSender().send(exchange.getRelativePath())));

        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/foo/a/b");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                HttpClientUtils.readResponse(result);
                Assert.assertEquals("DENIED", result.getHeaders("result")[0].getValue());
                return null;
            });
        }
    }

    @Test
    @IPv6Only
    public void testAllowed6_1() throws IOException {
        DefaultServer.setRootHandler(
                Handlers.predicates(
                        PredicatedHandlersParser.parse(
                                "ip-match[default-allow=false, acl={'::1 allow'}] -> {set(attribute='%{o,result}', value=ALLOWED)}"
                                        + " else {set(attribute='%{o,result}', value=DENIED)}\n",
                                getClass().getClassLoader()), exchange -> exchange.getResponseSender().send(exchange.getRelativePath())));

        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/foo/a/b");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                HttpClientUtils.readResponse(result);
                Assert.assertEquals("ALLOWED", result.getHeaders("result")[0].getValue());
                return null;
            });
        }
    }

    @Test
    @IPv6Only
    public void testAllowed6_2() throws IOException {
        DefaultServer.setRootHandler(
                Handlers.predicates(
                        PredicatedHandlersParser.parse(
                                "ip-match[default-allow=false, acl={'0:0:0:0:0:0:0:1 allow'}] -> {set(attribute='%{o,result}', value=ALLOWED)}"
                                        + " else {set(attribute='%{o,result}', value=DENIED)}\n",
                                getClass().getClassLoader()), exchange -> exchange.getResponseSender().send(exchange.getRelativePath())));

        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/foo/a/b");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                HttpClientUtils.readResponse(result);
                Assert.assertEquals("ALLOWED", result.getHeaders("result")[0].getValue());
                return null;
            });
        }
    }

    @Test
    @IPv6Only
    public void testDenied6_1() throws IOException {
        DefaultServer.setRootHandler(
                Handlers.predicates(
                        PredicatedHandlersParser.parse(
                                "ip-match[default-allow=false, acl={'::1 deny'}] -> {set(attribute='%{o,result}', value=ALLOWED)}"
                                        + " else {set(attribute='%{o,result}', value=DENIED)}\n",
                                getClass().getClassLoader()), exchange -> exchange.getResponseSender().send(exchange.getRelativePath())));

        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/foo/a/b");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                HttpClientUtils.readResponse(result);
                Assert.assertEquals("DENIED", result.getHeaders("result")[0].getValue());
                return null;
            });
        }
    }

    @Test
    @IPv6Only
    public void testDenied_2() throws IOException {
        DefaultServer.setRootHandler(
                Handlers.predicates(
                        PredicatedHandlersParser.parse(
                                "ip-match[default-allow=false, acl={'0:0:0:0:0:0:0:1 deny'}] -> {set(attribute='%{o,result}', value=ALLOWED)}"
                                        + " else {set(attribute='%{o,result}', value=DENIED)}\n",
                                getClass().getClassLoader()), exchange -> exchange.getResponseSender().send(exchange.getRelativePath())));

        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/foo/a/b");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                HttpClientUtils.readResponse(result);
                Assert.assertEquals("DENIED", result.getHeaders("result")[0].getValue());
                return null;
            });
        }
    }
}
