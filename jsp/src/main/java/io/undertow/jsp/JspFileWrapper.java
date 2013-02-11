package io.undertow.jsp;

import io.undertow.server.handlers.blocking.BlockingHttpHandler;
import io.undertow.server.HandlerWrapper;

/**
 * @author Stuart Douglas
 */
public class JspFileWrapper implements HandlerWrapper<BlockingHttpHandler> {

    private final String jspFile;

    public JspFileWrapper(final String jspFile) {
        this.jspFile = jspFile;
    }

    @Override
    public BlockingHttpHandler wrap(final BlockingHttpHandler handler) {
        return new JspFileHandler(jspFile, handler);
    }
}
