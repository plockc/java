/*
 *
 */
package plock.adt;

import java.util.*;

public class MapAdapter<K,V> implements Map<K, V> {
    protected final Map<K, V> map;

    @SuppressWarnings("unchecked")
    public MapAdapter(Map<? extends K, ? extends V> map) {
        this.map = (Map<K,V>)map;
    }
    public void clear() {map.clear();}
    public boolean containsKey(Object key) {return map.containsKey(key);}
    public boolean containsValue(Object value) {return map.containsValue(value);}
    public Set<Entry<K, V>> entrySet() {return map.entrySet();}
    public boolean equals(Object other) {return map.equals(other);}
    public V get(Object key) {return map.get(key);}
    public int hashCode() {return map.hashCode();}
    public boolean isEmpty() {return map.isEmpty();}
    public Set<K> keySet() {return (Set<K>)map.keySet();}
    public V put(K key, V value) {return map.put(key, value);}
    public void putAll(Map<? extends K, ? extends V> map) {this.map.putAll(map);}
    public V remove(Object key) {return map.remove(key);}
    public int size() {return map.size();}
    public Collection<V> values() {return map.values();}
    public String toString() {return map.toString();}
}
