package io.undertow.jsp;

import javax.servlet.ServletRequest;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.HttpHandler;
import io.undertow.servlet.handlers.ServletRequestContext;
import org.apache.jasper.Constants;

/**
 *
 *
 * @author Stuart Douglas
 */
public class JspFileHandler implements HttpHandler {

    private final String jspFile;
    private final HttpHandler next;

    public JspFileHandler(final String jspFile, final HttpHandler next) {
        this.jspFile = jspFile;
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        ServletRequest request = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY).getServletRequest();
        Object old = request.getAttribute(Constants.JSP_FILE);
        try {
            request.setAttribute(Constants.JSP_FILE, jspFile);
            next.handleRequest(exchange);
        } finally {
            request.setAttribute(Constants.JSP_FILE, old);
        }
    }
}
