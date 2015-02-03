package io.undertow.server.handlers.proxy;

import java.util.concurrent.atomic.AtomicInteger;

class RoundRobinHostPicker implements HostPicker {

    private final AtomicInteger currentHost = new AtomicInteger(0);

    @Override
    public int pick(LoadBalancingProxyClient.Host[] availableHosts) {
        return currentHost.incrementAndGet() % availableHosts.length;
    }
}
