/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.util;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Lock-free secure concurrent hash map.  Attempts to store keys which cause excessive collisions will result in
 * a security exception.
 *
 * @param <K> the key type
 * @param <V> the value type
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@Deprecated
public final class SecureHashMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V> {
    private static final int MAX_ROW_LENGTH = 32;
    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final int MAXIMUM_CAPACITY = 1 << 30;
    private static final float DEFAULT_LOAD_FACTOR = 0.60f;

    /** A row which has been resized into the new view. */
    private static final Item[] RESIZED = new Item[0];
    /** A non-existent table entry (as opposed to a {@code null} value). */
    private static final Object NONEXISTENT = new Object();

    private volatile Table<K, V> table;

    private final Set<K> keySet = new KeySet();
    private final Set<Entry<K, V>> entrySet = new EntrySet();
    private final Collection<V> values = new Values();

    private final float loadFactor;
    private final int initialCapacity;

    @SuppressWarnings("unchecked")
    private static final AtomicIntegerFieldUpdater<Table> sizeUpdater = AtomicIntegerFieldUpdater.newUpdater(Table.class, "size");

    @SuppressWarnings("unchecked")
    private static final AtomicReferenceFieldUpdater<SecureHashMap, Table> tableUpdater = AtomicReferenceFieldUpdater.newUpdater(SecureHashMap.class, Table.class, "table");
    @SuppressWarnings("unchecked")
    private static final AtomicReferenceFieldUpdater<Item, Object> valueUpdater = AtomicReferenceFieldUpdater.newUpdater(Item.class, Object.class, "value");

    /**
     * Construct a new instance.
     *
     * @param initialCapacity the initial capacity
     * @param loadFactor the load factor
     */
    public SecureHashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity must be > 0");
        }
        if (initialCapacity > MAXIMUM_CAPACITY) {
            initialCapacity = MAXIMUM_CAPACITY;
        }
        if (loadFactor <= 0.0 || Float.isNaN(loadFactor) || loadFactor >= 1.0) {
            throw new IllegalArgumentException("Load factor must be between 0.0f and 1.0f");
        }

        int capacity = 1;

        while (capacity < initialCapacity) {
            capacity <<= 1;
        }

        this.loadFactor = loadFactor;
        this.initialCapacity = capacity;

        final Table<K, V> table = new Table<>(capacity, loadFactor);
        tableUpdater.set(this, table);
    }

    /**
     * Construct a new instance.
     *
     * @param loadFactor the load factor
     */
    public SecureHashMap(final float loadFactor) {
        this(DEFAULT_INITIAL_CAPACITY, loadFactor);
    }

    /**
     * Construct a new instance.
     *
     * @param initialCapacity the initial capacity
     */
    public SecureHashMap(final int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Construct a new instance.
     */
    public SecureHashMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    private static int hashOf(final Object key) {
        int h = key.hashCode();
        h += (h <<  15) ^ 0xffffcd7d;
        h ^= (h >>> 10);
        h += (h <<   3);
        h ^= (h >>>  6);
        h += (h <<   2) + (h << 14);
        return h ^ (h >>> 16);
    }

    private static boolean equals(final Object o1, final Object o2) {
        return o1 == null ? o2 == null : o1.equals(o2);
    }

    private Item<K, V>[] addItem(final Item<K, V>[] row, final Item<K, V> newItem) {
        if (row == null) {
            return createRow(newItem);
        } else {
            final int length = row.length;
            if (length > MAX_ROW_LENGTH) {
                throw new SecurityException("Excessive map collisions");
            }
            Item<K, V>[] newRow = Arrays.copyOf(row, length + 1);
            newRow[length] = newItem;
            return newRow;
        }
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Item<K, V>[] createRow(final Item<K, V> newItem) {
        return new Item[] { newItem };
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Item<K, V>[] createRow(final int length) {
        return new Item[length];
    }

    private V doPut(K key, V value, boolean ifAbsent, Table<K, V> table) {
        final int hashCode = hashOf(key);
        final AtomicReferenceArray<Item<K, V>[]> array = table.array;
        final int idx = hashCode & array.length() - 1;

        OUTER: for (;;) {

            // Fetch the table row.
            Item<K, V>[] oldRow = array.get(idx);
            if (oldRow == RESIZED) {
                // row was transported to the new table so recalculate everything
                final V result = doPut(key, value, ifAbsent, table.resizeView);
                // keep a consistent size view though!
                if (result == NONEXISTENT) sizeUpdater.getAndIncrement(table);
                return result;
            }
            if (oldRow != null) {
                // Find the matching Item in the row.
                Item<K, V> oldItem = null;
                for (Item<K, V> tryItem : oldRow) {
                    if (equals(key, tryItem.key)) {
                        oldItem = tryItem;
                        break;
                    }
                }
                if (oldItem != null) {
                    // entry exists; try to return the old value and try to replace the value if allowed.
                    V oldItemValue;
                    do {
                        oldItemValue = oldItem.value;
                        if (oldItemValue == NONEXISTENT) {
                            // Key was removed; on the next iteration or two the doornail should be gone.
                            continue OUTER;
                        }
                    } while (! ifAbsent && ! valueUpdater.compareAndSet(oldItem, oldItemValue, value));
                    return oldItemValue;
                }
                // Row exists but item doesn't.
            }

            // Row doesn't exist, or row exists but item doesn't; try and add a new item to the row.
            final Item<K, V> newItem = new Item<>(key, hashCode, value);
            final Item<K, V>[] newRow = addItem(oldRow, newItem);
            if (! array.compareAndSet(idx, oldRow, newRow)) {
                // Nope, row changed; retry.
                continue;
            }

            // Up the table size.
            final int threshold = table.threshold;
            int newSize = sizeUpdater.incrementAndGet(table);
            // >= 0 is really a sign-bit check
            while (newSize >= 0 && (newSize & 0x7fffffff) > threshold) {
                if (sizeUpdater.compareAndSet(table, newSize, newSize | 0x80000000)) {
                    resize(table);
                    return nonexistent();
                }
                newSize = sizeUpdater.get(table);
            }
            // Success.
            return nonexistent();
        }
    }

    private void resize(Table<K, V> origTable) {
        final AtomicReferenceArray<Item<K, V>[]> origArray = origTable.array;
        final int origCapacity = origArray.length();
        final Table<K, V> newTable = new Table<>(origCapacity << 1, loadFactor);
        // Prevent resize until we're done...
        newTable.size = 0x80000000;
        origTable.resizeView = newTable;
        final AtomicReferenceArray<Item<K, V>[]> newArray = newTable.array;

        for (int i = 0; i < origCapacity; i ++) {
            // for each row, try to resize into two new rows
            Item<K, V>[] origRow, newRow0, newRow1;
            do {
                origRow = origArray.get(i);
                if (origRow != null) {
                    int count0 = 0, count1 = 0;
                    for (Item<K, V> item : origRow) {
                        if ((item.hashCode & origCapacity) == 0) {
                            count0++;
                        } else {
                            count1++;
                        }
                    }
                    if (count0 != 0) {
                        newRow0 = createRow(count0);
                        int j = 0;
                        for (Item<K, V> item : origRow) {
                            if ((item.hashCode & origCapacity) == 0) {
                                newRow0[j++] = item;
                            }
                        }
                        newArray.lazySet(i, newRow0);
                    }
                    if (count1 != 0) {
                        newRow1 = createRow(count1);
                        int j = 0;
                        for (Item<K, V> item : origRow) {
                            if ((item.hashCode & origCapacity) != 0) {
                                newRow1[j++] = item;
                            }
                        }
                        newArray.lazySet(i + origCapacity, newRow1);
                    }
                }
            } while (! origArray.compareAndSet(i, origRow, SecureHashMap.<K, V>resized()));
            if (origRow != null) sizeUpdater.getAndAdd(newTable, origRow.length);
        }

        int size;
        do {
            size = newTable.size;
            if ((size & 0x7fffffff) >= newTable.threshold) {
                // shorter path for reads and writes
                table = newTable;
                // then time for another resize, right away
                resize(newTable);
                return;
            }
        } while (!sizeUpdater.compareAndSet(newTable, size, size & 0x7fffffff));

        // All done, plug in the new table
        table = newTable;
    }

    private static <K, V> Item<K, V>[] remove(Item<K, V>[] row, int idx) {
        final int len = row.length;
        assert idx < len;
        if (len == 1) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Item<K, V>[] newRow = new Item[len - 1];
        if (idx > 0) {
            System.arraycopy(row, 0, newRow, 0, idx);
        }
        if (idx < len - 1) {
            System.arraycopy(row, idx + 1, newRow, idx, len - 1 - idx);
        }
        return newRow;
    }

    public V putIfAbsent(final K key, final V value) {
        final V result = doPut(key, value, true, table);
        return result == NONEXISTENT ? null : result;
    }

    public boolean remove(final Object objectKey, final Object objectValue) {
        // Get type-safe key and value.
        @SuppressWarnings("unchecked")
        final K key = (K) objectKey;
        @SuppressWarnings("unchecked")
        final V value = (V) objectValue;
        return doRemove(key, value, table);
    }

    private boolean doRemove(final Item<K, V> item, final Table<K, V> table) {
        int hashCode = item.hashCode;

        final AtomicReferenceArray<Item<K, V>[]> array = table.array;
        final int idx = hashCode & array.length() - 1;

        Item<K, V>[] oldRow;

        for (;;) {
            oldRow = array.get(idx);
            if (oldRow == null) {
                return false;
            }
            if (oldRow == RESIZED) {
                boolean result;
                if (result = doRemove(item, table.resizeView)) {
                    sizeUpdater.getAndDecrement(table);
                }
                return result;
            }

            int rowIdx = -1;
            for (int i = 0; i < oldRow.length; i ++) {
                if (item == oldRow[i]) {
                    rowIdx = i;
                    break;
                }
            }
            if (rowIdx == -1) {
                return false;
            }
            if (array.compareAndSet(idx, oldRow, remove(oldRow, rowIdx))) {
                sizeUpdater.getAndDecrement(table);
                return true;
            }
            // row changed, cycle back again
        }
    }

    private boolean doRemove(final K key, final V value, final Table<K, V> table) {

        final int hashCode = hashOf(key);

        final AtomicReferenceArray<Item<K, V>[]> array = table.array;
        final int idx = hashCode & array.length() - 1;

        Item<K, V>[] oldRow;

        // Fetch the table row.
        oldRow = array.get(idx);
        if (oldRow == null) {
            // no match for the key
            return false;
        }
        if (oldRow == RESIZED) {
            boolean result;
            if (result = doRemove(key, value, table.resizeView)) {
                // keep size consistent
                sizeUpdater.getAndDecrement(table);
            }
            return result;
        }

        // Find the matching Item in the row.
        Item<K, V> oldItem = null;
        V oldValue = null;
        int rowIdx = -1;
        for (int i = 0; i < oldRow.length; i ++) {
            Item<K, V> tryItem = oldRow[i];
            if (equals(key, tryItem.key)) {
                if (equals(value, oldValue = tryItem.value)) {
                    oldItem = tryItem;
                    rowIdx = i;
                    break;
                } else {
                    // value doesn't match; exit without changing map.
                    return false;
                }
            }
        }
        if (oldItem == null) {
            // no such entry exists.
            return false;
        }

        while (! valueUpdater.compareAndSet(oldItem, oldValue, NONEXISTENT)) {
            if (equals(value, oldValue = oldItem.value)) {
                // Values are equal; try marking it as removed again.
                continue;
            }
            // Value was changed to a non-equal value.
            return false;
        }

        // Now we are free to remove the item from the row.
        if (array.compareAndSet(idx, oldRow, remove(oldRow, rowIdx))) {
            // Adjust the table size, since we are definitely the ones to be removing this item from the table.
            sizeUpdater.decrementAndGet(table);
            return true;
        } else {
            // The old row changed so retry by the other algorithm
            return doRemove(oldItem, table);
        }
    }

    @SuppressWarnings("unchecked")
    public V remove(final Object objectKey) {
        final V result = doRemove((K) objectKey, table);
        return result == NONEXISTENT ? null : result;
    }

    private V doRemove(final K key, final Table<K, V> table) {
        final int hashCode = hashOf(key);

        final AtomicReferenceArray<Item<K, V>[]> array = table.array;
        final int idx = hashCode & array.length() - 1;

        // Fetch the table row.
        Item<K, V>[] oldRow = array.get(idx);
        if (oldRow == null) {
            // no match for the key
            return nonexistent();
        }
        if (oldRow == RESIZED) {
            V result;
            if ((result = doRemove(key, table.resizeView)) != NONEXISTENT) {
                // keep size consistent
                sizeUpdater.getAndDecrement(table);
            }
            return result;
        }

        // Find the matching Item in the row.
        Item<K, V> oldItem = null;
        int rowIdx = -1;
        for (int i = 0; i < oldRow.length; i ++) {
            Item<K, V> tryItem = oldRow[i];
            if (equals(key, tryItem.key)) {
                oldItem = tryItem;
                rowIdx = i;
                break;
            }
        }
        if (oldItem == null) {
            // no such entry exists.
            return nonexistent();
        }

        // Mark the item as "removed".
        @SuppressWarnings("unchecked")
        V oldValue = (V) valueUpdater.getAndSet(oldItem, NONEXISTENT);
        if (oldValue == NONEXISTENT) {
            // Someone else beat us to it.
            return nonexistent();
        }

        // Now we are free to remove the item from the row.
        if (array.compareAndSet(idx, oldRow, remove(oldRow, rowIdx))) {
            // Adjust the table size, since we are definitely the ones to be removing this item from the table.
            sizeUpdater.decrementAndGet(table);

            // Item is removed from the row; we are done here.
            return oldValue;
        } else {
            boolean result = doRemove(oldItem, table);
            assert result;
            return oldValue;
        }
    }

    @SuppressWarnings("unchecked")
    private static <V> V nonexistent() {
        return (V) NONEXISTENT;
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Item<K, V>[] resized() {
        return (Item<K, V>[]) RESIZED;
    }

    public boolean replace(final K key, final V oldValue, final V newValue) {
        return doReplace(key, oldValue, newValue, table);
    }

    private boolean doReplace(final K key, final V oldValue, final V newValue, final Table<K, V> table) {
        final int hashCode = hashOf(key);
        final AtomicReferenceArray<Item<K, V>[]> array = table.array;
        final int idx = hashCode & array.length() - 1;

        // Fetch the table row.
        Item<K, V>[] oldRow = array.get(idx);
        if (oldRow == null) {
            // no match for the key
            return false;
        }
        if (oldRow == RESIZED) {
            return doReplace(key, oldValue, newValue, table.resizeView);
        }

        // Find the matching Item in the row.
        Item<K, V> oldItem = null;
        V oldRowValue = null;
        for (Item<K, V> tryItem : oldRow) {
            if (equals(key, tryItem.key)) {
                if (equals(oldValue, oldRowValue = tryItem.value)) {
                    oldItem = tryItem;
                    break;
                } else {
                    // value doesn't match; exit without changing map.
                    return false;
                }
            }
        }
        if (oldItem == null) {
            // no such entry exists.
            return false;
        }

        // Now swap the item.
        while (! valueUpdater.compareAndSet(oldItem, oldRowValue, newValue)) {
            if (equals(oldValue, oldRowValue = oldItem.value)) {
                // Values are equal; try swapping it again.
                continue;
            }
            // Value was changed to a non-equal value.
            return false;
        }

        // Item is swapped; we are done here.
        return true;
    }

    public V replace(final K key, final V value) {
        final V result = doReplace(key, value, table);
        return result == NONEXISTENT ? null : result;
    }

    private V doReplace(final K key, final V value, final Table<K, V> table) {
        final int hashCode = hashOf(key);
        final AtomicReferenceArray<Item<K, V>[]> array = table.array;
        final int idx = hashCode & array.length() - 1;

        // Fetch the table row.
        Item<K, V>[] oldRow = array.get(idx);
        if (oldRow == null) {
            // no match for the key
            return nonexistent();
        }
        if (oldRow == RESIZED) {
            return doReplace(key, value, table.resizeView);
        }

        // Find the matching Item in the row.
        Item<K, V> oldItem = null;
        for (Item<K, V> tryItem : oldRow) {
            if (equals(key, tryItem.key)) {
                oldItem = tryItem;
                break;
            }
        }
        if (oldItem == null) {
            // no such entry exists.
            return nonexistent();
        }

        // Now swap the item.
        @SuppressWarnings("unchecked")
        V oldRowValue = (V) valueUpdater.getAndSet(oldItem, value);
        if (oldRowValue == NONEXISTENT) {
            // Item was removed.
            return nonexistent();
        }

        // Item is swapped; we are done here.
        return oldRowValue;
    }

    public int size() {
        return table.size & 0x7fffffff;
    }

    private V doGet(final Table<K, V> table, final K key) {
        final AtomicReferenceArray<Item<K, V>[]> array = table.array;
        final Item<K, V>[] row = array.get(hashOf(key) & (array.length() - 1));
        if(row != null) {
            for (Item<K, V> item : row) {
                if (equals(key, item.key)) {
                    return item.value;
                }
            }
        }
        return nonexistent();
    }

    public boolean containsKey(final Object key) {
        @SuppressWarnings("unchecked")
        final V value = doGet(table, (K) key);
        return value != NONEXISTENT;
    }

    public V get(final Object key) {
        @SuppressWarnings("unchecked")
        final V value = doGet(table, (K) key);
        return value == NONEXISTENT ? null : value;
    }

    public V put(final K key, final V value) {
        final V result = doPut(key, value, false, table);
        return result == NONEXISTENT ? null : result;
    }

    public void clear() {
        table = new Table<>(initialCapacity, loadFactor);
    }

    public Set<Entry<K, V>> entrySet() {
        return entrySet;
    }

    public Collection<V> values() {
        return values;
    }

    public Set<K> keySet() {
        return keySet;
    }

    final class KeySet extends AbstractSet<K> implements Set<K> {

        public void clear() {
            SecureHashMap.this.clear();
        }

        public boolean contains(final Object o) {
            return containsKey(o);
        }

        @SuppressWarnings("unchecked")
        public boolean remove(final Object o) {
            return doRemove((K) o, table) != NONEXISTENT;
        }

        public Iterator<K> iterator() {
            return new KeyIterator();
        }

        public boolean add(final K k) {
            return doPut(k, null, true, table) == NONEXISTENT;
        }

        public int size() {
            return SecureHashMap.this.size();
        }
    }

    final class Values extends AbstractCollection<V> implements Collection<V> {

        public void clear() {
            SecureHashMap.this.clear();
        }

        public boolean contains(final Object o) {
            return containsValue(o);
        }

        public Iterator<V> iterator() {
            return new ValueIterator();
        }

        public int size() {
            return SecureHashMap.this.size();
        }
    }

    final class EntrySet extends AbstractSet<Entry<K, V>> implements Set<Entry<K, V>> {

        public Iterator<Entry<K, V>> iterator() {
            return new EntryIterator();
        }

        public boolean add(final Entry<K, V> entry) {
            return doPut(entry.getKey(), entry.getValue(), true, table) == NONEXISTENT;
        }

        @SuppressWarnings("unchecked")
        public boolean remove(final Object o) {
            return o instanceof Entry && remove((Entry<K, V>) o);
        }

        public boolean remove(final Entry<K, V> entry) {
            return doRemove(entry.getKey(), entry.getValue(), table);
        }

        public void clear() {
            SecureHashMap.this.clear();
        }

        @SuppressWarnings("unchecked")
        public boolean contains(final Object o) {
            return o instanceof Entry && contains((Entry<K, V>) o);
        }

        public boolean contains(final Entry<K, V> entry) {
            final V tableValue = doGet(table, entry.getKey());
            final V entryValue = entry.getValue();
            return tableValue == null ? entryValue == null : tableValue.equals(entryValue);
        }

        public int size() {
            return SecureHashMap.this.size();
        }
    }

    abstract class TableIterator implements Iterator<Entry<K, V>> {
        public abstract Item<K, V> next();

        abstract V nextValue();
    }

    final class RowIterator extends TableIterator {
        private final Table<K, V> table;
        final Item<K, V>[] row;

        private int idx;
        private Item<K, V> next;
        private Item<K, V> remove;

        RowIterator(final Table<K, V> table, final Item<K, V>[] row) {
            this.table = table;
            this.row = row;
        }

        public boolean hasNext() {
            if (next == null) {
                final Item<K, V>[] row = this.row;
                if (row == null || idx == row.length) {
                    return false;
                }
                next = row[idx++];
            }
            return true;
        }

        V nextValue() {
            V value;
            do {
                if (next == null) {
                    final Item<K, V>[] row = this.row;
                    if (row == null || idx == row.length) {
                        return nonexistent();
                    }
                    next = row[idx++];
                }
                value = next.value;
            } while (value == NONEXISTENT);
            next = null;
            return value;
        }

        public Item<K, V> next() {
            if (hasNext()) try {
                return next;
            } finally {
                remove = next;
                next = null;
            }
            throw new NoSuchElementException();
        }

        public void remove() {
            final Item<K, V> remove = this.remove;
            if (remove == null) {
                throw new IllegalStateException("next() not yet called");
            }
            if (valueUpdater.getAndSet(remove, NONEXISTENT) == NONEXISTENT) {
                // someone else beat us to it; this is idempotent-ish
                return;
            }
            // item guaranteed to be in the map... somewhere
            this.remove = null;
            doRemove(remove, table);
        }
    }

    final class BranchIterator extends TableIterator {
        private final TableIterator branch0;
        private final TableIterator branch1;

        private boolean branch;

        BranchIterator(final TableIterator branch0, final TableIterator branch1) {
            this.branch0 = branch0;
            this.branch1 = branch1;
        }

        public boolean hasNext() {
            return branch0.hasNext() || branch1.hasNext();
        }

        public Item<K, V> next() {
            if (branch) {
                return branch1.next();
            }
            if (branch0.hasNext()) {
                return branch0.next();
            }
            branch = true;
            return branch1.next();
        }

        V nextValue() {
            if (branch) {
                return branch1.nextValue();
            }
            V value = branch0.nextValue();
            if (value != NONEXISTENT) {
                return value;
            }
            branch = true;
            return branch1.nextValue();
        }

        public void remove() {
            if (branch) {
                branch0.remove();
            } else {
                branch1.remove();
            }
        }
    }

    private TableIterator createRowIterator(Table<K, V> table, int rowIdx) {
        final AtomicReferenceArray<Item<K, V>[]> array = table.array;
        final Item<K, V>[] row = array.get(rowIdx);
        if (row == RESIZED) {
            final Table<K, V> resizeView = table.resizeView;
            return new BranchIterator(createRowIterator(resizeView, rowIdx), createRowIterator(resizeView, rowIdx + array.length()));
        } else {
            return new RowIterator(table, row);
        }
    }

    final class EntryIterator implements Iterator<Entry<K, V>> {
        private final Table<K, V> table = SecureHashMap.this.table;
        private TableIterator tableIterator;
        private TableIterator removeIterator;
        private int tableIdx;
        private Item<K, V> next;

        public boolean hasNext() {
            while (next == null) {
                if (tableIdx == table.array.length()) {
                    return false;
                }
                if (tableIterator == null) {
                    int rowIdx = tableIdx++;
                    if (table.array.get(rowIdx) != null) {
                        tableIterator = createRowIterator(table, rowIdx);
                    }
                }
                if (tableIterator != null) {
                    if (tableIterator.hasNext()) {
                        next = tableIterator.next();
                        return true;
                    } else {
                        tableIterator = null;
                    }
                }
            }
            return true;
        }

        public Entry<K, V> next() {
            if (hasNext()) try {
                return next;
            } finally {
                removeIterator = tableIterator;
                next = null;
            }
            throw new NoSuchElementException();
        }

        public void remove() {
            final TableIterator removeIterator = this.removeIterator;
            if (removeIterator == null) {
                throw new IllegalStateException();
            } else try {
                removeIterator.remove();
            } finally {
                this.removeIterator = null;
            }
        }
    }

    final class KeyIterator implements Iterator<K> {
        private final Table<K, V> table = SecureHashMap.this.table;
        private TableIterator tableIterator;
        private TableIterator removeIterator;
        private int tableIdx;
        private Item<K, V> next;

        public boolean hasNext() {
            while (next == null) {
                if (tableIdx == table.array.length() && tableIterator == null) {
                    return false;
                }
                if (tableIterator == null) {
                    int rowIdx = tableIdx++;
                    if (table.array.get(rowIdx) != null) {
                        tableIterator = createRowIterator(table, rowIdx);
                    }
                }
                if (tableIterator != null) {
                    if (tableIterator.hasNext()) {
                        next = tableIterator.next();
                        return true;
                    } else {
                        tableIterator = null;
                    }
                }
            }
            return true;
        }

        public K next() {
            if (hasNext()) try {
                return next.key;
            } finally {
                removeIterator = tableIterator;
                next = null;
            }
            throw new NoSuchElementException();
        }

        public void remove() {
            final TableIterator removeIterator = this.removeIterator;
            if (removeIterator == null) {
                throw new IllegalStateException();
            } else try {
                removeIterator.remove();
            } finally {
                this.removeIterator = null;
            }
        }
    }

    final class ValueIterator implements Iterator<V> {
        private final Table<K, V> table = SecureHashMap.this.table;
        private TableIterator tableIterator;
        private TableIterator removeIterator;
        private int tableIdx;
        private V next = nonexistent();

        public boolean hasNext() {
            while (next == NONEXISTENT) {
                if (tableIdx == table.array.length() && tableIterator == null)  {
                    return false;
                }
                if (tableIterator == null) {
                    int rowIdx = tableIdx++;
                    if (table.array.get(rowIdx) != null) {
                        tableIterator = createRowIterator(table, rowIdx);
                    }
                }
                if (tableIterator != null) {
                    next = tableIterator.nextValue();
                    if (next == NONEXISTENT) {
                        tableIterator = null;
                    }
                }
            }
            return true;
        }

        public V next() {
            if (hasNext()) try {
                return next;
            } finally {
                removeIterator = tableIterator;
                next = nonexistent();
            }
            throw new NoSuchElementException();
        }

        public void remove() {
            final TableIterator removeIterator = this.removeIterator;
            if (removeIterator == null) {
                throw new IllegalStateException();
            } else try {
                removeIterator.remove();
            } finally {
                this.removeIterator = null;
            }
        }
    }

    static final class Table<K, V> {
        final AtomicReferenceArray<Item<K, V>[]> array;
        final int threshold;
        /** Bits 0-30 are size; bit 31 is 1 if the table is being resized. */
        volatile int size;
        volatile Table<K, V> resizeView;

        private Table(int capacity, float loadFactor) {
            array = new AtomicReferenceArray<>(capacity);
            threshold = capacity == MAXIMUM_CAPACITY ? Integer.MAX_VALUE : (int)(capacity * loadFactor);
        }
    }

    static final class Item<K, V> implements Entry<K, V> {
        private final K key;
        private final int hashCode;
        volatile V value;

        Item(final K key, final int hashCode, final V value) {
            this.key = key;
            this.hashCode = hashCode;
            //noinspection ThisEscapedInObjectConstruction
            valueUpdater.lazySet(this, value);
        }

        public K getKey() {
            return key;
        }

        public V getValue() {
            V value = this.value;
            if (value == NONEXISTENT) {
                throw new IllegalStateException("Already removed");
            }
            return value;
        }

        public V setValue(final V value) {
            V oldValue;
            do {
                oldValue = this.value;
                if (oldValue == NONEXISTENT) {
                    throw new IllegalStateException("Already removed");
                }
            } while (! valueUpdater.compareAndSet(this, oldValue, value));
            return oldValue;
        }

        public int hashCode() {
            return hashCode;
        }

        public boolean equals(final Object obj) {
            return obj instanceof Item && equals((Item<?,?>) obj);
        }

        public boolean equals(final Item<?, ?> obj) {
            return obj != null && hashCode == obj.hashCode && key.equals(obj.key);
        }
    }
}
