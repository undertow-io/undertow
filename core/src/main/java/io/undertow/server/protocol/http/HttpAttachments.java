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

package io.undertow.server.protocol.http;

import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;

import java.util.function.Supplier;

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
     * out at the end of the chunked request or HTTP/2 response.
     *
     * Note that the results of {@link #RESPONSE_TRAILERS} and {@link #RESPONSE_TRAILER_SUPPLIER} will be merged if both exit
     * with the value supplied by the supplier taking precedence.
     *
     * Note that if pre chunked streams are being used then the trailers will not be appended to the response, however any
     * trailers parsed out of the chunked stream will be attached here instead.
     */
    public static final AttachmentKey<HeaderMap> RESPONSE_TRAILERS = AttachmentKey.create(HeaderMap.class);


    /**
     * Attachment key for a supplier response trailers. If a header map is attached under this key then the contents will be written
     * out at the end of the chunked request or HTTP/2 response.
     *
     * Note that the results of {@link #RESPONSE_TRAILERS} and {@link #RESPONSE_TRAILER_SUPPLIER} will be merged if both exit
     * with the value supplied by the supplier taking precedence.
     *
     * Note that if pre chunked streams are being used then the trailers will not be appended to the response, however any
     * trailers parsed out of the chunked stream will be attached here instead.
     */
    public static final AttachmentKey<Supplier<HeaderMap>> RESPONSE_TRAILER_SUPPLIER = AttachmentKey.create(Supplier.class);

    /**
     * If the value {@code true} is attached to the exchange under this key then Undertow will assume that the underlying application
     * has already taken care of chunking, and will not attempt to add its own chunk markers.
     *
     * This will only take effect if the application has explicitly set the {@literal Transfer-Encoding: chunked} header.
     *
     */
    public static final AttachmentKey<Boolean> PRE_CHUNKED_RESPONSE = AttachmentKey.create(Boolean.class);

}
