package io.undertow.examples.websockets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.WorkerDispatcher;
import io.undertow.websockets.Websockets;
import io.undertow.websockets.api.AbstractAssembledFrameHandler;
import io.undertow.websockets.api.WebSocketFrameHeader;
import io.undertow.websockets.api.WebSocketSession;
import io.undertow.websockets.api.WebSocketSessionHandler;
import org.xnio.IoUtils;
import org.xnio.streams.ChannelOutputStream;

/**
 * @author Stuart Douglas
 */
public class WebSocketServer {

    public static void main(final String[] args) {
        Undertow server = Undertow.builder()
                .addListener(8080, "localhost")
                .addPathHandler("/myapp", Websockets.handler(new WebSocketSessionHandler() {
                    @Override
                    public void onSession(final WebSocketSession session, HttpServerExchange exchange) {
                        session.setFrameHandler(new AbstractAssembledFrameHandler() {
                            @Override
                            public void onTextFrame(final WebSocketSession session, final WebSocketFrameHeader header, final CharSequence payload) {
                                session.sendText(payload, null);
                            }
                        });
                    }
                }))
                .setDefaultHandler(new HttpHandler() {
                    @Override
                    public void handleRequest(final HttpServerExchange exchange) {
                        URL resource = WebSocketServer.class.getResource("index.html");
                        try {
                            final URLConnection connection = resource.openConnection();
                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html; charset=UTF-8");
                            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, connection.getContentLength());
                            WorkerDispatcher.dispatch(exchange, new Runnable() {
                                @Override
                                public void run() {
                                    InputStream istream = null;
                                    OutputStream ostream = null;
                                    try {
                                        istream = connection.getInputStream();
                                        ostream = new ChannelOutputStream(exchange.getResponseChannel());
                                        byte[] buffer = new byte[512];
                                        int read;
                                        while ((read = istream.read(buffer)) > 0) {
                                            ostream.write(buffer, 0, read);
                                        }
                                        exchange.endExchange();
                                    } catch (IOException e) {
                                        exchange.endExchange();
                                    } finally {
                                        IoUtils.safeClose(istream);
                                        IoUtils.safeClose(ostream);
                                    }
                                }
                            });
                        } catch (IOException e) {
                            exchange.endExchange();
                        }
                    }
                }).build();
        server.start();
    }

}
