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

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

/**
 * Tests for ExchangeAttributeParser.
 * These tests verify that the parser correctly parses attribute strings and produces
 * the correct ExchangeAttribute instances with proper toString() representations.
 */
public class ExchangeAttributeParserTest {

    /**
     * Test for parsing simple nested attributes.
     * Verifies that nested attributes are parsed as a single attribute, not as a composite.
     */
    @Test
    public void testSimpleNestedAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String attributeString = "%{myCustomAttr:%{REQUEST_LINE}}";
        ExchangeAttribute attribute = parser.parse(attributeString);
        // The attribute should be parsed as a single nested attribute, not as a composite of multiple attributes
        Assert.assertEquals("Parsed attribute string should match input", attributeString, attribute.toString());
        Assert.assertFalse("Attribute should not be of CompositeExchangeAttribute type", attribute instanceof CompositeExchangeAttribute);
    }

    /**
     * Test for parsing complex nested attributes with multiple levels of nesting.
     * Verifies that deeply nested attributes are parsed as a single attribute.
     */
    @Test
    public void testComplexNestedAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String attributeString = "%{myCustomAttr:%{anotherCustomAttr:%{REQUEST_LINE}}-%{REQUEST_METHOD}}";
        ExchangeAttribute attribute = parser.parse(attributeString);
        // The attribute should be parsed as a single nested attribute, not as a composite of multiple attributes
        Assert.assertFalse("Attribute should not be of CompositeExchangeAttribute type", attribute instanceof CompositeExchangeAttribute);
        Assert.assertEquals("Parsed attribute string should match input", attributeString, attribute.toString());
    }

    /**
     * Test for parsing a simple attribute string.
     * Verifies that REQUEST_LINE is correctly parsed to RequestLineAttribute.
     */
    @Test
    public void testSimpleAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String attributeString = "%{REQUEST_LINE}";
        ExchangeAttribute attribute = parser.parse(attributeString);
        Assert.assertTrue("Attribute should be of RequestLineAttribute type", attribute instanceof RequestLineAttribute);
        Assert.assertEquals("Parsed attribute string should match input", attributeString, attribute.toString());
    }

    /**
     * Test for RequestMethodAttribute parser.
     * Verifies that both long form (%{METHOD}) and short form (%m) are correctly parsed.
     */
    @Test
    public void testRequestMethodAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());

        // test long form
        ExchangeAttribute attribute = parser.parse("%{METHOD}");
        Assert.assertTrue("Should parse to RequestMethodAttribute", attribute instanceof RequestMethodAttribute);
        Assert.assertEquals("toString should return long form", "%{METHOD}", attribute.toString());

        // test short form
        attribute = parser.parse("%m");
        Assert.assertTrue("Should parse to RequestMethodAttribute", attribute instanceof RequestMethodAttribute);
        Assert.assertEquals("toString should return long form", "%{METHOD}", attribute.toString());
    }

    /**
     * Test for RelativePathAttribute parser.
     * Verifies that both long form (%{RELATIVE_PATH}) and short form (%R) are correctly parsed.
     */
    @Test
    public void testRelativePathAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());

        // test long form
        String relativePathStringAttribute = "%{RELATIVE_PATH}";
        ExchangeAttribute attribute = parser.parse(relativePathStringAttribute);
        Assert.assertTrue("Should parse to RelativePathAttribute", attribute instanceof RelativePathAttribute);
        Assert.assertEquals("toString should return long form", relativePathStringAttribute, attribute.toString());

        // test short form
        relativePathStringAttribute = "%R";
        attribute = parser.parse(relativePathStringAttribute);
        Assert.assertTrue("Should parse to RelativePathAttribute", attribute instanceof RelativePathAttribute);
        Assert.assertEquals("toString should return long form", "%{RELATIVE_PATH}", attribute.toString());
    }

    /**
     * Test for RemoteIPAttribute parser.
     * Verifies that both long form (%{REMOTE_IP}) and short form (%a) are correctly parsed.
     */
    @Test
    public void testRemoteIPAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());

        // test long form
        String remoteIPStringAttribute = "%{REMOTE_IP}";
        ExchangeAttribute attribute = parser.parse(remoteIPStringAttribute);
        Assert.assertTrue("Should parse to RemoteIPAttribute", attribute instanceof RemoteIPAttribute);
        Assert.assertEquals("toString should return long form", remoteIPStringAttribute, attribute.toString());

        // test short form
        remoteIPStringAttribute = "%a";
        attribute = parser.parse(remoteIPStringAttribute);
        Assert.assertTrue("Should parse to RemoteIPAttribute", attribute instanceof RemoteIPAttribute);
        Assert.assertEquals("toString should return long form", "%{REMOTE_IP}", attribute.toString());
    }

    /**
     * Test for LocalIPAttribute parser.
     * Verifies that both long form (%{LOCAL_IP}) and short form (%A) are correctly parsed.
     */
    @Test
    public void testLocalIPAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());

        // test long form
        String localIPStringAttribute = "%{LOCAL_IP}";
        ExchangeAttribute attribute = parser.parse(localIPStringAttribute);
        Assert.assertTrue("Should parse to LocalIPAttribute", attribute instanceof LocalIPAttribute);
        Assert.assertEquals("toString should return long form", localIPStringAttribute, attribute.toString());

        // test short form
        localIPStringAttribute = "%A";
        attribute = parser.parse(localIPStringAttribute);
        Assert.assertTrue("Should parse to LocalIPAttribute", attribute instanceof LocalIPAttribute);
        Assert.assertEquals("toString should return long form", "%{LOCAL_IP}", attribute.toString());
    }

    /**
     * Test for LocalPortAttribute parser.
     * Verifies that both long form (%{LOCAL_PORT}) and short form (%p) are correctly parsed.
     */
    @Test
    public void testLocalPortAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());

        // test long form
        String localPortStringAttribute = "%{LOCAL_PORT}";
        ExchangeAttribute attribute = parser.parse(localPortStringAttribute);
        Assert.assertTrue("Should parse to LocalPortAttribute", attribute instanceof LocalPortAttribute);
        Assert.assertEquals("toString should return long form", localPortStringAttribute, attribute.toString());

        // test short form
        localPortStringAttribute = "%p";
        attribute = parser.parse(localPortStringAttribute);
        Assert.assertTrue("Should parse to LocalPortAttribute", attribute instanceof LocalPortAttribute);
        Assert.assertEquals("toString should return long form", "%{LOCAL_PORT}", attribute.toString());
    }

    /**
     * Test for RequestProtocolAttribute parser.
     * Verifies that both long form (%{PROTOCOL}) and short form (%H) are correctly parsed.
     */
    @Test
    public void testRequestProtocolAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());

        // test long form
        String requestProtocolStringAttribute = "%{PROTOCOL}";
        ExchangeAttribute attribute = parser.parse(requestProtocolStringAttribute);
        Assert.assertTrue("Should parse to RequestProtocolAttribute", attribute instanceof RequestProtocolAttribute);
        Assert.assertEquals("toString should return long form", requestProtocolStringAttribute, attribute.toString());

        // test short form
        requestProtocolStringAttribute = "%H";
        attribute = parser.parse(requestProtocolStringAttribute);
        Assert.assertTrue("Should parse to RequestProtocolAttribute", attribute instanceof RequestProtocolAttribute);
        Assert.assertEquals("toString should return long form", "%{PROTOCOL}", attribute.toString());
    }

    /**
     * Test for QueryStringAttribute parser.
     * Verifies that long form, short form, and bare query string forms are correctly parsed.
     */
    @Test
    public void testQueryStringAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());

        // test long form (includes '?')
        String queryStringAttribute = "%{QUERY_STRING}";
        ExchangeAttribute attribute = parser.parse(queryStringAttribute);
        Assert.assertTrue("Should parse to QueryStringAttribute", attribute instanceof QueryStringAttribute);
        Assert.assertEquals("toString should return QUERY_STRING form", queryStringAttribute, attribute.toString());

        // test short form (includes '?')
        attribute = parser.parse("%q");
        Assert.assertTrue("Should parse to QueryStringAttribute", attribute instanceof QueryStringAttribute);
        Assert.assertEquals("toString should return long form", queryStringAttribute, attribute.toString());

        // test bare query string (does not include '?')
        queryStringAttribute = "%{BARE_QUERY_STRING}";
        attribute = parser.parse("%{BARE_QUERY_STRING}");
        Assert.assertTrue("Should parse to QueryStringAttribute", attribute instanceof QueryStringAttribute);
        Assert.assertEquals("toString should return BARE_QUERY_STRING form", queryStringAttribute, attribute.toString());
    }

    /**
     * Test for RequestURLAttribute parser.
     * Verifies that both long form (%{REQUEST_URL}) and short form (%U) are correctly parsed.
     */
    @Test
    public void testRequestURLAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());

        // test long form
        String requestURLStringAttribute = "%{REQUEST_URL}";
        ExchangeAttribute attribute = parser.parse(requestURLStringAttribute);
        Assert.assertTrue("Should parse to RequestURLAttribute", attribute instanceof RequestURLAttribute);
        Assert.assertEquals("toString should return long form", requestURLStringAttribute, attribute.toString());

        // test short form
        requestURLStringAttribute = "%U";
        attribute = parser.parse(requestURLStringAttribute);
        Assert.assertTrue("Should parse to RequestURLAttribute", attribute instanceof RequestURLAttribute);
        Assert.assertEquals("toString should return long form", "%{REQUEST_URL}", attribute.toString());
    }

    /**
     * Test for BytesSentAttribute parser.
     * Verifies that long form, uppercase short form, and lowercase short form are correctly parsed.
     */
    @Test
    public void testBytesSentAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());

        // test long form
        String bytesSentStringAttribute = "%{BYTES_SENT}";
        ExchangeAttribute attribute = parser.parse(bytesSentStringAttribute);
        Assert.assertTrue("Should parse to BytesSentAttribute", attribute instanceof BytesSentAttribute);
        Assert.assertEquals("toString should return long form", bytesSentStringAttribute, attribute.toString());

        // test short form (uppercase, no dash)
        bytesSentStringAttribute = "%B";
        attribute = parser.parse(bytesSentStringAttribute);
        Assert.assertTrue("Should parse to BytesSentAttribute", attribute instanceof BytesSentAttribute);
        Assert.assertEquals("toString should return long form", "%{BYTES_SENT}", attribute.toString());

        // test short form (lowercase, dash if zero)
        bytesSentStringAttribute = "%b";
        attribute = parser.parse(bytesSentStringAttribute);
        Assert.assertTrue("Should parse to BytesSentAttribute", attribute instanceof BytesSentAttribute);
        Assert.assertEquals("toString should return long form", "%{BYTES_SENT}", attribute.toString());
    }

    /**
     * Test for BytesReadAttribute parser.
     * Verifies that long form, uppercase short form, and lowercase short form are correctly parsed.
     */
    @Test
    public void testBytesReadAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());

        // test long form
        String bytesReadStringAttribute = "%{BYTES_READ}";
        ExchangeAttribute attribute = parser.parse(bytesReadStringAttribute);
        Assert.assertTrue("Should parse to BytesReadAttribute", attribute instanceof BytesReadAttribute);
        Assert.assertEquals("toString should return long form", bytesReadStringAttribute, attribute.toString());

        // test short form (uppercase, do dash)
        bytesReadStringAttribute = "%X";
        attribute = parser.parse(bytesReadStringAttribute);
        Assert.assertTrue("Should parse to BytesReadAttribute", attribute instanceof BytesReadAttribute);
        Assert.assertEquals("toString should return long form", "%{BYTES_READ}", attribute.toString());

        // test short form (lowercase, dash if zero)
        bytesReadStringAttribute = "%x";
        attribute = parser.parse(bytesReadStringAttribute);
        Assert.assertTrue("Should parse to BytesReadAttribute", attribute instanceof BytesReadAttribute);
        Assert.assertEquals("toString should return long form", "%{BYTES_READ}", attribute.toString());
    }

    /**
     * Test for DateTimeAttribute parser.
     * Verifies that long form, short form, and custom format are correctly parsed.
     */
    @Test
    public void testDateTimeAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());

        // test long form
        String dateTimeStringAttribute = "%{DATE_TIME}";
        ExchangeAttribute attribute = parser.parse(dateTimeStringAttribute);
        Assert.assertTrue("Should parse to DateTimeAttribute", attribute instanceof DateTimeAttribute);
        Assert.assertEquals("toString should return long form", dateTimeStringAttribute, attribute.toString());

        // test short form
        dateTimeStringAttribute = "%t";
        attribute = parser.parse(dateTimeStringAttribute);
        Assert.assertTrue("Should parse to DateTimeAttribute", attribute instanceof DateTimeAttribute);
        Assert.assertEquals("toString should return long form", "%{DATE_TIME}", attribute.toString());

        // test custom format
        String customDateTimeStringAttribute = "%{time,yyyy-MM-dd}";
        attribute = parser.parse(customDateTimeStringAttribute);
        Assert.assertTrue("Should parse to DateTimeAttribute", attribute instanceof DateTimeAttribute);
        Assert.assertEquals("Custom format should be preserved", customDateTimeStringAttribute, attribute.toString());
    }

    /**
     * Test for RemoteUserAttribute parser.
     * Verifies that both long form (%{REMOTE_USER}) and short form (%u) are correctly parsed.
     */
    @Test
    public void testRemoteUserAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());

        // test long form
        String remoteUserStringAttribute = "%{REMOTE_USER}";
        ExchangeAttribute attribute = parser.parse(remoteUserStringAttribute);
        Assert.assertTrue("Should parse to RemoteUserAttribute", attribute instanceof RemoteUserAttribute);
        Assert.assertEquals("toString should return long form", remoteUserStringAttribute, attribute.toString());

        // test short form
        remoteUserStringAttribute = "%u";
        attribute = parser.parse(remoteUserStringAttribute);
        Assert.assertTrue("Should parse to RemoteUserAttribute", attribute instanceof RemoteUserAttribute);
        Assert.assertEquals("toString should return long form", "%{REMOTE_USER}", attribute.toString());
    }

    /**
     * Test for ResponseCodeAttribute parser.
     * Verifies that both long form (%{RESPONSE_CODE}) and short form (%s) are correctly parsed.
     */
    @Test
    public void testResponseCodeAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());

        // test long form
        String responseCodeStringAttribute = "%{RESPONSE_CODE}";
        ExchangeAttribute attribute = parser.parse(responseCodeStringAttribute);
        Assert.assertTrue("Should parse to ResponseCodeAttribute", attribute instanceof ResponseCodeAttribute);
        Assert.assertEquals("toString should return long form", responseCodeStringAttribute, attribute.toString());

        // test short form
        responseCodeStringAttribute = "%s";
        attribute = parser.parse(responseCodeStringAttribute);
        Assert.assertTrue("Should parse to ResponseCodeAttribute", attribute instanceof ResponseCodeAttribute);
        Assert.assertEquals("toString should return long form", "%{RESPONSE_CODE}", attribute.toString());
    }

    /**
     * Test for RemoteHostAttribute parser.
     * Verifies that both long form (%{REMOTE_HOST}) and short form (%h) are correctly parsed.
     */
    @Test
    public void testRemoteHostAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());

        // test long form
        String remoteHostStringAttribute = "%{REMOTE_HOST}";
        ExchangeAttribute attribute = parser.parse(remoteHostStringAttribute);
        Assert.assertTrue("Should parse to RemoteHostAttribute", attribute instanceof RemoteHostAttribute);
        Assert.assertEquals("toString should return long form", remoteHostStringAttribute, attribute.toString());

        // test short form
        remoteHostStringAttribute = "%h";
        attribute = parser.parse(remoteHostStringAttribute);
        Assert.assertTrue("Should parse to RemoteHostAttribute", attribute instanceof RemoteHostAttribute);
        Assert.assertEquals("toString should return long form", "%{REMOTE_HOST}", attribute.toString());
    }

    /**
     * Test for RemoteObfuscatedIPAttribute parser.
     * Verifies that both long form (%{REMOTE_OBFUSCATED_IP}) and short form (%o) are correctly parsed.
     */
    @Test
    public void testRemoteObfuscatedIPAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());

        // test long form
        String remoteObfuscatedIPStringAttribute = "%{REMOTE_OBFUSCATED_IP}";
        ExchangeAttribute attribute = parser.parse(remoteObfuscatedIPStringAttribute);
        Assert.assertTrue("Should parse to RemoteObfuscatedIPAttribute", attribute instanceof RemoteObfuscatedIPAttribute);
        Assert.assertEquals("toString should return long form", remoteObfuscatedIPStringAttribute, attribute.toString());

        // test short form
        remoteObfuscatedIPStringAttribute = "%o";
        attribute = parser.parse(remoteObfuscatedIPStringAttribute);
        Assert.assertTrue("Should parse to RemoteObfuscatedIPAttribute", attribute instanceof RemoteObfuscatedIPAttribute);
        Assert.assertEquals("toString should return long form", "%{REMOTE_OBFUSCATED_IP}", attribute.toString());
    }

    /**
     * Test for IdentUsernameAttribute parser.
     * Verifies that the short form (%l) is correctly parsed.
     */
    @Test
    public void testIdentUsernameAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String identUsernameStringAttribute = "%l";

        ExchangeAttribute attribute = parser.parse(identUsernameStringAttribute);
        Assert.assertTrue("Should parse to IdentUsernameAttribute", attribute instanceof IdentUsernameAttribute);
        Assert.assertEquals("toString should match input", identUsernameStringAttribute, attribute.toString());
    }

    /**
     * Test for AuthenticationTypeExchangeAttribute parser.
     * Verifies that the long form (%{AUTHENTICATION_TYPE}) is correctly parsed.
     */
    @Test
    public void testAuthenticationTypeAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String authenticationTypeStringAttribute = "%{AUTHENTICATION_TYPE}";

        ExchangeAttribute attribute = parser.parse(authenticationTypeStringAttribute);
        Assert.assertTrue("Should parse to AuthenticationTypeExchangeAttribute", attribute instanceof AuthenticationTypeExchangeAttribute);
        Assert.assertEquals("toString should match input", authenticationTypeStringAttribute, attribute.toString());
    }

    /**
     * Test for HostAndPortAttribute parser.
     * Verifies that the long form (%{HOST_AND_PORT}) is correctly parsed.
     */
    @Test
    public void testHostAndPortAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String hostAndPortStringAttribute = "%{HOST_AND_PORT}";

        ExchangeAttribute attribute = parser.parse(hostAndPortStringAttribute);
        Assert.assertTrue("Should parse to HostAndPortAttribute", attribute instanceof HostAndPortAttribute);
        Assert.assertEquals("toString should match input", hostAndPortStringAttribute, attribute.toString());
    }

    /**
     * Test for LocalServerNameAttribute parser.
     * Verifies that both long form (%{LOCAL_SERVER_NAME}) and short form (%v) are correctly parsed.
     */
    @Test
    public void testLocalServerNameAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());

        // test long form
        String localServerNameStringAttribute = "%{LOCAL_SERVER_NAME}";
        ExchangeAttribute attribute = parser.parse(localServerNameStringAttribute);
        Assert.assertTrue("Should parse to LocalServerNameAttribute", attribute instanceof LocalServerNameAttribute);
        Assert.assertEquals("toString should return long form", localServerNameStringAttribute, attribute.toString());

        // test short form
        localServerNameStringAttribute = "%v";
        attribute = parser.parse(localServerNameStringAttribute);
        Assert.assertTrue("Should parse to LocalServerNameAttribute", attribute instanceof LocalServerNameAttribute);
        Assert.assertEquals("toString should return long form", "%{LOCAL_SERVER_NAME}", attribute.toString());
    }

    /**
     * Test for RequestPathAttribute parser.
     * Verifies that the long form (%{REQUEST_PATH}) is correctly parsed.
     */
    @Test
    public void testRequestPathAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String requestPathStringAttribute = "%{REQUEST_PATH}";

        ExchangeAttribute attribute = parser.parse(requestPathStringAttribute);
        Assert.assertTrue("Should parse to RequestPathAttribute", attribute instanceof RequestPathAttribute);
        Assert.assertEquals("toString should match input", requestPathStringAttribute, attribute.toString());
    }

    /**
     * Test for ResolvedPathAttribute parser.
     * Verifies that the long form (%{RESOLVED_PATH}) is correctly parsed.
     */
    @Test
    public void testResolvedPathAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String resolvedPathStringAttribute = "%{RESOLVED_PATH}";

        ExchangeAttribute attribute = parser.parse(resolvedPathStringAttribute);
        Assert.assertTrue("Should parse to ResolvedPathAttribute", attribute instanceof ResolvedPathAttribute);
        Assert.assertEquals("toString should match input", resolvedPathStringAttribute, attribute.toString());
    }

    /**
     * Test for RequestSchemeAttribute parser.
     * Verifies that the long form (%{SCHEME}) is correctly parsed.
     */
    @Test
    public void testRequestSchemeAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String requestSchemeStringAttribute = "%{SCHEME}";

        ExchangeAttribute attribute = parser.parse(requestSchemeStringAttribute);
        Assert.assertTrue("Should parse to RequestSchemeAttribute", attribute instanceof RequestSchemeAttribute);
        Assert.assertEquals("toString should match input", requestSchemeStringAttribute, attribute.toString());
    }

    /**
     * Test for ResponseReasonPhraseAttribute parser.
     * Verifies that the long form (%{RESPONSE_REASON_PHRASE}) is correctly parsed.
     */
    @Test
    public void testResponseReasonPhraseAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String responseReasonPhraseStringAttribute = "%{RESPONSE_REASON_PHRASE}";

        ExchangeAttribute attribute = parser.parse(responseReasonPhraseStringAttribute);
        Assert.assertTrue("Should parse to ResponseReasonPhraseAttribute", attribute instanceof ResponseReasonPhraseAttribute);
        Assert.assertEquals("toString should match input", responseReasonPhraseStringAttribute, attribute.toString());
    }

    /**
     * Test for SecureExchangeAttribute parser.
     * Verifies that the long form (%{SECURE}) is correctly parsed.
     */
    @Test
    public void testSecureExchangeAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String secureExchangeStringAttribute = "%{SECURE}";

        ExchangeAttribute attribute = parser.parse(secureExchangeStringAttribute);
        Assert.assertTrue("Should parse to SecureExchangeAttribute", attribute instanceof SecureExchangeAttribute);
        Assert.assertEquals("toString should match input", secureExchangeStringAttribute, attribute.toString());
    }

    /**
     * Test for SecureProtocolAttribute parser.
     * Verifies that the long form (%{SECURE_PROTOCOL}) is correctly parsed.
     */
    @Test
    public void testSecureProtocolAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String secureProtocolStringAttribute = "%{SECURE_PROTOCOL}";

        ExchangeAttribute attribute = parser.parse(secureProtocolStringAttribute);
        Assert.assertTrue("Should parse to SecureProtocolAttribute", attribute instanceof SecureProtocolAttribute);
        Assert.assertEquals("toString should match input", secureProtocolStringAttribute, attribute.toString());
    }

    /**
     * Test for SslCipherAttribute parser.
     * Verifies that the long form (%{SSL_CIPHER}) is correctly parsed.
     */
    @Test
    public void testSslCipherAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String sslCipherStringAttribute = "%{SSL_CIPHER}";

        ExchangeAttribute attribute = parser.parse(sslCipherStringAttribute);
        Assert.assertTrue("Should parse to SslCipherAttribute", attribute instanceof SslCipherAttribute);
        Assert.assertEquals("toString should match input", sslCipherStringAttribute, attribute.toString());
    }

    /**
     * Test for SslClientCertAttribute parser.
     * Verifies that the long form (%{SSL_CLIENT_CERT}) is correctly parsed.
     */
    @Test
    public void testSslClientCertAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String sslClientCertStringAttribute = "%{SSL_CLIENT_CERT}";

        ExchangeAttribute attribute = parser.parse(sslClientCertStringAttribute);
        Assert.assertTrue("Should parse to SslClientCertAttribute", attribute instanceof SslClientCertAttribute);
        Assert.assertEquals("toString should match input", sslClientCertStringAttribute, attribute.toString());
    }

    /**
     * Test for SslSessionIdAttribute parser.
     * Verifies that the long form (%{SSL_SESSION_ID}) is correctly parsed.
     */
    @Test
    public void testSslSessionIdAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String sslSessionIdStringAttribute = "%{SSL_SESSION_ID}";

        ExchangeAttribute attribute = parser.parse(sslSessionIdStringAttribute);
        Assert.assertTrue("Should parse to SslSessionIdAttribute", attribute instanceof SslSessionIdAttribute);
        Assert.assertEquals("toString should match input", sslSessionIdStringAttribute, attribute.toString());
    }

    /**
     * Test for ThreadNameAttribute parser.
     * Verifies that both long form (%{THREAD_NAME}) and short form (%I) are correctly parsed.
     */
    @Test
    public void testThreadNameAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());

        // test long form
        String threadNameStringAttribute = "%{THREAD_NAME}";
        ExchangeAttribute attribute = parser.parse(threadNameStringAttribute);
        Assert.assertTrue("Should parse to ThreadNameAttribute", attribute instanceof ThreadNameAttribute);
        Assert.assertEquals("toString should return long form", threadNameStringAttribute, attribute.toString());

        // test short form
        threadNameStringAttribute = "%I";
        attribute = parser.parse(threadNameStringAttribute);
        Assert.assertTrue("Should parse to ThreadNameAttribute", attribute instanceof ThreadNameAttribute);
        Assert.assertEquals("toString should return long form", "%{THREAD_NAME}", attribute.toString());
    }

    /**
     * Test for TransportProtocolAttribute parser.
     * Verifies that the long form (%{TRANSPORT_PROTOCOL}) is correctly parsed.
     */
    @Test
    public void testTransportProtocolAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String transportProtocolStringAttribute = "%{TRANSPORT_PROTOCOL}";

        ExchangeAttribute attribute = parser.parse(transportProtocolStringAttribute);
        Assert.assertTrue("Should parse to TransportProtocolAttribute", attribute instanceof TransportProtocolAttribute);
        Assert.assertEquals("toString should match input", transportProtocolStringAttribute, attribute.toString());
    }

    /**
     * Test for NullAttribute parser.
     * Verifies that the long form (%{NULL}) is correctly parsed.
     */
    @Test
    public void testNullAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String nullStringAttribute = "%{NULL}";

        ExchangeAttribute attribute = parser.parse(nullStringAttribute);
        Assert.assertTrue("Should parse to NullAttribute", attribute instanceof NullAttribute);
        Assert.assertEquals("toString should match input", nullStringAttribute, attribute.toString());
    }

    /**
     * Test for ResponseTimeAttribute parser.
     * Verifies that various time unit forms (millis, seconds, micros, nanos) are correctly parsed.
     */
    @Test
    public void testResponseTimeAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());

        // test millis long form
        String responseTimeStringAttribute = "%{RESPONSE_TIME}";
        ExchangeAttribute attribute = parser.parse(responseTimeStringAttribute);
        Assert.assertTrue("Should parse to ResponseTimeAttribute", attribute instanceof ResponseTimeAttribute);
        Assert.assertEquals("toString should return long form", responseTimeStringAttribute, attribute.toString());

        // test millis short form
        responseTimeStringAttribute = "%D";
        attribute = parser.parse(responseTimeStringAttribute);
        Assert.assertTrue("Should parse to ResponseTimeAttribute", attribute instanceof ResponseTimeAttribute);
        Assert.assertEquals("toString should return long form", "%{RESPONSE_TIME}", attribute.toString());

        // test seconds short form
        responseTimeStringAttribute = "%T";
        attribute = parser.parse(responseTimeStringAttribute);
        Assert.assertTrue("Should parse to ResponseTimeAttribute", attribute instanceof ResponseTimeAttribute);
        Assert.assertEquals("toString should return seconds form", responseTimeStringAttribute, attribute.toString());

        // test micros
        responseTimeStringAttribute = "%{RESPONSE_TIME_MICROS}";
        attribute = parser.parse(responseTimeStringAttribute);
        Assert.assertTrue("Should parse to ResponseTimeAttribute", attribute instanceof ResponseTimeAttribute);
        Assert.assertEquals("toString should return micros form", responseTimeStringAttribute, attribute.toString());

        // test nanos
        responseTimeStringAttribute = "%{RESPONSE_TIME_NANOS}";
        attribute = parser.parse(responseTimeStringAttribute);
        Assert.assertTrue("Should parse to ResponseTimeAttribute", attribute instanceof ResponseTimeAttribute);
        Assert.assertEquals("toString should return nanos form", responseTimeStringAttribute, attribute.toString());
    }

    /**
     * Test for RequestHeaderAttribute parser.
     * Verifies that request header attributes (%{i,HeaderName}) are correctly parsed.
     */
    @Test
    public void testRequestHeaderAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String requestHeaderStringAttribute = "%{i,Content-Type}";

        // test with a 'Content-Type' header name
        ExchangeAttribute attribute = parser.parse(requestHeaderStringAttribute);
        Assert.assertTrue("Should parse to RequestHeaderAttribute", attribute instanceof RequestHeaderAttribute);
        Assert.assertEquals("toString should preserve header name", requestHeaderStringAttribute, attribute.toString());
    }

    /**
     * Test for ResponseHeaderAttribute parser.
     * Verifies that response header attributes (%{o,HeaderName}) are correctly parsed.
     */
    @Test
    public void testResponseHeaderAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String responseHeaderStringAttribute = "%{o,Content-Type}";

        // test with a 'Content-Type' header name
        ExchangeAttribute attribute = parser.parse(responseHeaderStringAttribute);
        Assert.assertTrue("Should parse to ResponseHeaderAttribute", attribute instanceof ResponseHeaderAttribute);
        Assert.assertEquals("toString should preserve header name", responseHeaderStringAttribute, attribute.toString());
    }

    /**
     * Test for CookieAttribute parser.
     * Verifies that cookie attributes (%{c,cookieName}) are correctly parsed.
     */
    @Test
    public void testCookieAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String cookieStringAttribute = "%{c,sessionId}";

        // test with a 'sessionId' cookie name
        ExchangeAttribute attribute = parser.parse(cookieStringAttribute);
        Assert.assertTrue("Should parse to CookieAttribute", attribute instanceof CookieAttribute);
        Assert.assertEquals("toString should preserve cookie name", cookieStringAttribute, attribute.toString());
    }

    /**
     * Test for RequestCookieAttribute parser.
     * Verifies that request cookie attributes (%{req-cookie,cookieName}) are correctly parsed.
     */
    @Test
    public void testRequestCookieAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String requestCookieStringAttribute = "%{req-cookie,sessionId}";

        // test with a 'sessionId' cookie name
        ExchangeAttribute attribute = parser.parse(requestCookieStringAttribute);
        Assert.assertTrue("Should parse to RequestCookieAttribute", attribute instanceof RequestCookieAttribute);
        Assert.assertEquals("toString should preserve cookie name", requestCookieStringAttribute, attribute.toString());
    }

    /**
     * Test for ResponseCookieAttribute parser.
     * Verifies that response cookie attributes (%{resp-cookie,cookieName}) are correctly parsed.
     */
    @Test
    public void testResponseCookieAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String responseCookieStringAttribute = "%{resp-cookie,sessionId}";

        // test with a 'sessionId' cookie name
        ExchangeAttribute attribute = parser.parse(responseCookieStringAttribute);
        Assert.assertTrue("Should parse to ResponseCookieAttribute", attribute instanceof ResponseCookieAttribute);
        Assert.assertEquals("toString should preserve cookie name", responseCookieStringAttribute, attribute.toString());
    }

    /**
     * Test for QueryParameterAttribute parser.
     * Verifies that query parameter attributes (%{q,paramName}) are correctly parsed.
     */
    @Test
    public void testQueryParameterAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String queryParameterStringAttribute = "%{q,param1}";

        // test with a 'param1' parameter name
        ExchangeAttribute attribute = parser.parse(queryParameterStringAttribute);
        Assert.assertTrue("Should parse to QueryParameterAttribute", attribute instanceof QueryParameterAttribute);
        Assert.assertEquals("toString should preserve parameter name", queryParameterStringAttribute, attribute.toString());
    }

    /**
     * Test for PathParameterAttribute parser.
     * Verifies that path parameter attributes (%{p,paramName}) are correctly parsed.
     */
    @Test
    public void testPathParameterAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String pathParameterStringAttribute = "%{p,id}";

        // test with an 'id' parameter name
        ExchangeAttribute attribute = parser.parse(pathParameterStringAttribute);
        Assert.assertTrue("Should parse to PathParameterAttribute", attribute instanceof PathParameterAttribute);
        Assert.assertEquals("toString should preserve parameter name", pathParameterStringAttribute, attribute.toString());
    }

    /**
     * Test for PredicateContextAttribute parser.
     * Verifies that predicate context attributes (${contextKey}) are correctly parsed.
     */
    @Test
    public void testPredicateContextAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());
        String predicateContextStringAttribute = "${contextKey}";

        ExchangeAttribute attribute = parser.parse(predicateContextStringAttribute);
        Assert.assertTrue("Should parse to PredicateContextAttribute", attribute instanceof PredicateContextAttribute);
        Assert.assertEquals("toString should preserve context key", predicateContextStringAttribute, attribute.toString());
    }

    /**
     * Test for ConstantExchangeAttribute parser.
     * Verifies that literal text, escaped characters, and unknown tokens are correctly parsed as constants.
     */
    @Test
    public void testConstantAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());

        // test literal text
        String constantStringAttribute = "literal text";
        ExchangeAttribute attribute = parser.parse(constantStringAttribute);
        Assert.assertTrue("Should parse to ConstantExchangeAttribute", attribute instanceof ConstantExchangeAttribute);
        Assert.assertEquals("toString should return literal text", constantStringAttribute, attribute.toString());

        // test escaped percent
        constantStringAttribute = "%%";
        attribute = parser.parse(constantStringAttribute);
        Assert.assertTrue("Should parse to ConstantExchangeAttribute", attribute instanceof ConstantExchangeAttribute);
        Assert.assertEquals("toString should return single percent", "%", attribute.toString());

        // test unknown token (should fall back to constant)
        constantStringAttribute = "%{UNKNOWN_TOKEN}";
        attribute = parser.parse(constantStringAttribute);
        Assert.assertTrue("Unknown token should parse to ConstantExchangeAttribute", attribute instanceof ConstantExchangeAttribute);
        Assert.assertEquals("toString should return unknown token as-is", constantStringAttribute, attribute.toString());
    }

    /**
     * Test for CompositeExchangeAttribute parser.
     * Verifies that multiple attributes combined in a single string are correctly parsed as a composite.
     */
    @Test
    public void testCompositeAttribute() {
        ExchangeAttributeParser parser = new ExchangeAttributeParser(ExchangeAttributeParserTest.class.getClassLoader(), Collections.emptyList());

        // test multiple attributes
        String compositeStringAttribute = "%m %U %s";
        ExchangeAttribute attribute = parser.parse(compositeStringAttribute);
        Assert.assertTrue("Should parse to CompositeExchangeAttribute", attribute instanceof CompositeExchangeAttribute);
        // composite toString concatenates the toString of each attribute
        Assert.assertEquals("toString should concatenate attributes", "%{METHOD} %{REQUEST_URL} %{RESPONSE_CODE}", attribute.toString());

        // test mixed literal and attributes
        compositeStringAttribute = "Method: %m, Path: %U";
        attribute = parser.parse(compositeStringAttribute);
        Assert.assertTrue("Should parse to CompositeExchangeAttribute", attribute instanceof CompositeExchangeAttribute);
        Assert.assertEquals("toString should preserve literals and attributes", "Method: %{METHOD}, Path: %{REQUEST_URL}", attribute.toString());
    }

}
