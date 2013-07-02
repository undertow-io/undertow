package io.undertow.server.handlers;

import io.undertow.attribute.ExchangeAttribute;
import io.undertow.attribute.ExchangeAttributeParser;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * Handler that can set an arbitrary attribute on the exchange. Both the attribute and the
 * value to set are expressed as exchange attributes.
 *
 *
 * @author Stuart Douglas
 */
public class SetAttributeHandler implements HttpHandler {

    private final HttpHandler next;
    private final ExchangeAttribute attribute;
    private final ExchangeAttribute value;

    public SetAttributeHandler(HttpHandler next, ExchangeAttribute attribute, ExchangeAttribute value) {
        this.next = next;
        this.attribute = attribute;
        this.value = value;
    }

    public SetAttributeHandler(HttpHandler next, final String attribute, final String value) {
        this.next = next;
        ExchangeAttributeParser parser = ExchangeAttributes.parser(getClass().getClassLoader());
        this.attribute = parser.parseSingleToken(attribute);
        this.value = parser.parse(value);
    }

    public SetAttributeHandler(HttpHandler next, final String attribute, final String value, final ClassLoader classLoader) {
        this.next = next;
        ExchangeAttributeParser parser = ExchangeAttributes.parser(classLoader);
        this.attribute = parser.parseSingleToken(attribute);
        this.value = parser.parse(value);
    }
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        attribute.writeAttribute(exchange, value.readAttribute(exchange));
        next.handleRequest(exchange);
    }
}
