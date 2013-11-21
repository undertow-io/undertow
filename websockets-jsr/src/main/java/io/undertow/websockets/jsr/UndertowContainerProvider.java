package io.undertow.websockets.jsr;

import io.undertow.servlet.api.ThreadSetupAction;
import io.undertow.servlet.core.CompositeThreadSetupAction;
import io.undertow.servlet.util.DefaultClassIntrospector;
import org.xnio.ByteBufferSlicePool;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Stuart Douglas
 */
public class UndertowContainerProvider extends ContainerProvider {

    private static final RuntimePermission PERMISSION = new RuntimePermission("io.undertow.websockets.jsr.MODIFY_WEBSOCKET_CONTAINER");

    private static final Map<ClassLoader, WebSocketContainer> webSocketContainers = new ConcurrentHashMap<ClassLoader, WebSocketContainer>();

    private static volatile WebSocketContainer defaultContainer;

    @Override
    protected WebSocketContainer getContainer() {
        ClassLoader tccl;
        if(System.getSecurityManager() == null) {
            tccl = Thread.currentThread().getContextClassLoader();
        } else {
            tccl = AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                @Override
                public ClassLoader run() {
                    return Thread.currentThread().getContextClassLoader();
                }
            });
        }
        WebSocketContainer webSocketContainer = webSocketContainers.get(tccl);
        if(webSocketContainer == null) {
            return getDefaultContainer();
        }
        return webSocketContainer;
    }

    private WebSocketContainer getDefaultContainer() {
        if(defaultContainer != null) {
            return defaultContainer;
        }
        synchronized (UndertowContainerProvider.class) {
            if(defaultContainer == null) {
                try {
                    //this is not great, as we have no way to control the lifecycle
                    //but there is not much we can do
                    //todo: what options should we use here?
                    XnioWorker worker = Xnio.getInstance().createWorker(OptionMap.create(Options.THREAD_DAEMON, true));
                    Pool<ByteBuffer> buffers = new ByteBufferSlicePool(1024, 10240);
                    defaultContainer = new ServerWebSocketContainer(DefaultClassIntrospector.INSTANCE, worker, buffers, new CompositeThreadSetupAction(Collections.<ThreadSetupAction>emptyList()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return defaultContainer;
        }
    }

    public static void addContainer(final ClassLoader classLoader, final WebSocketContainer webSocketContainer) {
        if(System.getSecurityManager() != null) {
            AccessController.checkPermission(PERMISSION);
        }
        webSocketContainers.put(classLoader, webSocketContainer);
    }

    public static void removeContainer(final ClassLoader classLoader) {
        if(System.getSecurityManager() != null) {
            AccessController.checkPermission(PERMISSION);
        }
        webSocketContainers.remove(classLoader);
    }
}
