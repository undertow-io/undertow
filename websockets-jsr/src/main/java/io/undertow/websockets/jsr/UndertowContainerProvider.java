package io.undertow.websockets.jsr;import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Stuart Douglas
 */
public class UndertowContainerProvider extends ContainerProvider {

    private static final RuntimePermission PERMISSION = new RuntimePermission("io.undertow.websockets.jsr.MODIFY_WEBSOCKET_CONTAINER");

    private static final Map<ClassLoader, WebSocketContainer> webSocketContainers = new ConcurrentHashMap<ClassLoader, WebSocketContainer>();

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
        return webSocketContainers.get(tccl);
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
        AccessController.checkPermission(PERMISSION);
        webSocketContainers.remove(classLoader);
    }
}
