package io.undertow.websockets.jsr;

import org.xnio.XnioWorker;
import org.xnio.ssl.XnioSsl;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.Endpoint;
import java.net.URI;

/**
 * Interface that is loaded from a service loader, that allows
 * you to configure SSL for web socket client connections.
 *
 * @author Stuart Douglas
 */
public interface WebsocketClientSslProvider {

    XnioSsl getSsl(XnioWorker worker, final Class<?> annotatedEndpoint, URI uri);

    XnioSsl getSsl(XnioWorker worker, final Object annotatedEndpointInstance, URI uri);

    XnioSsl getSsl(XnioWorker worker, final Endpoint endpoint, final ClientEndpointConfig cec, URI uri);

}
