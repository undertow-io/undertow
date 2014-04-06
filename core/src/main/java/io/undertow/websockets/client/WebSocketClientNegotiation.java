package io.undertow.websockets.client;

import io.undertow.websockets.WebSocketExtension;

import java.util.List;

/**
 * @author Stuart Douglas
 */
public class WebSocketClientNegotiation {

    private final List<String> supportedSubProtocols;
    private final List<WebSocketExtension> supportedExtensions;
    private volatile String selectedSubProtocol;
    private volatile List<String> selectedExtensions;

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

    public List<String> getSelectedExtensions() {
        return selectedExtensions;
    }

    public void handshakeComplete(String selectedProtocol, List<String> selectedExtensions) {
        this.selectedExtensions = selectedExtensions;
        this.selectedSubProtocol = selectedProtocol;
    }
}
