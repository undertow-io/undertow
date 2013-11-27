package io.undertow.examples.chat;

import io.undertow.Undertow;
import io.undertow.examples.UndertowExample;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import org.xnio.ChannelListener;

import java.nio.channels.Channel;
import java.util.ArrayList;
import java.util.List;

import static io.undertow.Handlers.path;
import static io.undertow.Handlers.resource;
import static io.undertow.Handlers.websocket;

/**
 * @author Stuart Douglas
 */
@UndertowExample("Chat")
public class ChatServer {

    private static final List<WebSocketChannel> sessions = new ArrayList<WebSocketChannel>();

    public static void main(final String[] args) {

        System.out.println("To see chat in action is to open two different browsers and point them at http://localhost:8080");

        Undertow server = Undertow.builder()
                .addListener(8080, "localhost")
                .setHandler(path()
                        .addPath("/myapp", websocket(new WebSocketConnectionCallback() {

                            @Override
                            public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
                                synchronized (sessions) {
                                    sessions.add(channel);
                                    channel.getCloseSetter().set(new ChannelListener<Channel>() {
                                        @Override
                                        public void handleEvent(Channel channel) {
                                            synchronized (sessions) {
                                                sessions.remove(channel);
                                            }
                                        }
                                    });
                                    channel.getReceiveSetter().set(new AbstractReceiveListener() {

                                        @Override
                                        protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
                                            final String messageData = message.getData();
                                            synchronized (sessions) {
                                                for (WebSocketChannel session : sessions) {
                                                    WebSockets.sendText(messageData, session, null);
                                                }
                                            }
                                        }
                                    });
                                    channel.resumeReceives();
                                }
                            }
                        }))
                        .addPath("/", resource(new ClassPathResourceManager(ChatServer.class.getClassLoader(), ChatServer.class.getPackage()))
                                .addWelcomeFiles("index.html")))
                .build();

        server.start();
    }

}
