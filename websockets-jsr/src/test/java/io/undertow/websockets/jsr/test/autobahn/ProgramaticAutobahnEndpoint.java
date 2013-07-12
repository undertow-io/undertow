package io.undertow.websockets.jsr.test.autobahn;

import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import java.nio.ByteBuffer;

/**
 * @author Stuart Douglas
 */
public class ProgramaticAutobahnEndpoint extends Endpoint {
    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        session.addMessageHandler(new BinaryMessageHandler(session));
        session.addMessageHandler(new TextMessageHandler(session));
    }

    private static class BinaryMessageHandler implements MessageHandler.Whole<ByteBuffer> {

        private final Session session;

        private BinaryMessageHandler(Session session) {
            this.session = session;
        }

        @Override
        public void onMessage(ByteBuffer byteBuffer) {
            session.getAsyncRemote().sendBinary(byteBuffer);
        }
    }

    private static class TextMessageHandler implements MessageHandler.Whole<String> {

        private final Session session;

        private TextMessageHandler(Session session) {
            this.session = session;
        }

        @Override
        public void onMessage(String byteBuffer) {
            session.getAsyncRemote().sendText(byteBuffer);
        }
    }
}
