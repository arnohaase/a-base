package com.ajjpj.afoundation.collection.immutable;

import com.ajjpj.afoundation.collection.AEquality;
import com.ajjpj.afoundation.function.AFunction1;

import java.io.Serializable;
import java.util.*;


/**
 * This is an {@link com.ajjpj.afoundation.collection.immutable.AHashMap} that is specialized for keys of type long, i.e. primitive numbers rather than objects. It duplicates its API
 *  to support both efficient primitive 'long' values and generified 'Long's,
 *
 * @author arno
 */
public class ALongHashMap<V> extends AbstractAMap<Long,V> {
    private static final int LEVEL_INCREMENT = 10;

    transient private Integer cachedHashcode = null; // intentionally not volatile: This class is immutable, so recalculating per thread works

    private static final ALongHashMap EMPTY = new ALongHashMap ();


    /**
     * Returns an empty ALongHashMap instance. Calling this factory method instead of
     *  the constructor allows internal reuse of empty map instances since they are immutable.
     */
    @SuppressWarnings("unchecked")
    public static <V> ALongHashMap<V> empty() {
        return EMPTY;
    }

    public static <V> ALongHashMap<V> fromJavaUtilMap(Map<? extends Number,V> map) {
        ALongHashMap<V> result = empty ();

        for(Map.Entry<? extends Number,V> entry: map.entrySet()) {
            result = result.updated(entry.getKey().longValue (), entry.getValue());
        }

        return result;
    }

    /**
     * Returns an ALongHashMap initialized from separate 'keys' and 'values' collections. Both collections
     *  are iterated exactly once and are expected to have the same size.
     */
    public static <V> ALongHashMap<V> fromKeysAndValues(Iterable<? extends Number> keys, Iterable<V> values) {
        final Iterator<? extends Number> ki = keys.iterator();
        final Iterator<V> vi = values.iterator();

        ALongHashMap<V> result = ALongHashMap.empty ();

        while(ki.hasNext()) {
            final Number key = ki.next();
            final V value = vi.next();

            result = result.updated(key.longValue (), value);
        }
        return result;
    }

    /**
     * Returns an ALongHashMap instance initialized from a collection of
     *  keys and a function. For each element of the <code>keys</code> collection, the function is called once to
     *  determine the corresponding value, and the pair is then stored in the map.
     */
    @SuppressWarnings("unused")
    public static <K extends Number, V, E extends Throwable> ALongHashMap<V> fromKeysAndFunction(Iterable<K> keys, AFunction1<? super K, ? extends V, E> f) throws E {
        final Iterator<K> ki = keys.iterator();

        ALongHashMap<V> result = empty ();

        while(ki.hasNext()) {
            final K key = ki.next();
            final V value = f.apply(key);

            result = result.updated(key.longValue (), value);
        }
        return result;
    }

    private ALongHashMap () {
    }

    @Override public AEquality keyEquality () {
        return AEquality.NATURAL_ORDER;
    }

    @Override public AMap<Long, V> clear () {
        return null;
    }

    @Override public int size() {
        return 0;
    }

    public boolean containsKey(long key) {
        return get(key).isDefined();
    }

    @Override public AOption<V> get (Long key) {
        return get (key.longValue ());
    }
    public AOption<V> get(long key) {
        return doGet(key, computeHash(key), 0);
    }

    @Override public V getRequired (Long key) {
        return getRequired (key.longValue());
    }
    public V getRequired (long key) {
        return get(key).get();
    }

    @Override public ALongHashMap<V> updated (Long key, V value) {
        return updated (key.longValue (), value);
    }
    public ALongHashMap<V> updated (long key, V value) {
        return doUpdated(key, computeHash(key), 0, value);
    }

    @Override public ALongHashMap<V> removed (Long key) {
        return removed (key.longValue ());
    }
    public ALongHashMap<V> removed (long key) {
        return doRemoved(key, computeHash(key), 0);
    }

    @Override
    public Iterator<AMapEntry<Long, V>> iterator() {
        return new Iterator<AMapEntry<Long, V>> () {
            final ALongMapIterator<V> inner = longIterator ();

            @Override public boolean hasNext () {
                return inner.hasNext ();
            }

            @Override public AMapEntry<Long, V> next () {
                inner.next ();
                return inner;
            }

            @Override public void remove () {
                throw new UnsupportedOperationException ();
            }
        };
    }

    public ALongMapIterator<V> longIterator() { //TODO test this - and all map iterator() implementations
        return new LongIteratorImpl<> (this);
    }

    @Override public ASet<Long> keys() {
        return new ALongHashSet (this);
    }

    /**
     * @param level number of least significant bits of the hash to discard for local hash lookup. This mechanism
     *              is used to create a 64-way hash trie - level increases by 10 at each level
     */
    AOption<V> doGet(long key, long hash, int level) {
        return AOption.none();
    }

    ALongHashMap<V> doUpdated(long key, long hash, int level, V value) {
        return new LongHashMap1<> (key, hash, value);
    }

    ALongHashMap<V> doRemoved(long key, long hash, int level) {
        return this;
    }

    private static long computeHash (long key) { //TODO better spread function?
        long h = key;
        h = h + ~(h << 18);
        h = h ^ (h >>> 28);
        h = h + (h << 8);
        return h ^ (h >>> 20);
    }

    @SuppressWarnings("unchecked")
    private static <V> ALongHashMap<V>[] createArray(int size) {
        return new ALongHashMap[size];
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder("{");
        boolean first = true;

        for(AMapEntry<Long, V> e: this) {
            if(first) {
                first = false;
            }
            else {
                result.append(", ");
            }

            result.append(e.getKey ()).append("->").append(e.getValue());
        }

        result.append("}");
        return result.toString();
    }

    /**
     * very internal method. It assumes hash0 != hash1.
     */
    private static<V> LongHashTrieMap<V> mergeLeafMaps (long hash0, ALongHashMap<V> elem0, long hash1, ALongHashMap<V> elem1, int level, int size) {
        final int index0 = (int) ((hash0 >>> level) & 0x3f);
        final int index1 = (int) ((hash1 >>> level) & 0x3f);
        if(index0 != index1) {
            final long bitmap = (1L << index0) | (1L << index1);
            final ALongHashMap<V>[] elems = createArray(2);
            if(index0 < index1) {
                elems[0] = elem0;
                elems[1] = elem1;
            }
            else {
                elems[0] = elem1;
                elems[1] = elem0;
            }
            return new LongHashTrieMap<>(bitmap, elems, size);
        }
        else {
            final ALongHashMap<V>[] elems = createArray(1);
            final long bitmap = (1L << index0);
            // try again, based on the
            elems[0] = mergeLeafMaps(hash0, elem0, hash1, elem1, level + LEVEL_INCREMENT, size);
            return new LongHashTrieMap<>(bitmap, elems, size);
        }
    }

    static class LongIteratorImpl<V> implements ALongMapIterator<V> { //TODO test this - and all 'iterator()' methods for all maps
        private final Deque<ALongHashMap<V>> stack = new ArrayDeque<> ();
        private LongHashMap1<V> current;

        LongIteratorImpl (ALongHashMap<V> root) {
            if (root.nonEmpty ()) {
                stack.push (root);
            }
        }

        @Override public boolean hasNext () {
            return !stack.isEmpty ();
        }

        @Override public void next () {
            ALongHashMap<V> next;

            try {
                while (true) {
                    next = stack.pop ();
                    if (next.getClass () == LongHashMap1.class) {
                        current = (LongHashMap1<V>) next;
                        break;
                    }

                    for (ALongHashMap<V> child: ((LongHashTrieMap<V>) next).elems) {
                        stack.push (child);
                    }
                }
            }
            catch (Exception e) { // avoid the 'empty' check to put the penalty on the exceptional rather than the regular case
                current = null;
                throw new NoSuchElementException ();
            }
        }

        @Override public long getLongKey () {
            try {
                return current.key;
            }
            catch (Exception e) { // avoid the 'null' check to put the penalty on the exceptional rather than the regular case
                throw new NoSuchElementException ();
            }
        }

        @Override public Long getKey () {
            return getLongKey ();
        }

        @Override public V getValue () {
            try {
                return current.value;
            }
            catch (Exception e) { // avoid the 'null' check to put the penalty on the exceptional rather than the regular case
                throw new NoSuchElementException ();
            }
        }
    }

    static class LongHashMap1<V> extends ALongHashMap<V> {
        private final long key;
        private final long hash;
        private final V value;

        LongHashMap1(long key, long hash, V value) {
            super();

            this.key = key;
            this.hash = hash;
            this.value = value;
        }

        @Override public int size() {
            return 1;
        }

        @Override AOption<V> doGet(long key, long hash, int level) {
            if(this.key == key) {
                return AOption.some (value);
            }
            return AOption.none();
        }

        @Override ALongHashMap<V> doUpdated(long key, long hash, int level, V value) {
            if (key == this.key) {
                if(this.value == value) {
                    return this;
                }
                else {
                    return new LongHashMap1<>(key, hash, value);
                }
            }
            else {
                // find a level where they don't collide
                final ALongHashMap<V> that = new LongHashMap1<>(key, hash, value);
                return mergeLeafMaps(this.hash, this, hash, that, level, 2);
            }
        }

        @Override ALongHashMap<V> doRemoved(long key, long hash, int level) {
            if (key == this.key) {
                return empty();
            }
            else {
                return this;
            }
        }
    }


    static class LongHashTrieMap<V> extends ALongHashMap<V> {
        final long bitmap;
        final ALongHashMap<V>[] elems;
        final int size;

        LongHashTrieMap (long bitmap, ALongHashMap<V>[] elems, int size) {
            this.bitmap = bitmap;
            this.elems = elems;
            this.size = size;
        }

        @Override public int size() {
            return size;
        }

        @Override
        AOption<V> doGet(long key, long hash, int level) {
            final int index = (int) ((hash >>> level) & 0x3f);

            if (bitmap == - 1) {
                return elems[index & 0x3f].doGet(key, hash, level + LEVEL_INCREMENT);
            }

            final long mask = 1L << index;
            if ((bitmap & mask) != 0) {
                final int offset = Long.bitCount (bitmap & (mask - 1));
                return elems[offset].doGet(key, hash, level + LEVEL_INCREMENT);
            }

            return AOption.none();
        }

        @Override ALongHashMap<V> doUpdated(long key, long hash, int level, V value) {
            final int index = (int) ((hash >>> level) & 0x3f);
            final long mask = (1L << index);
            final int offset = Long.bitCount(bitmap & (mask - 1));

            if ((bitmap & mask) != 0) {
                final ALongHashMap<V> sub = elems[offset];

                final ALongHashMap<V> subNew = sub.doUpdated(key, hash, level + LEVEL_INCREMENT, value);
                if(subNew == sub) {
                    return this;
                }
                else {
                    final ALongHashMap<V>[] elemsNew = createArray(elems.length);
                    System.arraycopy(elems, 0, elemsNew, 0, elems.length);
                    elemsNew[offset] = subNew;
                    return new LongHashTrieMap<> (bitmap, elemsNew, size + (subNew.size() - sub.size()));
                }
            }
            else {
                final ALongHashMap<V>[] elemsNew = createArray(elems.length + 1);
                System.arraycopy(elems, 0, elemsNew, 0, offset);
                elemsNew[offset] = new LongHashMap1<>(key, hash, value);
                System.arraycopy(elems, offset, elemsNew, offset + 1, elems.length - offset);
                return new LongHashTrieMap<>(bitmap | mask, elemsNew, size + 1);
            }
        }

        @Override ALongHashMap<V> doRemoved (long key, long hash, int level) {
            final int index = (int) ((hash >>> level) & 0x3f);
            final long mask = 1L << index;
            final int  offset = Long.bitCount (bitmap & (mask - 1));

            if ((bitmap & mask) != 0) {
                final ALongHashMap<V> sub = elems[offset];
                final ALongHashMap<V> subNew = sub.doRemoved(key, hash, level + LEVEL_INCREMENT);

                if (subNew == sub) {
                    return this;
                }
                else if (subNew.isEmpty()) {
                    final long bitmapNew = bitmap ^ mask;
                    if (bitmapNew != 0) {
                        final ALongHashMap<V>[] elemsNew = createArray(elems.length - 1);
                        System.arraycopy(elems, 0, elemsNew, 0, offset);
                        System.arraycopy(elems, offset + 1, elemsNew, offset, elems.length - offset - 1);
                        final int sizeNew = size - sub.size();
                        if (elemsNew.length == 1 && ! (elemsNew[0] instanceof LongHashTrieMap)) {
                            return elemsNew[0];
                        }
                        else {
                            return new LongHashTrieMap<>(bitmapNew, elemsNew, sizeNew);
                        }
                    }
                    else {
                        return ALongHashMap.empty ();
                    }
                }
                else if(elems.length == 1 && ! (subNew instanceof LongHashTrieMap)) {
                    return subNew;
                }
                else {
                    final ALongHashMap<V>[] elemsNew = createArray(elems.length);
                    System.arraycopy(elems, 0, elemsNew, 0, elems.length);
                    elemsNew[offset] = subNew;
                    final int sizeNew = size + (subNew.size() - sub.size());
                    return new LongHashTrieMap<>(bitmap, elemsNew, sizeNew);
                }
            } else {
                return this;
            }
        }
    }
}
