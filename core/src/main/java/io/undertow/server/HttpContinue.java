/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Protocols;
import io.undertow.util.StatusCodes;

/**
 * Class that provides support for dealing with HTTP 100 (Continue) responses.
 * <p>
 * Note that if a client is pipelining some requests and sending continue for others this
 * could cause problems if the pipelining buffer is enabled.
 *
 * @author Stuart Douglas
 */
public class HttpContinue {

    private static final Set<HttpString> COMPATIBLE_PROTOCOLS;

    static {
        Set<HttpString> compat = new HashSet<>();
        compat.add(Protocols.HTTP_1_1);
        compat.add(Protocols.HTTP_2_0);
        COMPATIBLE_PROTOCOLS = Collections.unmodifiableSet(compat);
    }

    public static final String CONTINUE = "100-continue";

    private static final AttachmentKey<Boolean> ALREADY_SENT = AttachmentKey.create(Boolean.class);

    /**
     * Returns true if this exchange requires the server to send a 100 (Continue) response.
     *
     * @param exchange The exchange
     * @return <code>true</code> if the server needs to send a continue response
     */
    public static boolean requiresContinueResponse(final HttpServerExchange exchange) {
        if (!COMPATIBLE_PROTOCOLS.contains(exchange.getProtocol()) || exchange.isResponseStarted() || !exchange.getConnection().isContinueResponseSupported() || exchange.getAttachment(ALREADY_SENT) != null) {
            return false;
        }

        HeaderMap requestHeaders = exchange.getRequestHeaders();
        return requiresContinueResponse(requestHeaders);
    }

    public static boolean requiresContinueResponse(HeaderMap requestHeaders) {
        List<String> expect = requestHeaders.get(Headers.EXPECT);
        if (expect != null) {
            for (String header : expect) {
                if (header.equalsIgnoreCase(CONTINUE)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Sets a 417 response code and ends the exchange.
     *
     * @param exchange The exchange to reject
     */
    public static void rejectExchange(final HttpServerExchange exchange) {
        exchange.setStatusCode(StatusCodes.EXPECTATION_FAILED);
        exchange.setPersistent(false);
        exchange.endExchange();
    }

}
