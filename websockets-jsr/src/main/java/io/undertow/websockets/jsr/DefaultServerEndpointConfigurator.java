package io.undertow.websockets.jsr;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfiguration;
import javax.websocket.server.ServerEndpointConfigurator;

/**
 * @author Stuart Douglas
 */
public class DefaultServerEndpointConfigurator extends ServerEndpointConfigurator {

    public DefaultServerEndpointConfigurator() {
        super();
    }

    @Override
    public String getNegotiatedSubprotocol(final List<String> supported, final List<String> requested) {
        return null;
    }

    @Override
    public List<Extension> getNegotiatedExtensions(final List<Extension> installed, final List<Extension> requested) {
        return Collections.emptyList();
    }

    @Override
    public boolean checkOrigin(final String originHeaderValue) {
        return true;
    }

    @Override
    public boolean matchesURI(final String path, final URI requestUri, final Map<String, String> templateExpansion) {
        int j = 0;
        String reqPath = requestUri.getPath();
        for (int i = 0; i < path.length(); ++i) {
            if (i == path.length() - 1 && path.charAt(i) == '/' && j == reqPath.length()) {
                //the match has a trailing / but the request URI doesn't. They are a match
                return true;
            } else if (j == reqPath.length()) {
                //the request path is to short to match
                return false;
            } else if (path.charAt(i) == '{') {
                //template expansion
                int start = i;
                while (path.charAt(i) != '}' && i < path.length()) {
                    ++i;
                }
                final String matchPart = path.substring(start+1, i);
                start = j;
                while (j < reqPath.length() && reqPath.charAt(j) != '/') {
                    ++j;
                }
                templateExpansion.put(matchPart, reqPath.substring(start, j));
            } else if (path.charAt(i) != reqPath.charAt(j)) {
                //mismatch
                return false;
            } else {
                ++j;
            }

        }
        return true;
    }

    @Override
    public void modifyHandshake(final ServerEndpointConfiguration sec, final HandshakeRequest request, final HandshakeResponse response) {

    }


}
