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
}
