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
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public enum AuthenticationState {

    /**
     * No authentication is required for this request.
     *
     * Although not required the mechanism specific handlers may still verify the currently authenticated user, this is so that
     * even if an unsecured portion of a site is being visited the current user can still be identified.
     */
    NOT_REQUIRED,

    /**
     * Authentication is required before this request can proceed.
     */
    REQUIRED,

    AUTHENTICATED,

    /**
     * At least one authentication mechanism was attempted and it failed.
     */
    FAILED

}
