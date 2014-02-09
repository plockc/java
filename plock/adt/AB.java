package plock.adt;

import java.util.*;

public class AB<A,B> implements Comparable<AB<A,B>> {
    public final A a;
    public final B b;
    
    public static class ReverseBComparator<A,B> implements Comparator<AB<A,B>> {
        public int compare(AB<A, B> first, AB<A, B> second) {
            int aCompared = EasyComparator.compareType(first.a, second.a);
            return (aCompared != 0) ? aCompared : -EasyComparator.compareType(first.b, second.b); // either a is the deciding factor or compare b for the final result
        }
    }
    
    public AB(A a, B b) {
        this.a=a;
        this.b=b;
    }
    @SuppressWarnings("unchecked")
    public <K> AB(Map<K,?> source, K aKey, K bKey) {
        this.a = (A)source.get(aKey);
        this.b = (B)source.get(bKey);
    }
    public static <A,B> AB<A,B> ab(A a, B b) {return new AB<A,B>(a,b);}
    public A getA() {return a;}
    public B getB() {return b;}
    public int hashCode() {return (a==null ? 0 : a.hashCode())  +  (b==null ? Integer.MAX_VALUE/2 : b.hashCode());}
    public String toString() {return ""+a+":"+b;}
    public boolean equals(Object other) {
        if (!(other instanceof AB)) {return false;}
        AB<?,?> otherAB = (AB<?,?>)other;
        return (a==null?(otherAB.a==null):a.equals(otherAB.a)) && (b==null?(otherAB.b==null):b.equals(otherAB.b));
    }
    public int compareTo(AB<A,B> other) {
        int aCompared = EasyComparator.compareType(this.a, other.a);
        return (aCompared != 0) ? aCompared : EasyComparator.compareType(this.b, other.b); // either a is the deciding factor or compare b for the final result
    }
    public static <A,B,K,V> List<AB<A,B>> convert(List<Map<K,V>> list, K aKey, K bKey) {
        List<AB<A,B>> newList = new ArrayList<AB<A,B>>(list.size());
        for (Map<K,V> row : list) {
            newList.add(new AB<A,B>(row, aKey, bKey));
        }
        return newList;
    }
    public static <A,B> SortedSet<AB<A,B>> convert(Map<A,B> in) {
        SortedSet<AB<A,B>> out = new TreeSet<AB<A,B>>();
        for (Map.Entry<A,B> entry : in.entrySet()) {
            out.add(new AB<A,B>(entry.getKey(), entry.getValue()));
        }
        return out;
    }
    public static <A,B> SortedMap<A,B> convert(Collection<AB<A,B>> in) {
        SortedMap<A,B> out = new TreeMap<A,B>();
        for (AB<A,B> element : in) {
            out.put(element.a, element.b);
        }
        return out;
    }
    public static <A,B> SortedSet<AB<A,B>> greatestBForEachA(Collection<AB<A,B>> in) {
        SortedSet<AB<A,B>> out = new TreeSet<AB<A,B>>();
        out.addAll(in);
        return convert(convert(in));
    }
    public static <A,B> SortedSet<AB<A,B>> leastBForEachA(Collection<AB<A,B>> in) {
        SortedSet<AB<A,B>> out = new TreeSet<AB<A,B>>(new ReverseBComparator<A,B>());
        out.addAll(in);
        return convert(convert(in));
    }
    public static <A,B> AB<A,B> firstB(Collection<AB<A,B>> in, A key) {
        for (AB<A,B> element : in) {
            if (key == null && element.a == null || key != null && key.equals(element.a)) {
                return element;
            }
        }
        return null;
    }
}
