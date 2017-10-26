package io.undertow.server.handlers.proxy;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.builder.HandlerBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
public class ProxyHandlerBuilder implements HandlerBuilder {

    @Override
    public String name() {
        return "reverse-proxy";
    }

    @Override
    public Map<String, Class<?>> parameters() {
        Map<String, Class<?>> params = new HashMap<>();
        params.put("hosts", String[].class);
        params.put("rewrite-host-header", Boolean.class);
        return params;
    }

    @Override
    public Set<String> requiredParameters() {
        return Collections.singleton("hosts");
    }

    @Override
    public String defaultParameter() {
        return "hosts";
    }

    @Override
    public HandlerWrapper build(Map<String, Object> config) {
        String[] hosts = (String[]) config.get("hosts");
        List<URI> uris = new ArrayList<>();
        for (String host : hosts) {
            try {
                uris.add(new URI(host));
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        Boolean rewriteHostHeader = (Boolean) config.get("rewrite-host-header");
        return new Wrapper(uris, rewriteHostHeader);
    }

    private static class Wrapper implements HandlerWrapper {

        private final List<URI> uris;
        private final boolean rewriteHostHeader;

        private Wrapper(List<URI> uris, Boolean rewriteHostHeader) {
            this.uris = uris;
            this.rewriteHostHeader = rewriteHostHeader != null && rewriteHostHeader;
        }

        @Override
        public HttpHandler wrap(HttpHandler handler) {
            final LoadBalancingProxyClient loadBalancingProxyClient = new LoadBalancingProxyClient();
            for (URI url : uris) {
                loadBalancingProxyClient.addHost(url);
            }

            return new ProxyHandler(loadBalancingProxyClient, -1, handler, rewriteHostHeader, false);
        }
    }

}
