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

package io.undertow.util;

import io.undertow.server.handlers.Cookie;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Stuart Douglas
 */
public class CookiesTestCase {

    @Test
    public void testParsingSetCookieHeaderV0() {

        Cookie cookie = Cookies.parseSetCookieHeader("CUSTOMER=WILE_E_COYOTE; path=/; expires=Wednesday, 09-Nov-99 23:12:40 GMT");
        Assert.assertEquals("CUSTOMER", cookie.getName());
        Assert.assertEquals("WILE_E_COYOTE", cookie.getValue());
        Assert.assertEquals("/", cookie.getPath());
        Assert.assertEquals(date(1999, 11, 9, 23, 12, 40), cookie.getExpires());


        cookie = Cookies.parseSetCookieHeader("SHIPPING=FEDEX; path=/foo; secure");
        Assert.assertEquals("SHIPPING", cookie.getName());
        Assert.assertEquals("FEDEX", cookie.getValue());
        Assert.assertEquals("/foo", cookie.getPath());
        Assert.assertTrue(cookie.isSecure());

        cookie = Cookies.parseSetCookieHeader("SHIPPING=FEDEX");
        Assert.assertEquals("SHIPPING", cookie.getName());
        Assert.assertEquals("FEDEX", cookie.getValue());
    }

    @Test
    public void testParsingSetCookieHeaderV1() {
        Cookie cookie = Cookies.parseSetCookieHeader("Customer=\"WILE_E_COYOTE\"; Version=\"1\"; Path=\"/acme\"");
        Assert.assertEquals("Customer", cookie.getName());
        Assert.assertEquals("WILE_E_COYOTE", cookie.getValue());
        Assert.assertEquals("/acme", cookie.getPath());
        Assert.assertEquals(1, cookie.getVersion());


        cookie = Cookies.parseSetCookieHeader("SHIPPING=\"FEDEX\"; path=\"/foo\"; secure; Version=\"1\";");
        Assert.assertEquals("SHIPPING", cookie.getName());
        Assert.assertEquals("FEDEX", cookie.getValue());
        Assert.assertEquals("/foo", cookie.getPath());
        Assert.assertTrue(cookie.isSecure());
        Assert.assertEquals(1, cookie.getVersion());
    }

    private static Date date(int year, int month, int day, int hour, int minute, int second) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.MILLISECOND, 0);
        c.setTimeZone(TimeZone.getTimeZone("GMT"));
        c.set(year, month-1, day, hour, minute, second);
        return c.getTime();
    }

    @Test
    public void testInvalidCookie() {
        Map<String, Cookie> cookies = Cookies.parseRequestCookies(1, false, Arrays.asList("\"; CUSTOMER=WILE_E_COYOTE"));

        Assert.assertFalse(cookies.containsKey("$Domain"));
        Assert.assertFalse(cookies.containsKey("$Version"));
        Assert.assertFalse(cookies.containsKey("$Path"));

        Cookie cookie = cookies.get("CUSTOMER");
        Assert.assertEquals("CUSTOMER", cookie.getName());
        Assert.assertEquals("WILE_E_COYOTE", cookie.getValue());

        cookies = Cookies.parseRequestCookies(1, false, Arrays.asList("; CUSTOMER=WILE_E_COYOTE"));

        cookie = cookies.get("CUSTOMER");
        Assert.assertEquals("CUSTOMER", cookie.getName());
        Assert.assertEquals("WILE_E_COYOTE", cookie.getValue());

        cookies = Cookies.parseRequestCookies(1, false, Arrays.asList("foobar; CUSTOMER=WILE_E_COYOTE"));

        cookie = cookies.get("CUSTOMER");
        Assert.assertEquals("CUSTOMER", cookie.getName());
        Assert.assertEquals("WILE_E_COYOTE", cookie.getValue());
    }
    @Test
    public void testRequestCookieDomainPathVersion() {
        Map<String, Cookie> cookies = Cookies.parseRequestCookies(1, false, Arrays.asList(
                "CUSTOMER=WILE_E_COYOTE; $Domain=LOONEY_TUNES; $Version=1; $Path=/"));

        Assert.assertFalse(cookies.containsKey("$Domain"));
        Assert.assertFalse(cookies.containsKey("$Version"));
        Assert.assertFalse(cookies.containsKey("$Path"));

        Cookie cookie = cookies.get("CUSTOMER");
        Assert.assertEquals("CUSTOMER", cookie.getName());
        Assert.assertEquals("WILE_E_COYOTE", cookie.getValue());
        Assert.assertEquals("LOONEY_TUNES", cookie.getDomain());
        Assert.assertEquals(1, cookie.getVersion());
        Assert.assertEquals("/", cookie.getPath());
    }

    @Test
    public void testMultipleRequestCookies() {
        Map<String, Cookie> cookies = Cookies.parseRequestCookies(2, false, Arrays.asList(
                "CUSTOMER=WILE_E_COYOTE; $Domain=LOONEY_TUNES; $Version=1; $Path=/; SHIPPING=FEDEX"));

        Cookie cookie = cookies.get("CUSTOMER");
        Assert.assertEquals("CUSTOMER", cookie.getName());
        Assert.assertEquals("WILE_E_COYOTE", cookie.getValue());
        Assert.assertEquals("LOONEY_TUNES", cookie.getDomain());
        Assert.assertEquals(1, cookie.getVersion());
        Assert.assertEquals("/", cookie.getPath());

        cookie = cookies.get("SHIPPING");
        Assert.assertEquals("SHIPPING", cookie.getName());
        Assert.assertEquals("FEDEX", cookie.getValue());
        Assert.assertEquals("LOONEY_TUNES", cookie.getDomain());
        Assert.assertEquals(1, cookie.getVersion());
        Assert.assertEquals("/", cookie.getPath());
    }

    @Test
    public void testEqualsInValueNotAllowed() {
        Map<String, Cookie> cookies = Cookies.parseRequestCookies(2, false, Arrays.asList("CUSTOMER=WILE_E_COYOTE=THE_COYOTE; SHIPPING=FEDEX"));
        Cookie cookie = cookies.get("CUSTOMER");
        Assert.assertNotNull(cookie);
        Assert.assertEquals("WILE_E_COYOTE", cookie.getValue());
        cookie = cookies.get("SHIPPING");
        Assert.assertNotNull(cookie);
        Assert.assertEquals("FEDEX", cookie.getValue());
    }

    @Test
    public void testEmptyCookieNames() {
        Map<String, Cookie> cookies = Cookies.parseRequestCookies(4, false, Arrays.asList("=foo; CUSTOMER=WILE_E_COYOTE=THE_COYOTE; =foobar; SHIPPING=FEDEX; =bar"));
        Cookie cookie = cookies.get("CUSTOMER");
        Assert.assertNotNull(cookie);
        Assert.assertEquals("WILE_E_COYOTE", cookie.getValue());
        cookie = cookies.get("SHIPPING");
        Assert.assertNotNull(cookie);
        Assert.assertEquals("FEDEX", cookie.getValue());
        cookie = cookies.get("");
        Assert.assertNotNull(cookie);
        Assert.assertEquals("foo", cookie.getValue());
    }

    @Test
    public void testEqualsInValueAllowed() {
        Map<String, Cookie> cookies = Cookies.parseRequestCookies(1, true, Arrays.asList("CUSTOMER=WILE_E_COYOTE=THE_COYOTE"));
        Cookie cookie = cookies.get("CUSTOMER");
        Assert.assertNotNull(cookie);
        Assert.assertEquals("WILE_E_COYOTE=THE_COYOTE", cookie.getValue());
    }

    @Test
    public void testEqualsInValueAllowedInQuotedValue() {
        Map<String, Cookie> cookies = Cookies.parseRequestCookies(2, true, Arrays.asList("CUSTOMER=\"WILE_E_COYOTE=THE_COYOTE\"; SHIPPING=FEDEX" ));
        Assert.assertEquals(2, cookies.size());
        Cookie cookie = cookies.get("CUSTOMER");
        Assert.assertNotNull(cookie);
        Assert.assertEquals("WILE_E_COYOTE=THE_COYOTE", cookie.getValue());
        cookie = cookies.get("SHIPPING");
        Assert.assertNotNull(cookie);
        Assert.assertEquals("FEDEX", cookie.getValue());
    }

    @Test
    public void testEqualsInValueNotAllowedInQuotedValue() {
        Map<String, Cookie> cookies = Cookies.parseRequestCookies(2, false, Arrays.asList("CUSTOMER=\"WILE_E_COYOTE=THE_COYOTE\"; SHIPPING=FEDEX" ));
        Assert.assertEquals(2, cookies.size());
        Cookie cookie = cookies.get("CUSTOMER");
        Assert.assertNotNull(cookie);
        Assert.assertEquals("WILE_E_COYOTE=THE_COYOTE", cookie.getValue());
        cookie = cookies.get("SHIPPING");
        Assert.assertNotNull(cookie);
        Assert.assertEquals("FEDEX", cookie.getValue());
    }

    @Test
    public void testSimpleJSONObjectInRequestCookies() {
        Map<String, Cookie> cookies = Cookies.parseRequestCookies(2, true, Arrays.asList(
                "CUSTOMER={\"v1\":1, \"id\":\"some_unique_id\", \"c\":\"http://www.google.com?q=love me\"};"
                + " $Domain=LOONEY_TUNES; $Version=1; $Path=/; SHIPPING=FEDEX"));

        Cookie cookie = cookies.get("CUSTOMER");
        Assert.assertEquals("CUSTOMER", cookie.getName());
        Assert.assertEquals("{\"v1\":1, \"id\":\"some_unique_id\", \"c\":\"http://www.google.com?q=love me\"}",
               cookie.getValue());
        Assert.assertEquals("LOONEY_TUNES", cookie.getDomain());
        Assert.assertEquals(1, cookie.getVersion());
        Assert.assertEquals("/", cookie.getPath());

        cookie = cookies.get("SHIPPING");
        Assert.assertEquals("SHIPPING", cookie.getName());
        Assert.assertEquals("FEDEX", cookie.getValue());
        Assert.assertEquals("LOONEY_TUNES", cookie.getDomain());
        Assert.assertEquals(1, cookie.getVersion());
        Assert.assertEquals("/", cookie.getPath());
    }

    @Test
    public void testComplexJSONObjectInRequestCookies() {
        Map<String, Cookie> cookies = Cookies.parseRequestCookies(2, false, Arrays.asList(
                "CUSTOMER={ \"accounting\" : [ { \"firstName\" : \"John\", \"lastName\" : \"Doe\", \"age\" : 23 },"
                + " { \"firstName\" : \"Mary\",  \"lastName\" : \"Smith\", \"age\" : 32 }], "
                + "\"sales\" : [ { \"firstName\" : \"Sally\", \"lastName\" : \"Green\", \"age\" : 27 }, "
                + "{ \"firstName\" : \"Jim\", \"lastName\" : \"Galley\", \"age\" : 41 } ] };"
                + " $Domain=LOONEY_TUNES; $Version=1; $Path=/; SHIPPING=FEDEX"));

        Cookie cookie = cookies.get("CUSTOMER");
        Assert.assertEquals("CUSTOMER", cookie.getName());
        Assert.assertEquals("{ \"accounting\" : [ { \"firstName\" : \"John\", \"lastName\" : \"Doe\", \"age\" : 23 },"
                + " { \"firstName\" : \"Mary\",  \"lastName\" : \"Smith\", \"age\" : 32 }], "
                + "\"sales\" : [ { \"firstName\" : \"Sally\", \"lastName\" : \"Green\", \"age\" : 27 }, "
                + "{ \"firstName\" : \"Jim\", \"lastName\" : \"Galley\", \"age\" : 41 } ] }",
               cookie.getValue());
        Assert.assertEquals("LOONEY_TUNES", cookie.getDomain());
        Assert.assertEquals(1, cookie.getVersion());
        Assert.assertEquals("/", cookie.getPath());

        cookie = cookies.get("SHIPPING");
        Assert.assertEquals("SHIPPING", cookie.getName());
        Assert.assertEquals("FEDEX", cookie.getValue());
        Assert.assertEquals("LOONEY_TUNES", cookie.getDomain());
        Assert.assertEquals(1, cookie.getVersion());
        Assert.assertEquals("/", cookie.getPath());
    }
}
