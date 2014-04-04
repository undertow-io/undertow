package io.undertow.websockets.jsr;

import org.xnio.Pool;
import org.xnio.XnioWorker;

import javax.websocket.server.ServerEndpointConfig;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Web socket deployment information
 *
 * @author Stuart Douglas
 */
public class WebSocketDeploymentInfo {

    public static final String ATTRIBUTE_NAME = "io.undertow.websockets.jsr.WebSocketDeploymentInfo";

    private XnioWorker worker;
    private Pool<ByteBuffer> buffers;
    private boolean dispatchToWorkerThread = false;
    private final List<Class<?>> annotatedEndpoints = new ArrayList<Class<?>>();
    private final List<ServerEndpointConfig> programaticEndpoints = new ArrayList<ServerEndpointConfig>();
    private final List<ContainerReadyListener> containerReadyListeners = new ArrayList<ContainerReadyListener>();

    public XnioWorker getWorker() {
        return worker;
    }

    public WebSocketDeploymentInfo setWorker(XnioWorker worker) {
        this.worker = worker;
        return this;
    }

    public Pool<ByteBuffer> getBuffers() {
        return buffers;
    }

    public WebSocketDeploymentInfo setBuffers(Pool<ByteBuffer> buffers) {
        this.buffers = buffers;
        return this;
    }

    public WebSocketDeploymentInfo addEndpoint(final Class<?> annotated) {
        this.annotatedEndpoints.add(annotated);
        return this;
    }

    public WebSocketDeploymentInfo addEndpoint(final ServerEndpointConfig endpoint) {
        this.programaticEndpoints.add(endpoint);
        return this;
    }

    public List<Class<?>> getAnnotatedEndpoints() {
        return annotatedEndpoints;
    }

    public List<ServerEndpointConfig> getProgramaticEndpoints() {
        return programaticEndpoints;
    }

    void containerReady(ServerWebSocketContainer container) {
        for(ContainerReadyListener listener : containerReadyListeners) {
            listener.ready(container);
        }
    }

    public WebSocketDeploymentInfo addListener(final ContainerReadyListener listener) {
        containerReadyListeners.add(listener);
        return this;
    }

    public boolean isDispatchToWorkerThread() {
        return dispatchToWorkerThread;
    }

    public void setDispatchToWorkerThread(boolean dispatchToWorkerThread) {
        this.dispatchToWorkerThread = dispatchToWorkerThread;
    }

    public interface ContainerReadyListener {
        void ready(ServerWebSocketContainer container);
    }
}
