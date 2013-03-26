package io.undertow.server.handlers.cache;

import java.util.AbstractCollection;
import java.util.Deque;

/**
 * A concurrent deque that allows direct item removal without traversal.
 *
 * @author Jason T. Greene
 */
public abstract  class ConcurrentDirectDeque<E> extends AbstractCollection<E> implements Deque<E>, java.io.Serializable {

    public static <K> ConcurrentDirectDeque<K> newInstance() {
        try {
            return new FastConcurrentDirectDeque<K>();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public abstract Object offerFirstAndReturnToken(E e);

    public abstract Object offerLastAndReturnToken(E e);

    public abstract void removeToken(Object token);
}
