package com.ajjpj.afoundation.collection.immutable;

import com.ajjpj.afoundation.collection.AEquality;
import com.ajjpj.afoundation.function.AFunction1;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;


/**
 * This interface represents immutable maps with mutator methods (<code>updated(), removed()</code>) that return a
 *  new map instance with different members. Since all instances are immutable, they are thread-safe by definition.
 *  They are also useful in recursive algorithms.<p>
 *
 * This interface does not implement <code>java.util.Map</code> since that interface is inherently mutable. There is
 *  however a conversion method to <code>java.util.Map</code>, and AMap implementations have factory methods that
 *  take <code>java.util.Map</code> instances.<p>
 *
 * The AMap contract is <em>not</em> hard-wired to <code>equals()</code> and <code>hashCode()</code> methods.
 *  Implementations are configurable through a pluggable equalityForEquals strategy, which defaults to equals() and hashCode()
 *  however. That allows e.g. using all AMap implementations based on object identity ('==') along the lines of what
 *  <code>java.util.IdentityHashMap</code> does.<p>
 *
 * Implementations (AHashMap in particular) use highly optimized algorithms to minimize 'copying' on modification.
 *
 * @author arno
 */
public interface AMap<K,V> extends Iterable<AMapEntry<K,V>>, Serializable {
    static <K,V> AMap<K,V> empty() {
        return AHashMap.empty ();
    }

    static <K,V> AMap<K,V> fromJava (Map<K,V> map) {
        return AHashMap.fromJavaUtilMap (map);
    }

    /**
     * @return an {@link AEquality} that returns {@code true} if and only if two elements are
     *         'equal' in the sense that one would replace the other as a key.
     */
    AEquality keyEquality ();

    /**
     * @return an empty {@link AMap} instance of the same type and configuration as this instance.
     */
    AMap<K,V> clear();

    /**
     * @return the number of key-value pairs in this map.
     */
    int size();

    /**
     * @return true if and only if this map's {@link #size()} is 0.
     */
    boolean isEmpty();

    /**
     * @return true if and only if this map's {@link #size()} greater than 0.
     */
    boolean nonEmpty();

    /**
     * Returns true iff the map contains the specified key. The containment check is based on the implementation's
     *  equalityForEquals strategy, which may or may not use <code>equals()</code>.
     */
    boolean containsKey(K key);

    /**
     * Returns true iff the map contains the specified value.
     */
    boolean containsValue(V value);

    /**
     * Returns the value stored for a given key. If the map contains the key, the corresponding value is wrapped in
     *  AOption.some(...), otherwise AOption.none() is returned.
     */
    AOption<V> get(K key);

    /**
     * This is the equivalent of calling get(...).get(); implementations throw a NoSuchElementException if there is
     *  no entry for the key.
     */
    V getRequired(K key);

    /**
     * This method 'adds' a new value for a given key, returning a modified copy of the map while leaving the original
     *  unmodified.
     */
    AMap<K,V> updated(K key, V value);

    /**
     * This method 'removes' an entry from the map, returning a modified copy of the map while leaving the original
     *  unmodified.
     */
    AMap<K,V> removed(K key);

    /**
     * Returns an iterator with all key/value pairs stored in the map. This method allows AMap instances to be used
     *  with the <code>for(...: map)</code> syntax introduced with Java 5.
     */
    @Override Iterator<AMapEntry<K,V>> iterator();

    /**
     * Returns an <code>java.util.Set</code> with the map's keys. The returned object throws
     *  <code>UnsupportedOperationException</code> for all modifying operations.<p>
     *
     * The returned set is <em>not</em> guaranteed to provide uniqueness with regard to <code>equals()</code>. If the
     *  map's equalityForEquals strategy treats two objects as different even if their <code>equals</code> methods return true,
     *  then the returned set may contain both.
     */
    ASet<K> keys();

    /**
     * Returns a <code>java.util.Collection</code> with the map's values. Duplicate values are returned as often as they
     *  occur. The returned collection throws <code>UnsupportedOperationException</code> for all modifying operations.
     */
    ACollection<V> values();

    /**
     * Returns a read-only <code>java.util.Map</code> view of the AMap. Constructing the view takes constant time (i.e.
     *  there is no copying), lookup and iteration are passed to the underlying AMap. All modifying operations on the
     *  returned Map throw <code>UnsupportedOperationException</code>.
     */
    java.util.Map<K,V> asJavaUtilMap();

    /**
     * This method wraps an AMap, causing a given defaultValue to be returned for all keys not explicitly present in
     *  the map. Looking up the value for a key not explicitly stored in the map does <em>not</em> modify the map.
     */
    AMap<K,V> withDefaultValue(V defaultValue);

    /**
     * This method wraps an AMap. If one of the <code>get...</code> methods is called for a key not stored in the map,
     *  a function is called to determine the value to be returned. <p>
     *
     * NB: This does <em>not</em> cause the calculated value to be stored in the map; repeated calls for the same
     *  key trigger the value's calculation anew each time.
     */
    AMap<K,V> withDefault(AFunction1<? super K, ? extends V, ? extends RuntimeException> function);
}
