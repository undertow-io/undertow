package io.undertow.servlet.api;

import io.undertow.server.handlers.blocking.BlockingHttpHandler;

/**
 * Interface that can be used to wrap the servlet chain, adding additional handlers
 *
 * The handler that is passed in is the handler that would normally run straight after the
 * {@link io.undertow.servlet.handlers.ServletInitialHandler}
 *
 * This may be run multiple times for the same servlet, as servlet mapped to different paths
 * have different chains.
 *
 * @author Stuart Douglas
 */
public interface HandlerChainWrapper {

    BlockingHttpHandler wrap(BlockingHttpHandler handler);

}
