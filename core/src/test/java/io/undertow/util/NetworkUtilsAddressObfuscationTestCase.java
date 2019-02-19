/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * verifies that the proxy protocol ip address parser correctly parses IP addresses as per the additional requirements
 * in the proxy protocol spec
 *
 * @author Stuart Douglas
 */
public class NetworkUtilsAddressObfuscationTestCase {

    private static String cvt(String input) throws UnknownHostException {
        return NetworkUtils.toObfuscatedString(InetAddress.getByName(input));
    }

    @Test
    public void testIpV4Address() throws IOException {
        Assert.assertEquals("1.123.255.", cvt("1.123.255.2"));
        Assert.assertEquals("127.0.0.", cvt("127.0.0.1"));
        Assert.assertEquals("0.0.0.", cvt("0.0.0.0"));
    }

    @Test
    public void testIpv6Address() throws IOException {
        Assert.assertEquals("2001:1db8:", cvt("2001:1db8:100:3:6:ff00:42:8329"));
        Assert.assertEquals("2001:1db8:", cvt("2001:1db8:100::6:ff00:42:8329"));
        Assert.assertEquals("0:0:", cvt("::1"));
    }
}
