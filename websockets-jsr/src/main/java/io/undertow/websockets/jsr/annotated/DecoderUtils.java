package io.undertow.websockets.jsr.annotated;

import java.util.List;

import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;

/**
 * @author Stuart Douglas
 */
public class DecoderUtils {

    /**
     * Gets a decoder for a given type.
     *
     * @param type                  The type
     * @param endpointConfiguration The endpoint configuration
     * @return A list of decoders, or null if no decoders exist
     */
    public static List<Decoder> getDecodersForType(final Class<?> type, final EndpointConfig endpointConfiguration) {
//        final List<Decoder> decoders = new ArrayList<>();
//        for (final Decoder decoder : endpointConfiguration.getDecoders()) {
//            final Class<?> clazz = ClassUtils.getDecoderType(decoder.getClass());
//            if (type.isAssignableFrom(clazz)) {
//                decoders.add(decoder);
//            }
//        }
//        if (!decoders.isEmpty()) {
//            return decoders;
//        }
        return null;
    }


    private DecoderUtils() {
    }

}
