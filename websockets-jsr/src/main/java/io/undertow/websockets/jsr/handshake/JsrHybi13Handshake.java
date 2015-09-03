/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.undertow.websockets.jsr.handshake;

import io.undertow.websockets.WebSocketExtension;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.protocol.version13.Hybi13Handshake;
import io.undertow.websockets.jsr.ConfiguredServerEndpoint;
import io.undertow.websockets.jsr.ExtensionImpl;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import io.undertow.connector.ByteBufferPool;
import org.xnio.StreamConnection;

import javax.websocket.Extension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link Hybi13Handshake} sub-class which takes care of match against the {@link javax.websocket.server.ServerEndpointConfig} and
 * stored the config in the attributes for later usage.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class JsrHybi13Handshake extends Hybi13Handshake {
    private final ConfiguredServerEndpoint config;

    public JsrHybi13Handshake(ConfiguredServerEndpoint config) {
        super(Collections.<String>emptySet(), false);
        this.config = config;
    }

    @Override
    protected void upgradeChannel(final WebSocketHttpExchange exchange, byte[] data) {
        HandshakeUtil.prepareUpgrade(config.getEndpointConfiguration(), exchange);
        super.upgradeChannel(exchange, data);
    }

    @Override
    public WebSocketChannel createChannel(WebSocketHttpExchange exchange, final StreamConnection c, final ByteBufferPool buffers) {
        WebSocketChannel channel = super.createChannel(exchange, c, buffers);
        HandshakeUtil.setConfig(channel, config);
        return channel;
    }

    @Override
    public boolean matches(WebSocketHttpExchange exchange) {
        return super.matches(exchange) && HandshakeUtil.checkOrigin(config.getEndpointConfiguration(), exchange);
    }

    @Override
    protected String supportedSubprotols(String[] requestedSubprotocolArray) {
        return HandshakeUtil.selectSubProtocol(config, requestedSubprotocolArray);
    }

    @Override
    protected List<WebSocketExtension> selectedExtension(List<WebSocketExtension> extensionList) {
        List<Extension> ext = new ArrayList<>();
        for(WebSocketExtension i : extensionList) {
            ext.add(ExtensionImpl.create(i));
        }
        List<Extension> selected = HandshakeUtil.selectExtensions(config, ext);
        if(selected == null) {
            return Collections.emptyList();
        }
        List<WebSocketExtension> ret = new ArrayList<>();
        for(Extension i : selected) {
            List<WebSocketExtension.Parameter> parameters = new ArrayList<>();
            for(Extension.Parameter p : i.getParameters()) {
                parameters.add(new WebSocketExtension.Parameter(p.getName(), p.getValue()));
            }
            ret.add(new WebSocketExtension(i.getName(), parameters));
        }

        return ret;
    }
}
