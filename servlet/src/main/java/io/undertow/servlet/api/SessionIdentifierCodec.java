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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.servlet.api;

/**
 * Encapsulates logic to encode/decode additional information in/from a session identifier.
 * Both the {@link #encode(String)} and {@link #decode(String)} methods should be idempotent.
 * The codec methods should also be symmetrical.  i.e. the result of
 * <code>decode(encode(x))</code> should yield <code>x</code>, just as the result of
 * <code>encode(decode(y))</code> should yield <code>y</code>.
 * @author Paul Ferraro
 */
public interface SessionIdentifierCodec {
    /**
     * Encodes the specified session identifier
     * @param sessionId a session identifier
     * @return an encoded session identifier
     */
    String encode(String sessionId);

    /**
     * Decodes the specified session identifier encoded via {@link #encode(String)}.
     * @param encodedSessionId an encoded session identifier
     * @return the decoded session identifier
     */
    String decode(String encodedSessionId);
}
