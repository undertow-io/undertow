package io.undertow.server.handlers.accesslog;

import java.util.Arrays;
import java.util.List;

import static io.undertow.server.handlers.accesslog.TokenHandler.Factory;

/**
 * @author Stuart Douglas
 */
public class CombinedTokenFactory implements Factory {

    private final List<Factory> factories;

    public CombinedTokenFactory(final List<Factory> factories) {
        this.factories = factories;
    }

    public CombinedTokenFactory(final Factory ... factories) {
        this.factories = Arrays.asList(factories);
    }

    @Override
    public TokenHandler create(final String token) {
        for(Factory factory : factories) {
            TokenHandler res = factory.create(token);
            if(res != null) {
                return res;
            }
        }
        return null;
    }
}
