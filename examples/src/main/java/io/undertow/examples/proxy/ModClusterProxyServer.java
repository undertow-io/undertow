package io.undertow.examples.proxy;

import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

import io.undertow.Undertow;
import io.undertow.examples.UndertowExample;
import io.undertow.proxy.MCMPHandler;
import io.undertow.proxy.ModClusterLoadBalancingProxyClient;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.ProxyHandler;

/**
 * @author Jean-Frederic Clere
 */

@UndertowExample("ModCluster Proxy Server")
public class ModClusterProxyServer {

    /* the address and port to receive the MCMP elements */
    static String chost = System.getProperty("io.undertow.examples.proxy.CADDRESS");
    static final int cport = Integer.parseInt(System.getProperty("io.undertow.examples.proxy.CPORT", "6666"));

    /* the address and port to receive normal requests */
    static String phost = System.getProperty("io.undertow.examples.proxy.ADDRESS", "localhost");
    static final int pport = Integer.parseInt(System.getProperty("io.undertow.examples.proxy.PORT", "8000"));

    /* Start HACK */
    private static final OptionMap DEFAULT_OPTIONS;
    private static XnioWorker worker;
    static {
       final OptionMap.Builder builder = OptionMap.builder()
               .set(Options.WORKER_IO_THREADS, 8)
               .set(Options.TCP_NODELAY, true)
               .set(Options.KEEP_ALIVE, true)
               .set(Options.WORKER_NAME, "Proxy");
         DEFAULT_OPTIONS = builder.getMap();
         Xnio xnio = Xnio.getInstance();
         try {
            worker = xnio.createWorker(null, DEFAULT_OPTIONS);
            System.out.println("worker: " + worker);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /* End HACK */

    public static void main(final String[] args) {
        Undertow server;
        try {
            if (chost == null) {
                // We are going to guess it.
                chost = java.net.InetAddress.getLocalHost().getHostName();
                System.out.println("Using: " + chost + ":" + cport);
            }
            ModClusterLoadBalancingProxyClient loadBalancer = new ModClusterLoadBalancingProxyClient();
            ProxyHandler proxy = new ProxyHandler(loadBalancer, 30000, ResponseCodeHandler.HANDLE_404);
            MCMPHandler mcmp = new MCMPHandler(proxy, loadBalancer);
            mcmp.setChost(chost);
            mcmp.setCport(cport);
            mcmp.setProxy(proxy);
            server = Undertow.builder()
                    .addListener(cport, chost)
                    .addListener(pport, phost)
                    .setHandler(mcmp)
                    .build();
            server.start();
            mcmp.init();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
