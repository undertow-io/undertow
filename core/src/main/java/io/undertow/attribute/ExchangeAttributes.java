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

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

import java.util.Arrays;
import java.util.Collections;

/**
 * Utility class for retrieving exchange attributes
 *
 * @author Stuart Douglas
 */
public class ExchangeAttributes {

    public static ExchangeAttributeParser parser(final ClassLoader classLoader) {
         return new ExchangeAttributeParser(classLoader, Collections.<ExchangeAttributeWrapper>emptyList());
    }

    public static ExchangeAttributeParser parser(final ClassLoader classLoader, ExchangeAttributeWrapper ... wrappers) {
        return new ExchangeAttributeParser(classLoader, Arrays.asList(wrappers));
    }

    public static ExchangeAttribute cookie(final String cookieName) {
        return new CookieAttribute(cookieName);
    }

    public static ExchangeAttribute bytesSent(boolean dashIfZero) {
        return new BytesSentAttribute(dashIfZero);
    }

    public static ExchangeAttribute dateTime() {
        return DateTimeAttribute.INSTANCE;
    }

    public static ExchangeAttribute localIp() {
        return LocalIPAttribute.INSTANCE;
    }

    public static ExchangeAttribute localPort() {
        return LocalPortAttribute.INSTANCE;
    }

    public static ExchangeAttribute localServerName() {
        return LocalServerNameAttribute.INSTANCE;
    }

    public static ExchangeAttribute queryString() {
        return QueryStringAttribute.INSTANCE;
    }

    public static ExchangeAttribute relativePath() {
        return RelativePathAttribute.INSTANCE;
    }

    public static ExchangeAttribute remoteIp() {
        return RemoteIPAttribute.INSTANCE;
    }

    public static ExchangeAttribute remoteObfuscatedIp() {
        return RemoteObfuscatedIPAttribute.INSTANCE;
    }

    public static ExchangeAttribute remoteUser() {
        return RemoteUserAttribute.INSTANCE;
    }

    public static ExchangeAttribute requestHeader(final HttpString header) {
        return new RequestHeaderAttribute(header);
    }

    public static ExchangeAttribute requestList() {
        return RequestLineAttribute.INSTANCE;
    }

    public static ExchangeAttribute requestMethod() {
        return RequestMethodAttribute.INSTANCE;
    }

    public static ExchangeAttribute requestProtocol() {
        return RequestProtocolAttribute.INSTANCE;
    }

    public static ExchangeAttribute requestURL() {
        return RequestURLAttribute.INSTANCE;
    }

    public static ExchangeAttribute responseCode() {
        return ResponseCodeAttribute.INSTANCE;
    }

    public static ExchangeAttribute responseReasonPhrase() {
        return ResponseReasonPhraseAttribute.INSTANCE;
    }

    public static ExchangeAttribute responseHeader(final HttpString header) {
        return new ResponseHeaderAttribute(header);
    }

    public static ExchangeAttribute transportProtocol() {
        return TransportProtocolAttribute.INSTANCE;
    }

    public static ExchangeAttribute threadName() {
        return ThreadNameAttribute.INSTANCE;
    }

    public static ExchangeAttribute constant(String value) {
        return new ConstantExchangeAttribute(value);
    }

    public static String  resolve(final HttpServerExchange exchange, final ExchangeAttribute[] attributes) {
        final StringBuilder result = new StringBuilder();
        for (int i = 0; i < attributes.length; ++i) {
            final String str = attributes[i].readAttribute(exchange);
            if (str != null) {
                result.append(str);
            }
        }
        return result.toString();
    }


    private ExchangeAttributes() {

    }

    public static ExchangeAttribute authenticationType() {
        return AuthenticationTypeExchangeAttribute.INSTANCE;
    }
}
