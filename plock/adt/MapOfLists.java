package plock.adt; 

import java.util.*;
import plock.adt.MapAdapter;

/** Best use is to collect an insertion order list of values for a given key, values can appear multiple times.
 *  Designed to be lightweight on add, but removes traverses the value collection.
 *  If a value list becomes empty, the key should be dropped.
 *  Null is not a valid for a value
 *  Modifications through iterators like keySet().iterator().remove() are not tracked properly.
 *  TODO: short term, wrap the iterators and values() keySet() to fail on updates
 */
public class MapOfLists<K,V> extends MapAdapter<K,List<V>> {
    private Map<V,LinkedHashSet<K>> valuesToKeys = new HashMap<V,LinkedHashSet<K>>();
    private int numElements = 0;
    
    public MapOfLists() {super(new LinkedHashMap<K,List<V>>());}
    public MapOfLists(Map<? extends K, ? extends List<V>> m) {this(); putAll(m);}

    /** @return the total number of values */
    public int getNumElements() {
        return numElements;
    }

    /** @return a list copy of the set of keys for given value ordered by the first appearance of a value in a key */
    public List<K> getKeysForValue(V v) {
        synchronized(map) {
            Set<K> keys = valuesToKeys.get(v);
            return keys == null ? Collections.<K>emptyList() : Collections.unmodifiableList(new ArrayList<K>(keys));
        }
    }
    
    /** @return an insertion ordered copy of the values for a given value */
    public List<V> values(Object key) {
        synchronized(map) {
            List<V> values = super.get(key);
            return values == null ? Collections.<V>emptyList() : Collections.unmodifiableList(new ArrayList<V>(values));
        }
    }

    public MapOfLists<K,V> addValueToKey(K key, V v) {
        if (v == null) {throw new NullPointerException("null not allowed");}
        synchronized(map) {
            List<V> vlist = map.get(key);
            if (vlist == null) {
                vlist = new LinkedList<V>();
                map.put(key, vlist);
            }
            vlist.add(v);
            LinkedHashSet<K> keysForNewVs = valuesToKeys.get(v);
            if (keysForNewVs == null) {
                keysForNewVs = new LinkedHashSet<K>();
                valuesToKeys.put(v, keysForNewVs);
            }
            keysForNewVs.add(key);
            numElements++;
        }
        return this;
    }
    
    /** Will need to traverse the value list */
    public boolean removeFirstValueFromKey(K key, V v) {
        synchronized(map) {
            List<V> vlist = get(key);
            if (vlist == null) {
                return false;
            }
            boolean removed = false;
            Iterator<V> vIter = vlist.iterator();
            while (vIter.hasNext()) {
                if (vIter.next().equals(v)) {
                    if (removed) {
                        return true; // list will be non-empty and still contain 'v', no cleanup to do
                    } else {
                        vIter.remove();
                        numElements--;
                        removed = true;
                    }
                }
            }
            // if we make it here, there are no move values 'v' in the list
            if (removed) {
                if (vlist.isEmpty()) {
                    remove(key);
                }
                Set<K> keysForValue = valuesToKeys.get(v);
                keysForValue.remove(key);
                if (keysForValue.isEmpty()) {
                    valuesToKeys.remove(v);
                }
            }
            return removed;
        }
    }

    /** removes all values from the key */
    public boolean removeValueFromKey(K key, V v) {
        synchronized(map) {
            List<V> vlist = get(key);
            if (vlist == null) {
                return false;
            }
            boolean removed = false;
            Iterator<V> vIter = vlist.iterator();
            while (vIter.hasNext()) {
                if (vIter.next().equals(v)) {
                    vIter.remove();
                    removed=true;
                    numElements--;
                }
            }
            if (removed) {
                if (vlist.isEmpty()) {
                    remove(key);
                }
                Set<K> keysForValue = valuesToKeys.get(v);
                keysForValue.remove(key);
                if (keysForValue.isEmpty()) {
                    valuesToKeys.remove(v);
                }
            }
            return removed;
        }
    }

    /** removes value completely, will require full value traversals for any key with 'v' in it */
    public boolean removeValue(V v) {
        synchronized(map) {
            boolean removedSomething = false;
            Set<K> keys = valuesToKeys.get(v);
            if (keys != null) {
                for (K key : new ArrayList<K>(keys)) {
                    removedSomething |= removeValueFromKey(key, v);
                }
            }
            return removedSomething;
        }
    }
    
    @Override
    public void clear() {
        synchronized(map) {
            super.clear();
            valuesToKeys.clear();
            numElements = 0;
        }
    }

    @Override
    public List<V> put(K key, List<V> newVs) {
        if (newVs == null) {throw new NullPointerException("no null values allowed");}
        synchronized(map) {
            if (newVs.size() == 0) {
                return remove(key);
            }

            newVs = new LinkedList<V>(newVs);
            for (V newV : newVs) {
                if (newV == null) {
                    throw new NullPointerException("no null values allowed");
                }
            }
            List<V> oldVs = map.put(key, newVs);
            if (oldVs != null) {
                for (V v : oldVs) {
                    valuesToKeys.get(v).remove(key); // for each V in the list, remove a back reference
                }
                numElements -= oldVs.size();
            }
            for (V v : newVs) {
                LinkedHashSet<K> keysForNewVs = valuesToKeys.get(v);
                if (keysForNewVs == null) {
                    keysForNewVs = new LinkedHashSet<K>();
                    valuesToKeys.put(v, keysForNewVs);
                }
                keysForNewVs.add(key);
            }
            numElements += newVs.size();
            return oldVs;
        }
    }

    @Override
    public void putAll(Map<? extends K, ? extends List<V>> m) {
        synchronized(map) {
            for (Map.Entry<? extends K, ? extends List<V>> entry : m.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public List<V> remove(Object key) {
        synchronized(map) {
            List<V> oldVs = super.remove(key);
            if (oldVs != null) {
                for (V v : oldVs) {
                    valuesToKeys.get(v).remove(key); // for each V in the list, remove a back reference
                }
                numElements -= oldVs.size();
            }
            return oldVs;
        }
    }
    public Set<Entry<K, List<V>>> entrySet() {return Collections.unmodifiableSet(map.entrySet());}
    public List<V> get(Object key) {
        List<V> val=super.get(key);
        return val == null ? val : Collections.synchronizedList(val);
    }
    public Set<K> keySet() {return Collections.unmodifiableSet(map.keySet());} // this was a "live" keyset
    public Collection<List<V>> values() {
        synchronized(map) {
            Collection<List<V>> curValues = map.values();
            Collection<List<V>> safeValues = new ArrayList<List<V>>(curValues.size());
            for (List<V> value : curValues) {
                safeValues.add(Collections.unmodifiableList(value));
            }
            return safeValues;
        }
    }
}
