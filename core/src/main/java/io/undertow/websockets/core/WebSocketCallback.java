package io.undertow.websockets.core;

/**
 * @author Stuart Douglas
 */
public interface WebSocketCallback<T> {

    void complete(final WebSocketChannel channel, T context);

    void onError(final WebSocketChannel channel, T context, Throwable throwable);

}
