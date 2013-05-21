package io.undertow.server.handlers;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for peer security handler
 *
 * @author Stuart Douglas
 */
public class IPAddressAccessControlHandlerUnitTestCase {

    @Test
    public void testIPv4ExactMatch() throws UnknownHostException {
        IPAddressAccessControlHandler handler = new IPAddressAccessControlHandler()
                .setDefaultAllow(false)
                .addAllow("127.0.0.1");
        Assert.assertTrue(handler.isAllowed(InetAddress.getByName("127.0.0.1")));
        Assert.assertFalse(handler.isAllowed(InetAddress.getByName("127.0.0.2")));
    }

    @Test
    public void testIPv6ExactMatch() throws UnknownHostException {
        IPAddressAccessControlHandler handler = new IPAddressAccessControlHandler()
                .setDefaultAllow(false)
                .addAllow("FE45:00:00:000:0:AAA:FFFF:0045");
        Assert.assertTrue(handler.isAllowed(InetAddress.getByName("FE45:0:0:0:0:AAA:FFFF:45")));
        Assert.assertFalse(handler.isAllowed(InetAddress.getByName("127.0.0.2")));
        Assert.assertFalse(handler.isAllowed(InetAddress.getByName("FE45:0:0:0:0:AAA:FFFF:46")));
    }

    @Test
    public void testIPv4WildcardMatch() throws UnknownHostException {
        IPAddressAccessControlHandler handler = new IPAddressAccessControlHandler()
                .setDefaultAllow(true)
                .addAllow("127.0.0.1")
                .addDeny("127.0.*.*");
        Assert.assertTrue(handler.isAllowed(InetAddress.getByName("127.0.0.1")));
        Assert.assertFalse(handler.isAllowed(InetAddress.getByName("127.0.0.2")));
        Assert.assertTrue(handler.isAllowed(InetAddress.getByName("127.1.0.2")));
    }

    @Test
    public void testIPv6PrefixMatch() throws UnknownHostException {
        IPAddressAccessControlHandler handler = new IPAddressAccessControlHandler()
                .setDefaultAllow(true)
                .addAllow("FE45:00:00:000:0:AAA:FFFF:0045")
                .addDeny("FE45:00:00:000:0:AAA:FFFF:*");
        Assert.assertTrue(handler.isAllowed(InetAddress.getByName("FE45:0:0:0:0:AAA:FFFF:45")));
        Assert.assertTrue(handler.isAllowed(InetAddress.getByName("127.0.0.2")));
        Assert.assertFalse(handler.isAllowed(InetAddress.getByName("FE45:0:0:0:0:AAA:FFFF:46")));
        Assert.assertTrue(handler.isAllowed(InetAddress.getByName("FE45:0:0:0:0:AAA:FFFb:46")));
    }

    @Test
    public void testIPv4SlashMatch() throws UnknownHostException {
        IPAddressAccessControlHandler handler = new IPAddressAccessControlHandler()
                .setDefaultAllow(true)
                .addAllow("127.0.0.1")
                .addAllow("127.0.0.48/30")
                .addDeny("127.0.0.0/16");
        Assert.assertTrue(handler.isAllowed(InetAddress.getByName("127.0.0.1")));
        Assert.assertFalse(handler.isAllowed(InetAddress.getByName("127.0.0.2")));
        Assert.assertFalse(handler.isAllowed(InetAddress.getByName("127.0.1.1")));
        Assert.assertTrue(handler.isAllowed(InetAddress.getByName("127.1.0.2")));
        Assert.assertFalse(handler.isAllowed(InetAddress.getByName("127.0.0.47")));
        Assert.assertTrue(handler.isAllowed(InetAddress.getByName("127.0.0.48")));
        Assert.assertTrue(handler.isAllowed(InetAddress.getByName("127.0.0.49")));
        Assert.assertTrue(handler.isAllowed(InetAddress.getByName("127.0.0.50")));
        Assert.assertTrue(handler.isAllowed(InetAddress.getByName("127.0.0.51")));
        Assert.assertFalse(handler.isAllowed(InetAddress.getByName("127.0.0.52")));
    }



    @Test
    public void testIPv6SlashMatch() throws UnknownHostException {
        IPAddressAccessControlHandler handler = new IPAddressAccessControlHandler()
                .setDefaultAllow(true)
                .addAllow("FE45:00:00:000:0:AAA:FFFF:0045")
                .addAllow("FE45:00:00:000:0:AAA:FFFF:01F4/127")
                .addDeny("FE45:00:00:000:0:AAA:FFFF:0/112");
        Assert.assertTrue(handler.isAllowed(InetAddress.getByName("FE45:0:0:0:0:AAA:FFFF:45")));
        Assert.assertTrue(handler.isAllowed(InetAddress.getByName("127.0.0.2")));
        Assert.assertFalse(handler.isAllowed(InetAddress.getByName("FE45:0:0:0:0:AAA:FFFF:46")));
        Assert.assertTrue(handler.isAllowed(InetAddress.getByName("FE45:0:0:0:0:AAA:FFFb:46")));

        Assert.assertFalse(handler.isAllowed(InetAddress.getByName("fe45:0000:0000:0000:0000:0aaa:ffff:01f3")));
        Assert.assertTrue(handler.isAllowed(InetAddress.getByName("fe45:0000:0000:0000:0000:0aaa:ffff:01f4")));
        Assert.assertTrue(handler.isAllowed(InetAddress.getByName("fe45:0000:0000:0000:0000:0aaa:ffff:01f5")));
        Assert.assertFalse(handler.isAllowed(InetAddress.getByName("fe45:0000:0000:0000:0000:0aaa:ffff:01f6")));
    }
}
