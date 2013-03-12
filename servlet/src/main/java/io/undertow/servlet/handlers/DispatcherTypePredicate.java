package io.undertow.servlet.handlers;

import javax.servlet.DispatcherType;

import io.undertow.predicate.Predicate;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.spec.HttpServletRequestImpl;

/**
 * Predicate that returns true if the dispatcher type matches the specified type.
 *
 *
 * @author Stuart Douglas
 */
public class DispatcherTypePredicate implements Predicate<HttpServerExchange> {

    public static final DispatcherTypePredicate FORWARD = new DispatcherTypePredicate(DispatcherType.FORWARD);
    public static final DispatcherTypePredicate INCLUDE = new DispatcherTypePredicate(DispatcherType.INCLUDE);
    public static final DispatcherTypePredicate REQUEST = new DispatcherTypePredicate(DispatcherType.REQUEST);
    public static final DispatcherTypePredicate ASYNC = new DispatcherTypePredicate(DispatcherType.ASYNC);
    public static final DispatcherTypePredicate ERROR = new DispatcherTypePredicate(DispatcherType.ERROR);


    private final DispatcherType dispatcherType;

    public DispatcherTypePredicate(final DispatcherType dispatcherType) {
        this.dispatcherType = dispatcherType;
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        return value.getAttachment(HttpServletRequestImpl.DISPATCHER_TYPE_ATTACHMENT_KEY) == dispatcherType;
    }
}
