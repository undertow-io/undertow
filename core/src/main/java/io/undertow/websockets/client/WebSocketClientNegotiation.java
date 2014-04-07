package io.undertow.websockets.client;

import io.undertow.websockets.WebSocketExtension;

import java.util.List;
import java.util.Map;

/**
 * @author Stuart Douglas
 */
public class WebSocketClientNegotiation {

    private final List<String> supportedSubProtocols;
    private final List<WebSocketExtension> supportedExtensions;
    private volatile String selectedSubProtocol;
    private volatile List<WebSocketExtension> selectedExtensions;

    public WebSocketClientNegotiation(List<String> supportedSubProtocols, List<WebSocketExtension> supportedExtensions) {
        this.supportedSubProtocols = supportedSubProtocols;
        this.supportedExtensions = supportedExtensions;
    }

    public List<String> getSupportedSubProtocols() {
        return supportedSubProtocols;
    }

    public List<WebSocketExtension> getSupportedExtensions() {
        return supportedExtensions;
    }

    public String getSelectedSubProtocol() {
        return selectedSubProtocol;
    }

    public List<WebSocketExtension> getSelectedExtensions() {
        return selectedExtensions;
    }

    public void beforeRequest(final Map<String, String> headers) {

    }
    public void afterRequest(final Map<String, String> headers) {

    }

    public void handshakeComplete(String selectedProtocol, List<WebSocketExtension> selectedExtensions) {
        this.selectedExtensions = selectedExtensions;
        this.selectedSubProtocol = selectedProtocol;
    }
}
