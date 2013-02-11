package io.undertow.examples.security.basic;

import java.security.Principal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.undertow.Undertow;
import io.undertow.io.IoCallback;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * @author Stuart Douglas
 */
public class BasicAuthServer {

    protected static final IdentityManager identityManager;

    static {
        final Map<String, char[]> users = new HashMap<String, char[]>(2);
        users.put("userOne", "passwordOne".toCharArray());
        users.put("userTwo", "passwordTwo".toCharArray());

        identityManager = new IdentityManager() {

            @Override
            public Account verify(Account account) {
                // An existing account so for testing assume still valid.
                return account;
            }

            @Override
            public Account verify(String id, Credential credential) {
                Account account = getAccount(id);
                if (account != null && verifyCredential(account, credential)) {
                    return account;
                }

                return null;
            }

            @Override
            public Account verify(Credential credential) {
                // TODO Auto-generated method stub
                return null;
            }

            private boolean verifyCredential(Account account, Credential credential) {
                if (credential instanceof PasswordCredential) {
                    char[] password = ((PasswordCredential) credential).getPassword();
                    char[] expectedPassword = users.get(account.getPrincipal().getName());

                    return Arrays.equals(password, expectedPassword);
                }
                return false;
            }

            @Override
            public char[] getPassword(final Account account) {
                return users.get(account.getPrincipal().getName());
            }

            @Override
            public Account getAccount(final String id) {
                if (users.containsKey(id)) {
                    return new Account() {

                        private Principal principal = new Principal() {

                            @Override
                            public String getName() {
                                return id;
                            }
                        };

                        @Override
                        public Principal getPrincipal() {
                            return principal;
                        }

                        @Override
                        public boolean isUserInGroup(String group) {
                            return false;
                        }

                    };
                }
                return null;
            }

        };
    }


    public static void main(final String[] args) {
        Undertow server = Undertow.builder()
                .addListener(8080, "localhost")
                .setDefaultHandler(new HttpHandler() {
                    @Override
                    public void handleRequest(final HttpServerExchange exchange) {
                        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "11");
                        exchange.getResponseSender().send("Hello World", IoCallback.END_EXCHANGE);
                    }
                })
                .setLoginConfig(
                        Undertow.loginConfig(identityManager)
                                .basicAuth("MyApp"))
                .build();
        server.start();
    }

}
