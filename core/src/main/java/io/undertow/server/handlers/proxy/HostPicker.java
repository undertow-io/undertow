package io.undertow.server.handlers.proxy;

public interface HostPicker {

    int pick(LoadBalancingProxyClient.Host[] availableHosts);
}
