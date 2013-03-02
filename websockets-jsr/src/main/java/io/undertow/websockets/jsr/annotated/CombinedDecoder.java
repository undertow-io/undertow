package io.undertow.websockets.jsr.annotated;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import javax.websocket.DecodeException;
import javax.websocket.Decoder;

import io.undertow.websockets.jsr.JsrWebSocketMessages;

/**
 * @author Stuart Douglas
 */
public abstract class CombinedDecoder<T extends Decoder> {

    protected final List<T> decoders;

    public CombinedDecoder(final List<T> decoders) {
        this.decoders = decoders;
    }


    static class Text<T> extends CombinedDecoder<Decoder.Text<T>> {

        public Text(final List<Decoder.Text<T>> decoders) {
            super(decoders);
        }

        public T decode(final String value) throws IOException, DecodeException {
            for (Decoder.Text<T> decoder : decoders) {
                if (decoder.willDecode(value)) {
                    return decoder.decode(value);
                }
            }
            throw new DecodeException(value, JsrWebSocketMessages.MESSAGES.noDecoderAcceptedMessage(decoders));
        }
    }

    static class Binary<T> extends CombinedDecoder<Decoder.Binary<T>> {

        public Binary(final List<Decoder.Binary<T>> decoders) {
            super(decoders);
        }

        public T decode(final ByteBuffer value) throws IOException, DecodeException {
            for (Decoder.Binary<T> decoder : decoders) {
                if (decoder.willDecode(value)) {
                    return decoder.decode(value);
                }
            }
            throw new DecodeException(value, JsrWebSocketMessages.MESSAGES.noDecoderAcceptedMessage(decoders));
        }
    }

}
