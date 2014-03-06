package io.undertow.server.protocol.http;

import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;

/**
 * Exchange attachments that have specific meaning when using the HTTP protocol
 *
 * @author Stuart Douglas
 */
public class HttpAttachments {

    /**
     * Attachment key for request trailers when using chunked encoding. When the request is parsed the trailers
     * will be attached under this key.
     */
    public static final AttachmentKey<HeaderMap> REQUEST_TRAILERS = AttachmentKey.create(HeaderMap.class);

    /**
     * Attachment key for response trailers. If a header map is attached under this key then the contents will be written
     * out at the end of the chunked request.
     *
     * Note that if pre chunked streams are being used then the trailers will not be appended to the response, however any
     * trailers parsed out of the chunked stream will be attached here instead.
     */
    public static final AttachmentKey<HeaderMap> RESPONSE_TRAILERS = AttachmentKey.create(HeaderMap.class);

}
