/*******************************************************************************
 *    ___                  _   ____  ____
 *   / _ \ _   _  ___  ___| |_|  _ \| __ )
 *  | | | | | | |/ _ \/ __| __| | | |  _ \
 *  | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *   \__\_\\__,_|\___||___/\__|____/|____/
 *
 * Copyright (C) 2014-2017 Appsicle
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

package com.questdb.std;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Iterator;


public class ObjIntHashMap<K> implements Iterable<ObjIntHashMap.Entry<K>>, Mutable {
    private static final int MIN_INITIAL_CAPACITY = 16;
    private static final Object noEntryValue = new Object();
    private final int noKeyValue;
    private final double loadFactor;
    private final EntryIterator iterator = new EntryIterator();
    private K[] keys;
    private int[] values;
    private int free;
    private int capacity;
    private int mask;

    public ObjIntHashMap() {
        this(8);
    }

    private ObjIntHashMap(int initialCapacity) {
        this(initialCapacity, 0.5, -1);
    }

    @SuppressWarnings("unchecked")
    private ObjIntHashMap(int initialCapacity, double loadFactor, int noKeyValue) {
        int capacity = Math.max(initialCapacity, (int) (initialCapacity / loadFactor));
        capacity = capacity < MIN_INITIAL_CAPACITY ? MIN_INITIAL_CAPACITY : Numbers.ceilPow2(capacity);
        this.loadFactor = loadFactor;
        this.noKeyValue = noKeyValue;
        keys = (K[]) new Object[capacity];
        values = new int[capacity];
        free = this.capacity = initialCapacity;
        mask = capacity - 1;
        clear();
    }

    public final void clear() {
        Arrays.fill(keys, noEntryValue);
    }

    public int get(K key) {
        int index = key.hashCode() & mask;

        if (Unsafe.arrayGet(keys, index) == noEntryValue) {
            return noKeyValue;
        }

        if (Unsafe.arrayGet(keys, index) == key || key.equals(Unsafe.arrayGet(keys, index))) {
            return values[index];
        }

        return probe(key, index);
    }

    public Iterable<Entry<K>> immutableIterator() {
        return new EntryIterator();
    }

    @Override
    @NotNull
    public Iterator<Entry<K>> iterator() {
        iterator.index = 0;
        return iterator;
    }

    public void put(K key, int value) {
        int index = key.hashCode() & mask;
        if (cantPutAt(index, key, value)) {
            probeInsert(key, index, value);
        }
    }

    public boolean putIfAbsent(K key, int value) {
        int index = key.hashCode() & mask;
        if (Unsafe.arrayGet(keys, index) == noEntryValue) {
            keys[index] = key;
            values[index] = value;
            free--;
            if (free == 0) {
                rehash();
            }
            return true;
        }

        return Unsafe.arrayGet(keys, index) != key && !key.equals(Unsafe.arrayGet(keys, index)) && probeInsertIfAbsent(key, index, value);
    }

    public int size() {
        return capacity - free;
    }

    private boolean cantPutAt(int index, K key, int value) {
        if (Unsafe.arrayGet(keys, index) == noEntryValue) {
            Unsafe.arrayPut(keys, index, key);
            Unsafe.arrayPut(values, index, value);
            free--;
            if (free == 0) {
                rehash();
            }
            return false;
        }

        if (Unsafe.arrayGet(keys, index) == key || key.equals(Unsafe.arrayGet(keys, index))) {
            Unsafe.arrayPut(values, index, value);
            return false;
        }
        return true;
    }

    private int probe(K key, int index) {
        do {
            index = (index + 1) & mask;
            if (Unsafe.arrayGet(keys, index) == noEntryValue) {
                return noKeyValue;
            }
            if (Unsafe.arrayGet(keys, index) == key || key.equals(Unsafe.arrayGet(keys, index))) {
                return Unsafe.arrayGet(values, index);
            }
        } while (true);
    }

    private void probeInsert(K key, int index, int value) {
        do {
            index = (index + 1) & mask;
        } while (cantPutAt(index, key, value));
    }

    private boolean probeInsertIfAbsent(K key, int index, int value) {
        do {
            index = (index + 1) & mask;
            if (Unsafe.arrayGet(keys, index) == noEntryValue) {
                Unsafe.arrayPut(keys, index, key);
                Unsafe.arrayPut(values, index, value);
                free--;
                if (free == 0) {
                    rehash();
                }
                return true;
            }

            if (Unsafe.arrayGet(keys, index) == key || key.equals(Unsafe.arrayGet(keys, index))) {
                return false;
            }
        } while (true);
    }

    @SuppressWarnings({"unchecked"})
    private void rehash() {

        int newCapacity = values.length << 1;
        mask = newCapacity - 1;
        free = capacity = (int) (newCapacity * loadFactor);
        int[] oldValues = values;
        K[] oldKeys = keys;
        this.keys = (K[]) new Object[newCapacity];
        this.values = new int[newCapacity];
        Arrays.fill(keys, noEntryValue);

        for (int i = oldKeys.length; i-- > 0; ) {
            if (Unsafe.arrayGet(oldKeys, i) != noEntryValue) {
                put(Unsafe.arrayGet(oldKeys, i), Unsafe.arrayGet(oldValues, i));
            }
        }
    }

    public static class Entry<V> {
        public V key;
        public int value;
    }

    public class EntryIterator implements ImmutableIterator<Entry<K>> {

        private final Entry<K> entry = new Entry<>();
        private int index = 0;

        @Override
        public boolean hasNext() {
            return index < values.length && (Unsafe.arrayGet(keys, index) != noEntryValue || scan());
        }

        @Override
        public Entry<K> next() {
            entry.key = Unsafe.arrayGet(keys, index);
            entry.value = Unsafe.arrayGet(values, index++);
            return entry;
        }

        private boolean scan() {
            do {
                index++;
            } while (index < values.length && Unsafe.arrayGet(keys, index) == noEntryValue);

            return index < values.length;
        }
    }
}
