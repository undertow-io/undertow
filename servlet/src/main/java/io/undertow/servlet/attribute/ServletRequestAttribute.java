package io.undertow.servlet.attribute;

import io.undertow.attribute.ExchangeAttribute;
import io.undertow.attribute.ExchangeAttributeBuilder;
import io.undertow.attribute.ReadOnlyAttributeException;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.ServletRequestContext;

/**
 * An attribute in the servlet request
 *
 * @author Stuart Douglas
 */
public class ServletRequestAttribute implements ExchangeAttribute {

    private final String attributeName;

    public ServletRequestAttribute(final String attributeName) {
        this.attributeName = attributeName;
    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        ServletRequestContext context = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        if (context != null) {
            Object result = context.getServletRequest().getAttribute(attributeName);
            if (result != null) {
                return result.toString();
            }
        }
        return null;
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        ServletRequestContext context = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        if (context != null) {
            context.getServletRequest().setAttribute(attributeName, newValue);
        }
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Servlet request attribute";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.startsWith("%{r,") && token.endsWith("}")) {
                final String attributeName = token.substring(4, token.length() - 1);
                return new ServletRequestAttribute(attributeName);
            }
            return null;
        }
    }
}
