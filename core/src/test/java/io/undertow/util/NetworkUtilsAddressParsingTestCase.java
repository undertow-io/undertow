package io.undertow.util;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * verifies that the proxy protocol ip address parser correctly parses IP addresses as per the additional requirements
 * in the proxy protocol spec
 *
 * @author Stuart Douglas
 */
public class NetworkUtilsAddressParsingTestCase {

    @Test
    public void testIpV4Address() throws IOException {
        InetAddress res = NetworkUtils.parseIpv4Address("1.123.255.2");
        Assert.assertTrue(res instanceof Inet4Address);
        Assert.assertEquals(1, res.getAddress()[0]);
        Assert.assertEquals(123, res.getAddress()[1]);
        Assert.assertEquals((byte)255, res.getAddress()[2]);
        Assert.assertEquals(2, res.getAddress()[3]);
        Assert.assertEquals("/1.123.255.2", res.toString());

        res = NetworkUtils.parseIpv4Address("127.0.0.1");
        Assert.assertTrue(res instanceof Inet4Address);
        Assert.assertEquals(127, res.getAddress()[0]);
        Assert.assertEquals(0, res.getAddress()[1]);
        Assert.assertEquals((byte)0, res.getAddress()[2]);
        Assert.assertEquals(1, res.getAddress()[3]);
        Assert.assertEquals("/127.0.0.1", res.toString());
    }

    @Test(expected = IOException.class)
    public void testIpV4AddressWithLeadingZero() throws IOException {
        NetworkUtils.parseIpv4Address("01.123.255.2");
    }

    @Test(expected = IOException.class)
    public void testIpV4AddressToSmall() throws IOException {
        NetworkUtils.parseIpv4Address("01.123.255");
    }

    @Test(expected = IOException.class)
    public void testIpV4AddressToLarge() throws IOException {
        NetworkUtils.parseIpv4Address("01.123.255.1.1");
    }

    @Test(expected = IOException.class)
    public void testIpV4AddressMultipleDots() throws IOException {
        NetworkUtils.parseIpv4Address("1..255.2");
    }

    @Test(expected = IOException.class)
    public void testIpV4AddressMultipleDots2() throws IOException {
        NetworkUtils.parseIpv4Address("1..3.255.2");
    }

    @Test(expected = IOException.class)
    public void testIpV4Hostname() throws IOException {
        NetworkUtils.parseIpv4Address("localhost");
    }

    @Test(expected = IOException.class)
    public void testIpV4Hostname2() throws IOException {
        NetworkUtils.parseIpv4Address("ff");
    }
    @Test(expected = IOException.class)
    public void testIpV4AddressStartsWithDot() throws IOException {
        NetworkUtils.parseIpv4Address(".1.123.255.2");
    }

    @Test
    public void testIpv6Address() throws IOException {
        String addressString = "2001:1db8:100:3:6:ff00:42:8329";
        InetAddress res = NetworkUtils.parseIpv6Address(addressString);
        Assert.assertTrue(res instanceof Inet6Address);

        int[] parts = {0x2001, 0x1db8, 0x100, 0x3, 0x6, 0xff00, 0x42, 0x8329};
        for(int i = 0 ; i < parts.length; ++i) {
            Assert.assertEquals(((byte)(parts[i]>>8)), res.getAddress()[i * 2]);
            Assert.assertEquals(((byte)(parts[i])), res.getAddress()[i * 2 + 1]);
        }
        Assert.assertEquals("/" + addressString, res.toString());

        addressString = "2001:1db8:100::6:ff00:42:8329";
        res = NetworkUtils.parseIpv6Address(addressString);
        Assert.assertTrue(res instanceof Inet6Address);

        parts = new int[]{0x2001, 0x1db8, 0x100, 0x0, 0x6, 0xff00, 0x42, 0x8329};
        for(int i = 0 ; i < parts.length; ++i) {
            Assert.assertEquals(((byte)(parts[i]>>8)), res.getAddress()[i * 2]);
            Assert.assertEquals(((byte)(parts[i])), res.getAddress()[i * 2 + 1]);
        }
        Assert.assertEquals("/2001:1db8:100:0:6:ff00:42:8329", res.toString());

        addressString = "2001:1db8:100::ff00:42:8329";
        res = NetworkUtils.parseIpv6Address(addressString);
        Assert.assertTrue(res instanceof Inet6Address);

        parts = new int[]{0x2001, 0x1db8, 0x100, 0x0, 0x0, 0xff00, 0x42, 0x8329};
        for(int i = 0 ; i < parts.length; ++i) {
            Assert.assertEquals(((byte)(parts[i]>>8)), res.getAddress()[i * 2]);
            Assert.assertEquals(((byte)(parts[i])), res.getAddress()[i * 2 + 1]);
        }
        Assert.assertEquals("/2001:1db8:100:0:0:ff00:42:8329", res.toString());

        addressString = "2001:1db8:0100:0000:0000:ff00:0042:8329";
        res = NetworkUtils.parseIpv6Address(addressString);
        Assert.assertTrue(res instanceof Inet6Address);

        parts = new int[]{0x2001, 0x1db8, 0x100, 0x0, 0x0, 0xff00, 0x42, 0x8329};
        for(int i = 0 ; i < parts.length; ++i) {
            Assert.assertEquals(((byte)(parts[i]>>8)), res.getAddress()[i * 2]);
            Assert.assertEquals(((byte)(parts[i])), res.getAddress()[i * 2 + 1]);
        }
        Assert.assertEquals("/2001:1db8:100:0:0:ff00:42:8329", res.toString());

        addressString = "::1";
        res = NetworkUtils.parseIpv6Address(addressString);
        Assert.assertTrue(res instanceof Inet6Address);

        parts = new int[]{0, 0, 0, 0, 0, 0, 0, 0x1};
        for(int i = 0 ; i < parts.length; ++i) {
            Assert.assertEquals(((byte)(parts[i]>>8)), res.getAddress()[i * 2]);
            Assert.assertEquals(((byte)(parts[i])), res.getAddress()[i * 2 + 1]);
        }
        Assert.assertEquals("/0:0:0:0:0:0:0:1", res.toString());
    }

    @Test(expected = IOException.class)
    public void testIpV6AddressToSmall() throws IOException {
        NetworkUtils.parseIpv6Address("2001:1db8:3:6:ff00:42:8329");
    }

    @Test(expected = IOException.class)
    public void testIpV6AddressToLarge() throws IOException {
        NetworkUtils.parseIpv6Address("2001:1db8:100:3:6:7:ff00:42:8329");
    }

    @Test(expected = IOException.class)
    public void testIpV6AddressMultipleColons() throws IOException {
        NetworkUtils.parseIpv6Address("2001:1db8:100::3:6:ff00:42:8329");
    }

    @Test(expected = IOException.class)
    public void testIpV6AddressMultipleColons2() throws IOException {
        NetworkUtils.parseIpv6Address("2001::100::329");
    }

    @Test(expected = IOException.class)
    public void testIpV6Hostname() throws IOException {
        NetworkUtils.parseIpv6Address("localhost");
    }

    @Test(expected = IOException.class)
    public void testIpV6Hostname2() throws IOException {
        NetworkUtils.parseIpv6Address("ff");
    }

    @Test(expected = IOException.class)
    public void testIpV6AddressStartsWithColon() throws IOException {
        NetworkUtils.parseIpv6Address(":2001:1db8:100:3:6:ff00:42:8329");
    }

}
