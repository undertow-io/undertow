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

import io.undertow.security.idm.IdentityManager;
import io.undertow.server.handlers.form.FormParserFactory;

import java.util.Map;

/**
 *
 * Factory for authentication mechanisms.
 *
 *
 *
 * @author Stuart Douglas
 */
public interface AuthenticationMechanismFactory {

    String REALM = "realm";
    String LOGIN_PAGE = "login_page";
    String ERROR_PAGE = "error_page";
    String CONTEXT_PATH = "context_path";
    String DEFAULT_PAGE = "default_page";
    String OVERRIDE_INITIAL = "override_initial";

    /**
     * Creates an authentication mechanism using the specified properties
     *
     * @param mechanismName The name under which this factory was registered
     * @param properties The properties
     * @param formParserFactory Parser to create a form data parser for a given request.
     * @return The mechanism
     */
    @Deprecated
    default AuthenticationMechanism create(String mechanismName, FormParserFactory formParserFactory, final Map<String, String> properties) {
        return null;
    }

    /**
     * Creates an authentication mechanism that needs access to the deployment IdentityManager and specified properties
     *
     * @param mechanismName The name under which this factory was registered
     * @param identityManager the IdentityManager instance asscociated with the deployment
     * @param formParserFactory Parser to create a form data parser for a given request.
     * @param properties The properties
     * @return The mechanism
     */
    default AuthenticationMechanism create(String mechanismName, IdentityManager identityManager, FormParserFactory formParserFactory, final Map<String, String> properties) {
        return create(mechanismName, formParserFactory, properties);
    }

}
