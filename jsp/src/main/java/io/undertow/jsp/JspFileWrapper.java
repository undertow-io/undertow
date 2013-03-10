package io.undertow.jsp;

import io.undertow.server.HttpHandler;
import io.undertow.server.HandlerWrapper;

/**
 * @author Stuart Douglas
 */
public class JspFileWrapper implements HandlerWrapper {

    private final String jspFile;

    public JspFileWrapper(final String jspFile) {
        this.jspFile = jspFile;
    }

    @Override
    public HttpHandler wrap(final HttpHandler handler) {
        return new JspFileHandler(jspFile, handler);
    }
}
