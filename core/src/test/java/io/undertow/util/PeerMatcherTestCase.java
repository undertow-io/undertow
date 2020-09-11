package io.undertow.util;

import io.undertow.testutils.category.UnitTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

@Category(UnitTest.class)
public class PeerMatcherTestCase {

    @Test
    public void testIPv4ExactMatchSocketAddress() throws UnknownHostException {
        PeerMatcher matcher = new PeerMatcher()
                .setDefaultAllow(false)
                .addAllow("127.0.0.1");

        Assert.assertTrue(matcher.isAllowed(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0)));
        Assert.assertFalse(matcher.isAllowed(new InetSocketAddress(InetAddress.getByName("127.0.0.2"), 0)));
    }

    @Test
    public void testIPv4ExactMatch() throws UnknownHostException {
        PeerMatcher matcher = new PeerMatcher()
                .setDefaultAllow(false)
                .addAllow("127.0.0.1");

        Assert.assertTrue(matcher.isAllowed(InetAddress.getByName("127.0.0.1")));
        Assert.assertFalse(matcher.isAllowed(InetAddress.getByName("127.0.0.2")));
    }

    @Test
    public void testIPv6ExactMatch() throws UnknownHostException {
        PeerMatcher matcher = new PeerMatcher()
                .setDefaultAllow(false)
                .addAllow("FE45:00:00:000:0:AAA:FFFF:0045");
        Assert.assertTrue(matcher.isAllowed(InetAddress.getByName("FE45:0:0:0:0:AAA:FFFF:45")));
        Assert.assertFalse(matcher.isAllowed(InetAddress.getByName("127.0.0.2")));
        Assert.assertFalse(matcher.isAllowed(InetAddress.getByName("FE45:0:0:0:0:AAA:FFFF:46")));
    }

    @Test
    public void testIPv4WildcardMatch() throws UnknownHostException {
        PeerMatcher matcher = new PeerMatcher()
                .setDefaultAllow(true)
                .addAllow("127.0.0.1")
                .addDeny("127.0.*.*");
        Assert.assertTrue(matcher.isAllowed(InetAddress.getByName("127.0.0.1")));
        Assert.assertFalse(matcher.isAllowed(InetAddress.getByName("127.0.0.2")));
        Assert.assertTrue(matcher.isAllowed(InetAddress.getByName("127.1.0.2")));
    }

    @Test
    public void testIPv6PrefixMatch() throws UnknownHostException {
        PeerMatcher matcher = new PeerMatcher()
                .setDefaultAllow(true)
                .addAllow("FE45:00:00:000:0:AAA:FFFF:0045")
                .addDeny("FE45:00:00:000:0:AAA:FFFF:*");
        Assert.assertTrue(matcher.isAllowed(InetAddress.getByName("FE45:0:0:0:0:AAA:FFFF:45")));
        Assert.assertTrue(matcher.isAllowed(InetAddress.getByName("127.0.0.2")));
        Assert.assertFalse(matcher.isAllowed(InetAddress.getByName("FE45:0:0:0:0:AAA:FFFF:46")));
        Assert.assertTrue(matcher.isAllowed(InetAddress.getByName("FE45:0:0:0:0:AAA:FFFb:46")));
    }

    @Test
    public void testIPv4SlashMatch() throws UnknownHostException {
        PeerMatcher matcher = new PeerMatcher()
                .setDefaultAllow(true)
                .addAllow("127.0.0.1")
                .addAllow("127.0.0.48/30")
                .addDeny("127.0.0.0/16");
        Assert.assertTrue(matcher.isAllowed(InetAddress.getByName("127.0.0.1")));
        Assert.assertFalse(matcher.isAllowed(InetAddress.getByName("127.0.0.2")));
        Assert.assertFalse(matcher.isAllowed(InetAddress.getByName("127.0.1.1")));
        Assert.assertTrue(matcher.isAllowed(InetAddress.getByName("127.1.0.2")));
        Assert.assertFalse(matcher.isAllowed(InetAddress.getByName("127.0.0.47")));
        Assert.assertTrue(matcher.isAllowed(InetAddress.getByName("127.0.0.48")));
        Assert.assertTrue(matcher.isAllowed(InetAddress.getByName("127.0.0.49")));
        Assert.assertTrue(matcher.isAllowed(InetAddress.getByName("127.0.0.50")));
        Assert.assertTrue(matcher.isAllowed(InetAddress.getByName("127.0.0.51")));
        Assert.assertFalse(matcher.isAllowed(InetAddress.getByName("127.0.0.52")));
    }


    @Test
    public void testIPv6SlashMatch() throws UnknownHostException {
        PeerMatcher matcher = new PeerMatcher()
                .setDefaultAllow(true)
                .addAllow("FE45:00:00:000:0:AAA:FFFF:0045")
                .addAllow("FE45:00:00:000:0:AAA:FFFF:01F4/127")
                .addDeny("FE45:00:00:000:0:AAA:FFFF:0/112");
        runIpv6SlashMatchTest(matcher);
    }

    private void runIpv6SlashMatchTest(PeerMatcher matcher) throws UnknownHostException {
        Assert.assertTrue(matcher.isAllowed(InetAddress.getByName("FE45:0:0:0:0:AAA:FFFF:45")));
        Assert.assertTrue(matcher.isAllowed(InetAddress.getByName("127.0.0.2")));
        Assert.assertFalse(matcher.isAllowed(InetAddress.getByName("FE45:0:0:0:0:AAA:FFFF:46")));
        Assert.assertTrue(matcher.isAllowed(InetAddress.getByName("FE45:0:0:0:0:AAA:FFFb:46")));

        Assert.assertFalse(matcher.isAllowed(InetAddress.getByName("fe45:0000:0000:0000:0000:0aaa:ffff:01f3")));
        Assert.assertTrue(matcher.isAllowed(InetAddress.getByName("fe45:0000:0000:0000:0000:0aaa:ffff:01f4")));
        Assert.assertTrue(matcher.isAllowed(InetAddress.getByName("fe45:0000:0000:0000:0000:0aaa:ffff:01f5")));
        Assert.assertFalse(matcher.isAllowed(InetAddress.getByName("fe45:0000:0000:0000:0000:0aaa:ffff:01f6")));
    }
}
