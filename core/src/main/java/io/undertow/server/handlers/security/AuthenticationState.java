/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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
package io.undertow.server.handlers.security;

/**
 * The AuthenticationState represents the overall status of authentication for the current request.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public enum AuthenticationState {

    /**
     * Authentication is required for this request, for the handleRequest stage of authentication all called mechanisms should
     * attempt authentication, if in this state for the handleComplete stage then all mechanisms should send their challenge.
     *
     * Access to any resource will not be granted whilst in this state.
     *
     * It is possible to transition to this state from {@link #NOT_REQUIRED} should an authentication attempt fail, this would indicate
     * that all mechanisms should challenge the client again as previously submitted tokens were rejected.
     */
    REQUIRED,

    /**
     * Authentication is not required, however in the handleRequest stage mechanisms will be give then opportunity to
     * authenticate the incoming request.
     *
     * During the handleComplete stage if this is still the state then no authentication mechanisms will be called.
     */
    NOT_REQUIRED,

    /**
     * Authentication has already been completed by a mechanism, no further mechanisms will be asked to authenticate during the
     * handleRequest stage, during the handleComplete stage only the mechanism that authenticated the request will be called
     * giving it an opportunity to pass back any additional mechanism specific tokens in the response.
     *
     */
    AUTHENTICATED

}
