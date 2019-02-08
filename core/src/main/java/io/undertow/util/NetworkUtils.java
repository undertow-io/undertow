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

import io.undertow.UndertowMessages;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;

/**
 * @author Stuart Douglas
 */
public class NetworkUtils {

    public static String formatPossibleIpv6Address(String address) {
        if (address == null) {
            return null;
        }
        if (!address.contains(":")) {
            return address;
        }
        if (address.startsWith("[") && address.endsWith("]")) {
            return address;
        }
        return "[" + address + "]";
    }


    public static InetAddress parseIpv4Address(String addressString) throws IOException {
        String[] parts = addressString.split("\\.");
        if (parts.length != 4) {
            throw UndertowMessages.MESSAGES.invalidIpAddress(addressString);
        }
        byte[] data = new byte[4];
        for (int i = 0; i < 4; ++i) {
            String part = parts[i];
            if (part.length() == 0 || (part.charAt(0) == '0' && part.length() > 1)) {
                //leading zeros are not allowed
                throw UndertowMessages.MESSAGES.invalidIpAddress(addressString);
            }
            data[i] = (byte) Integer.parseInt(part);
        }
        return InetAddress.getByAddress(data);

    }

    public static InetAddress parseIpv6Address(String addressString) throws IOException {
        boolean startsWithColon = addressString.startsWith(":");
        if (startsWithColon && !addressString.startsWith("::")) {
            throw UndertowMessages.MESSAGES.invalidIpAddress(addressString);
        }
        String[] parts = (startsWithColon ? addressString.substring(1) : addressString).split(":"); //because of the way split works we want to change a leading double colon to a single one. We have already verified that the address does not actually start with a single colon
        byte[] data = new byte[16];
        int partOffset = 0;
        boolean seenEmpty = false;
        if (parts.length > 8) {
            throw UndertowMessages.MESSAGES.invalidIpAddress(addressString);
        }
        for (int i = 0; i < parts.length; ++i) {
            String part = parts[i];
            if (part.length() > 4) {
                throw UndertowMessages.MESSAGES.invalidIpAddress(addressString);
            } else if (part.isEmpty()) {
                if (seenEmpty) {
                    throw UndertowMessages.MESSAGES.invalidIpAddress(addressString);
                }
                seenEmpty = true;
                int off = 8 - parts.length;//this works because of the empty part that represents the double colon, so the parts list is one larger than the number of digits
                if (off < 0) {
                    throw UndertowMessages.MESSAGES.invalidIpAddress(addressString);
                }
                partOffset = off * 2;
            } else if (part.length() > 1 && part.charAt(0) == '0') {
                //leading zeros are not allowed
                throw UndertowMessages.MESSAGES.invalidIpAddress(addressString);
            } else {
                int num = Integer.parseInt(part, 16);
                data[i * 2 + partOffset] = (byte) (num >> 8);
                data[i * 2 + partOffset + 1] = (byte) (num);
            }
        }
        if (parts.length < 8 && !seenEmpty) {
            //address was too small
            throw UndertowMessages.MESSAGES.invalidIpAddress(addressString);
        }
        return InetAddress.getByAddress(data);
    }

    public static String toObfuscatedString(InetAddress address) {
        if (address == null) {
            return null;
        }
        String s = address.getHostAddress();
        if (address instanceof Inet4Address) {
            // IPv4 addresses: cut off last byte 
            return s.substring(0, s.lastIndexOf(".")+1);
        }
        // IPv6 addresses: cut off at second colon
        return s.substring(0, s.indexOf(":", s.indexOf(":")+1)+1);
    }
    
    private NetworkUtils() {

    }
}
