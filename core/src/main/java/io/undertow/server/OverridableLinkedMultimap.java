package io.undertow.server;

import com.google.common.collect.ForwardingListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

class OverridableLinkedMultimap<K, V> extends ForwardingListMultimap<K, V> {

    private final ListMultimap<K, V> target;

    OverridableLinkedMultimap(final ListMultimap<K, V> target) {
        this.target = target;
    }

    @Override
    protected ListMultimap<K, V> delegate() {
        return this.target;
    }

    @Override
    public boolean put(K key, V value) {
        if (super.containsEntry(key, value)) {
            // TODO: add proper insertion, this will add at end, not replace in place
            super.remove(key, value);
        }
        return super.put(key, value);
    }

    @Override
    public boolean putAll(K key, Iterable<? extends V> values) {
        // TODO: add proper insertion, this will add at end, not replace in place
        //return super.putAll(key, values);
        throw new RuntimeException();
    }

    @Override
    public boolean putAll(Multimap<? extends K, ? extends V> multimap) {
        // TODO: add proper insertion, this will add at end, not replace in place
        //return super.putAll(multimap);
        throw new RuntimeException();
    }

}
