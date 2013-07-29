package io.undertow.client;

import java.io.IOException;

/**
 * @author Emanuel Muckenhuber
 */
public interface ClientCallback<T> {

    /**
     * Invoked when an operation completed.
     *
     * @param result the operation result
     */
    void completed(T result);

    /**
     * Invoked when the operation failed.
     *
     * @param e the exception
     */
    void failed(IOException e);

}
