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
package io.undertow.security.api;

/**
 * Enumeration to indicate the authentication mode in use.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public enum AuthenticationMode {

    /**
     * Where the authentication mode is set to pro-active each request on arrival will be passed to the defined authentication
     * mechanisms to eagerly perform authentication if there is sufficient information available in order to do so.
     *
     * A pro-active authentication could be possible for a number of reasons such as already having a SSL connection
     * established, an identity being cached against the current session or even a browser sending in authentication headers.
     *
     * Running in pro-active mode the sending of the challenge to the client is still driven by the constraints defined so this
     * is not the same as mandating security for all paths. For some mechanisms such as Digest this is a recommended mode as
     * without it there is a risk that clients are sending in headers with unique nonce counts that go unverified risking that a
     * malicious client could make use of them. This is also useful for applications that wish to make use of the current
     * authenticated user if one exists without mandating that authentication occurs.
     */
    PRO_ACTIVE,

    /**
     * When running in constraint driven mode the authentication mechanisms are only executed where the constraint that mandates
     * authentication is triggered, for all other requests no authentication occurs unless requested by the internal APIs which
     * may be exposed using the Servlet APIs.
     */
    CONSTRAINT_DRIVEN;

}
