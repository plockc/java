package plock.adt;

import java.util.*;

public class EasyComparator<T> implements Comparator<T> {
    public int compare(T o1, T o2) {
        return compareType(o1, o2);
    }
    @SuppressWarnings("unchecked")
    public static <T> int compareType(T t1, T t2) {
        if (t1 == null && t2 != null) { // null is less than non-null, short circuit
            return -1;
        }
        if (t1 != null && t2 == null) { // non-null is greater than null, short circuit
            return 1;
        }
        if (t1 == null && t2 == null) { // null is same, short circuit
            return 0;
        }
        if (t1 instanceof Comparable) {
            return ((Comparable<T>)t1).compareTo(t2); // either both null (0), or let's compare with a non-null implementation
        } else {
            return t1.toString().compareTo(t2.toString()); // drop down to string representation
        }
    }
}
