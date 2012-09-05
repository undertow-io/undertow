package io.undertow.server.handlers.file;

import java.util.Collection;
import java.util.Deque;

/**
 * A concurrent deque that allows direct item removal without traversal.
 *
 * @author Jason T. Greene
 */
public interface ConcurrentDirectDeque<E> extends Collection<E>,Deque<E>, java.io.Serializable {
    Object offerFirstAndReturnToken(E e);

    Object offerLastAndReturnToken(E e);

    void removeToken(Object token);
}
