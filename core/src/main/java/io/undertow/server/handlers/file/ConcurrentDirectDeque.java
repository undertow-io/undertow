package io.undertow.server.handlers.file;

import java.lang.reflect.Constructor;
import java.util.AbstractCollection;
import java.util.Deque;

/**
 * A concurrent deque that allows direct item removal without traversal.
 *
 * @author Jason T. Greene
 */
public abstract  class ConcurrentDirectDeque<E> extends AbstractCollection<E> implements Deque<E>, java.io.Serializable {
    private static Constructor<? extends ConcurrentDirectDeque> CONSTRUCTOR;

    static {
        boolean fast = false;
        try {
            new FastConcurrentDirectDeque();
            fast = true;
        } catch (Throwable t) {
        }

        Class<? extends ConcurrentDirectDeque> klazz = fast ? FastConcurrentDirectDeque.class : PortableConcurrentDirectDeque.class;
        try {
            CONSTRUCTOR = klazz.getConstructor();
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(e.getMessage());
        }
    }

    public static <K> ConcurrentDirectDeque<K> newInstance() {
        try {
            return CONSTRUCTOR.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public abstract Object offerFirstAndReturnToken(E e);

    public abstract Object offerLastAndReturnToken(E e);

    public abstract void removeToken(Object token);
}
