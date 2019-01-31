package io.undertow.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class SimpleObjectPoolTestCase {

    @Rule
    public final ExpectedException expected = ExpectedException.none();

    @Test
    public void testObjectAlreadyReturned() {
        SimpleObjectPool<Object> pool = new SimpleObjectPool<>(1, Object::new, obj -> {}, obj -> {});
        PooledObject<Object> pooled = pool.allocate();
        pooled.close();

        expected.expect(IllegalStateException.class);
        pooled.getObject();
    }

    @Test
    public void testCloseMayBeInvokedMultipleTimesWhenObjectIsRecycled() {
        AtomicInteger recycled = new AtomicInteger();
        AtomicInteger destroyed = new AtomicInteger();
        SimpleObjectPool<Object> pool = new SimpleObjectPool<>(
                1, Object::new, obj -> recycled.incrementAndGet(), obj -> destroyed.incrementAndGet());
        PooledObject<Object> pooled = pool.allocate();
        pooled.close();
        pooled.close();
        assertEquals("Pooled object should only be recycled once", 1, recycled.get());
        assertEquals("Pooled object should be queued for reuse, not destroyed", 0, destroyed.get());
    }

    @Test
    public void testCloseMayBeInvokedMultipleTimesWhenObjectIsConsumed() {
        AtomicInteger recycled = new AtomicInteger();
        AtomicInteger destroyed = new AtomicInteger();
        SimpleObjectPool<Object> pool = new SimpleObjectPool<>(
                1, Object::new, obj -> recycled.incrementAndGet(), obj -> destroyed.incrementAndGet());
        PooledObject<Object> initial = pool.allocate();
        PooledObject<Object> pooled = pool.allocate();
        initial.close(); // This object fills the queue so that 'pooled' should be destroyed
        pooled.close();
        pooled.close();
        assertEquals("Each pooled object should be recycled", 2, recycled.get());
        assertEquals("Pooled object should be destroyed exactly once", 1, destroyed.get());
    }
}