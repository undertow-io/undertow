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

package io.undertow.server.handlers.accesslog;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.undertow.attribute.ExchangeAttribute;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.attribute.SubstituteEmptyWrapper;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.Predicates;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;

/**
 * Access log handler. This handler will generate access log messages based on the provided format string,
 * and pass these messages into the provided {@link AccessLogReceiver}.
 * <p>
 * This handler can log any attribute that is provides via the {@link io.undertow.attribute.ExchangeAttribute}
 * mechanism. A general guide to the most common attribute is provided before, however this mechanism is extensible.
 * <p>
 * <p>
 * <p>This factory produces token handlers for the following patterns</p>
 * <ul>
 * <li><b>%a</b> - Remote IP address
 * <li><b>%A</b> - Local IP address
 * <li><b>%b</b> - Bytes sent, excluding HTTP headers, or '-' if no bytes
 * were sent
 * <li><b>%B</b> - Bytes sent, excluding HTTP headers
 * <li><b>%h</b> - Remote host name
 * <li><b>%H</b> - Request protocol
 * <li><b>%l</b> - Remote logical username from identd (always returns '-')
 * <li><b>%m</b> - Request method
 * <li><b>%o</b> - Obfuscated remote IP address (IPv4: last byte removed,
 * IPv6: cut off after second colon, ie. '1.2.3.' or 'fe08:44:')
 * <li><b>%p</b> - Local port
 * <li><b>%q</b> - Query string (excluding the '?' character)
 * <li><b>%r</b> - First line of the request
 * <li><b>%s</b> - HTTP status code of the response
 * <li><b>%t</b> - Date and time, in Common Log Format format
 * <li><b>%u</b> - Remote user that was authenticated
 * <li><b>%U</b> - Requested URL path
 * <li><b>%v</b> - Local server name
 * <li><b>%D</b> - Time taken to process the request, in millis
 * <li><b>%T</b> - Time taken to process the request, in seconds
 * <li><b>%I</b> - current Request thread name (can compare later with stacktraces)
 * </ul>
 * <p>In addition, the caller can specify one of the following aliases for
 * commonly utilized patterns:</p>
 * <ul>
 * <li><b>common</b> - <code>%h %l %u %t "%r" %s %b</code>
 * <li><b>combined</b> -
 * <code>%h %l %u %t "%r" %s %b "%{i,Referer}" "%{i,User-Agent}"</code>
 * <li><b>commonobf</b> - <code>%o %l %u %t "%r" %s %b</code>
 * <li><b>combinedobf</b> -
 * <code>%o %l %u %t "%r" %s %b "%{i,Referer}" "%{i,User-Agent}"</code>
 * </ul>
 * <p>
 * <p>
 * There is also support to write information from the cookie, incoming
 * header, or the session<br>
 * It is modeled after the apache syntax:
 * <ul>
 * <li><code>%{i,xxx}</code> for incoming headers
 * <li><code>%{o,xxx}</code> for outgoing response headers
 * <li><code>%{c,xxx}</code> for a specific cookie
 * <li><code>%{r,xxx}</code> xxx is an attribute in the ServletRequest
 * <li><code>%{s,xxx}</code> xxx is an attribute in the HttpSession
 * </ul>
 *
 * @author Stuart Douglas
 */
public class AccessLogHandler implements HttpHandler {

    private final HttpHandler next;
    private final AccessLogReceiver accessLogReceiver;
    private final String formatString;
    private final ExchangeAttribute tokens;
    private final ExchangeCompletionListener exchangeCompletionListener = new AccessLogCompletionListener();
    private final Predicate predicate;

    public AccessLogHandler(final HttpHandler next, final AccessLogReceiver accessLogReceiver, final String formatString, ClassLoader classLoader) {
        this(next, accessLogReceiver, formatString, classLoader, Predicates.truePredicate());
    }

    public AccessLogHandler(final HttpHandler next, final AccessLogReceiver accessLogReceiver, final String formatString, ClassLoader classLoader, Predicate predicate) {
        this.next = next;
        this.accessLogReceiver = accessLogReceiver;
        this.predicate = predicate;
        this.formatString = handleCommonNames(formatString);
        this.tokens = ExchangeAttributes.parser(classLoader, new SubstituteEmptyWrapper("-")).parse(this.formatString);
    }

    public AccessLogHandler(final HttpHandler next, final AccessLogReceiver accessLogReceiver, String formatString, final ExchangeAttribute attribute) {
        this(next, accessLogReceiver, formatString, attribute, Predicates.truePredicate());
    }

    public AccessLogHandler(final HttpHandler next, final AccessLogReceiver accessLogReceiver, String formatString, final ExchangeAttribute attribute, Predicate predicate) {
        this.next = next;
        this.accessLogReceiver = accessLogReceiver;
        this.predicate = predicate;
        this.formatString = handleCommonNames(formatString);
        this.tokens = attribute;
    }



    private static String handleCommonNames(String formatString) {
        if(formatString.equals("common")) {
            return "%h %l %u %t \"%r\" %s %b";
        } else if (formatString.equals("combined")) {
            return "%h %l %u %t \"%r\" %s %b \"%{i,Referer}\" \"%{i,User-Agent}\"";
        } else if(formatString.equals("commonobf")) {
            return "%o %l %u %t \"%r\" %s %b";
        } else if (formatString.equals("combinedobf")) {
            return "%o %l %u %t \"%r\" %s %b \"%{i,Referer}\" \"%{i,User-Agent}\"";
        }
        return formatString;
    }


    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        exchange.addExchangeCompleteListener(exchangeCompletionListener);
        next.handleRequest(exchange);
    }

    private class AccessLogCompletionListener implements ExchangeCompletionListener {
        @Override
        public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {
            try {
                if(predicate == null || predicate.resolve(exchange)) {
                    accessLogReceiver.logMessage(tokens.readAttribute(exchange));
                }
            } finally {
                nextListener.proceed();
            }
        }
    }

    @Override
    public String toString() {
        return "AccessLogHandler{" +
                "formatString='" + formatString + '\'' +
                '}';
    }

    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "access-log";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            Map<String, Class<?>> params = new HashMap<>();
            params.put("format", String.class);
            params.put("category", String.class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("format");
        }

        @Override
        public String defaultParameter() {
            return "format";
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            return new Wrapper((String) config.get("format"), (String) config.get("category"));
        }

    }

    private static class Wrapper implements HandlerWrapper {

        private final String format;
        private final String category;

        private Wrapper(String format, String category) {
            this.format = format;
            this.category = category;
        }

        @Override
        public HttpHandler wrap(HttpHandler handler) {
            if (category == null || category.trim().isEmpty()) {
                return new AccessLogHandler(handler, new JBossLoggingAccessLogReceiver(), format, Wrapper.class.getClassLoader());
            } else {
                return new AccessLogHandler(handler, new JBossLoggingAccessLogReceiver(category), format, Wrapper.class.getClassLoader());
            }
        }
    }
}
