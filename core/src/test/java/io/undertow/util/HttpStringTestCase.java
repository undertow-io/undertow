package io.undertow.util;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Matej Lazar
 */
public class HttpStringTestCase {

    @Test
    public void testOrderShorterFirst() {
        HttpString a =  new HttpString("a");
        HttpString aa =  new HttpString("aa");
        Assert.assertEquals(-1, a.compareTo(aa));
    }

    /**
     * test HttpString.compareTo part: bytes.length - other.bytes.length
     */
    @Test
    public void testCompareShorterFirst() {
        HttpString accept =  new HttpString(Headers.ACCEPT_STRING);
        Assert.assertEquals(accept.compareTo(Headers.ACCEPT_CHARSET), Headers.ACCEPT.compareTo(Headers.ACCEPT_CHARSET));

        HttpString acceptCharset =  new HttpString(Headers.ACCEPT_CHARSET_STRING);
        Assert.assertEquals(acceptCharset.compareTo(Headers.ACCEPT), Headers.ACCEPT_CHARSET.compareTo(Headers.ACCEPT));
    }

    /**
     * test HttpString.compareTo part: res = signum(higher(bytes[i]) - higher(other.bytes[i]));
     */
    @Test
    public void testCompare() {
        HttpString contentType =  new HttpString(Headers.CONTENT_TYPE_STRING);
        Assert.assertEquals(contentType.compareTo(Headers.COOKIE), Headers.CONTENT_TYPE.compareTo(Headers.COOKIE));

        HttpString cookie =  new HttpString(Headers.COOKIE_STRING);
        Assert.assertEquals(cookie.compareTo(Headers.CONTENT_TYPE), Headers.COOKIE.compareTo(Headers.CONTENT_TYPE));
    }

}
