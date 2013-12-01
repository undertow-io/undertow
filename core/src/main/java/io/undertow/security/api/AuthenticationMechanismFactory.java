package io.undertow.security.api;

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


    /**
     * Creates an authentication mechanism using the specified properties
     *
     * @param mechanismName The name under which this factory was registered
     * @param properties The properties
     * @return The mechanism
     */
    AuthenticationMechanism create(String mechanismName, FormParserFactory formParserFactory, final Map<String, String> properties);

}
