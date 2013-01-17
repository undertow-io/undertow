package io.undertow.security.api;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.concurrent.Executor;

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.util.AttachmentKey;
import org.xnio.IoFuture;

/**
 * The servlet security context. This context is attached to the exchange and holds all security
 * related information.
 *
 * @author Stuart Douglas
 * @see io.undertow.security.impl.SecurityContextImpl
 */
public interface SecurityContext {


    /**
     * The attachment key that is used to attach this context to the exchange
     */
    AttachmentKey<SecurityContext> ATTACHMENT_KEY = AttachmentKey.create(SecurityContext.class);

    /**
     * Performs authentication on the request, returning the result. This method can potentially block, so should not
     * be invoked from an async handler.
     * <p/>
     * If the authentication fails this {@code AuthenticationResult} can be used to send a challenge back to the client.
     * <p/>
     * Note that challenges with only be set if {@link #setAuthenticationRequired()} has been previously called this
     * request
     *
     */
    SecurityContext.AuthenticationResult authenticate() throws IOException;

    /**
     * Performs authentication on the request, returning an IoFuture that can be used to retrieve the result.
     * <p/>
     * If the authentication fails this {@code AuthenticationResult} can be used to send a challenge back to the client.
     * <p/>
     * Invoking this method can result in worker handoff, once it has been invoked the current handler should not modify the
     * exchange.
     * <p/>
     * <p/>
     * Note that challenges with only be set if {@link #setAuthenticationRequired()} has been previously called this
     * request
     *
     * @param executor The executor to use for blocking operations
     */
    IoFuture<SecurityContext.AuthenticationResult> authenticate(Executor executor);

    /**
     * Performs authentication on the request. If the auth succeeds then the next handler will be invoked, otherwise the
     * completion handler will be called.
     * <p/>
     * Invoking this method can result in worker handoff, once it has been invoked the current handler should not modify the
     * exchange.
     * <p/>
     * <p/>
     * Note that challenges with only be set if {@link #setAuthenticationRequired()} has been previously called this
     * request
     *
     * @param completionHandler The completion handler
     * @param nextHandler       The next handler to invoke once auth succeeds
     */
    void authenticate(HttpCompletionHandler completionHandler, HttpHandler nextHandler);

    /**
     * Marks this request as requiring authentication. Authentication challenge headers will only be sent if this
     * method has been called. If {@link #authenticate()} is called without first
     * calling this method then
     */
    void setAuthenticationRequired();

    AuthenticationState getAuthenticationState();

    /**
     *
     * @return The authenticated principle, or <code>null</code> if the request has not been authenticated yet
     */
    Principal getAuthenticatedPrincipal();

    /**
     *
     * @return The name of the mechanism that was used to authenticate
     */
    String getMechanismName();

    boolean isUserInGroup(String group);

    /**
     * Adds an authentication mechanism to this context. When {@link #authenticate()} is
     * called mechanisms will be iterated over in the order they are added, and given a chance to authenticate the user.
     *
     * @param mechanism The mechanism to add
     */
    void addAuthenticationMechanism(AuthenticationMechanism mechanism);

    /**
     *
     * @return A list of all authentication mechanisms in this context
     */
    List<AuthenticationMechanism> getAuthenticationMechanisms();

    /**
     * Attempts to log the user in using the provided credentials
     * <p/>
     * This operation may block
     *
     * @param username The username
     * @param password The password
     * @return <code>true</code> if the login suceeded, false otherwise
     */
    boolean login(String username, String password);

    /**
     * de-authenticates the current exchange.
     *
     */
    void logout();

    class AuthenticationResult {

        private final AuthenticationMechanism.AuthenticationMechanismOutcome outcome;
        private final Runnable requestCompletionTasks;

        public AuthenticationResult(final AuthenticationMechanism.AuthenticationMechanismOutcome outcome, final Runnable requestCompletionTasks) {
            this.outcome = outcome;
            this.requestCompletionTasks = requestCompletionTasks;
        }

        public AuthenticationMechanism.AuthenticationMechanismOutcome getOutcome() {
            return outcome;
        }

        public Runnable getRequestCompletionTasks() {
            return requestCompletionTasks;
        }
    }
}
