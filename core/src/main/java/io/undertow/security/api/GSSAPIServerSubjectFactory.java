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

import java.security.GeneralSecurityException;

import javax.security.auth.Subject;

/**
 * The GSSAPIServerSubjectFactory is a factory responsible for returning the {@link Subject} that should be used for handing the
 * GSSAPI based authentication for a specific request.
 *
 * The authentication handlers will not perform any caching of the returned Subject, the factory implementation can either
 * return a new Subject for each request or can cache them maybe based on the expiration time of tickets contained within the
 * Subject.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface GSSAPIServerSubjectFactory {

    // TODO - Does this need to be supplying some kind of wrapper that allows a try/finally approach to being and end using the Subject?

    /**
     * Obtain the Subject to use for the specified host.
     *
     * All virtual hosts on a server could use the same Subject or each virtual host could have a different Subject, the
     * implementation of the factory will make that decision. The factory implementation will also decide if there should be a
     * default fallback Subject or if a Subject should only be provided for recognised hosts.
     *
     * @param hostName - The host name used for this request.
     * @return The Subject to use for the specified host name or null if no match possible.
     * @throws GeneralSecurityException if there is a security failure obtaining the {@link Subject}
     */
    Subject getSubjectForHost(final String hostName) throws GeneralSecurityException;

}
