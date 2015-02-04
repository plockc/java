import java.lang.reflect.Array;
import java.util.IdentityHashMap;
import java.util.*;
import java.util.regex.*;

/**
* json convert arrays/collections/anything Iterable in color, 
* 
* eventually will try to compact the output while still making it readable
* will only do two levels max on a line, unless it becomes too many lines
* and when a child ends up with a really long entry, will do each child on a different line
 * One implementation trick is avoiding separate methods for each of the array primitive types
 * each branch can maintain it's own compacting statistics 
*
* [ {"foo":"bar"}, {"biz":{"yo":"man",
*                          "what":"is your problem"}} ]
* [ {"fooz":"bar"}, 
*   { "biz":{"yo":"man", "what":"is your problem"}} ]
* find out the width of each child then determine if the all go on one line or grid, or one per line
*  based on the room remaining
*/
/** use Arrays.toString() to do a normal array print */
public class Json {
	private static final String ESC="\033[", PLAIN=ESC+"m", RED=ESC+"31m", CYAN=ESC+"36m";

  /**
   * @param array the obects to print, this can be an array, a collection, or an iterable
   */
	public static String toString(Object array) {
		return toString(array, new IdentityHashMap(), 0, 30).formatted;
	}

  //
  // BEGIN INTERNALS
  //

    static class Child {
        public Child(String formatted, int length) {this.formatted=formatted; this.length=length;}
        String formatted;
        int length;
    }
    private static final Child toString(final Object array, IdentityHashMap seen, int indent, int cols) {
      if (array instanceof CharSequence) {
          // lengths is the sequence plus the two quotes
		return new Child(RED+"\""+array+"\""+PLAIN, ((CharSequence)array).length()+2);
      }
      if (array instanceof Number || array instanceof Boolean) {
        String str = array.toString();
          return new Child(CYAN+str+PLAIN, str.length());
      }
      boolean isArray = array.getClass().isArray();
      boolean isIterable = array instanceof Iterable;
      boolean isMap = array instanceof Map;
      if (!isArray && !isIterable && !isMap) {
          String str = RED+"\""+array+"\""+PLAIN;
          return new Child(str, str.length());
      }
		if (seen.containsKey(array)) {
            throw new IllegalArgumentException("cannot have loops with "+array);
        }
      seen.put(array,array);

      class ArrayIterable implements Iterable {
          int length = Array.getLength(array);
          int index=0;
          public Iterator iterator() { return new Iterator() {
              public boolean hasNext() {return index < length;}
              public Object next() {return Array.get(array,index++);}
          };}
      }
      if (isArray || isIterable) {
          return toString(isArray ? new ArrayIterable() : (Iterable)array, seen, indent, cols-2);
      } else {
          return toString((Map)array, seen, indent, cols-2);
      }
  }

  private static final Pattern newLinePattern = Pattern.compile("\n", Pattern.DOTALL);

  public static final Child toString(Map map, IdentityHashMap seen, int indent, int cols) {
    List<Child> keys = new ArrayList<Child>();
    List<Child> values = new ArrayList<Child>();
    int maxKeyLength = 0;
    int sumKeyLength = 0;
    int maxValueLength = 0;
    int sumValueLength = 0;
    for (Map.Entry entry : ((Map<Object,Object>)map).entrySet()) {
        Child keyChild = toString(entry.getKey(), seen, indent, cols-2);
        keyChild.formatted = newLinePattern.matcher(keyChild.formatted).replaceAll("\n  ");

        Child valueChild = toString(entry.getValue(), seen, indent, cols-2);
        valueChild.formatted = newLinePattern.matcher(valueChild.formatted).replaceAll("\n  ");

        maxKeyLength = Math.max(keyChild.length, maxKeyLength);
        sumKeyLength += keyChild.length;

        maxValueLength = Math.max(valueChild.length, maxValueLength);
        sumValueLength += valueChild.length;

        keys.add(keyChild);
        values.add(valueChild);
    }
    // check if all the keys and values will fit on a single line
    if (2+sumKeyLength+sumValueLength+2*(map.size()-1) <= cols) {
        List<Child> entries = new ArrayList<Child>(keys.size());
        for (int i=0; i<keys.size(); i++) {
            Child key = keys.get(i);
            Child value = values.get(i);
            entries.add(new Child(key.formatted+": "+value.formatted, key.length+value.length+2));
        }
        Child formatted = formatChildren(entries, ", ");
        // remember length needs to include the wrapping "{}"
        return new Child("{"+formatted.formatted+"}", 2+formatted.length);
    }
    // check if we can do key+value on a single line
    if (2+maxKeyLength+maxValueLength <= cols) {
        List<Child> entries = new ArrayList<Child>(keys.size());
        for (int i=0; i<keys.size(); i++) {
            Child key = keys.get(i);
            Child value = values.get(i);
            entries.add(new Child(key.formatted+": "+value.formatted, key.length+value.length+2));
        }
        Child formatted = formatChildren(entries, ",\n  ");
        // remember length needs to include the wrapping "{}"
        return new Child("{ "+formatted.formatted+"}", 2+maxKeyLength+maxValueLength);
    } 
    List<Child> entries = new ArrayList<Child>(keys.size());
    for (int i=0; i<keys.size(); i++) {
        Child key = keys.get(i);
        Child value = values.get(i);
        int length = Math.max(key.length+1, value.length+4);
        entries.add(new Child(key.formatted+":\n    "+value.formatted, length));
    }
    Child formatted = formatChildren(entries, ",\n  ");
    // remember length needs to include the wrapping "{ }", and the widest key or value
    int length = Math.max(maxKeyLength+1, maxValueLength+4);
    return new Child("{ "+formatted.formatted+"}", 3+length);
  }

  public static final Child toString(Iterable iter, IdentityHashMap seen, int indent, int cols) {
      List<Child> children = new ArrayList<Child>();
      int maxLength = 0;
      int sumLength = 0;
        for (Object obj : iter) {
            Child child = toString(obj, seen, indent, cols-2);
            // if the child is multiline, indent them
            child.formatted = newLinePattern.matcher(child.formatted).replaceAll("\n  ");
            maxLength = Math.max(child.length, maxLength);
            sumLength += child.length;
            children.add(child);
        }
        // if can fit on a single line, then just comma delim
        if (2+sumLength+(children.size()-1)*2 <= cols) {
            Child formatted = formatChildren(children, ", ");
            // making sure to add two for the wrapping "[]"
            return new Child("["+formatted.formatted+"]", 2+formatted.length);
        }  
        // will not fit on a single line, do a line per entry with indention
        Child formatted = formatChildren(children, ",\n  ");
        // the child width plus the leading "[ "
        return new Child("[ "+formatted.formatted+"]", maxLength+3);
  }

  public static final Child formatChildren(List<Child> children, String delim) {
      int length=0;
  	StringBuilder arrayBuilder = new StringBuilder();
    for (Child child : children) {
        length+=child.length + delim.length();
        arrayBuilder.append(child.formatted+delim);
    }
    if (!children.isEmpty()) {
	    arrayBuilder.delete(arrayBuilder.length()-delim.length(), arrayBuilder.length());
        length -= delim.length();
	}
    return new Child(arrayBuilder.toString(), length);
  }
  public static void main(String[] args) {
  	System.out.println(toString(Arrays.asList(3, Arrays.asList("Hey", "Yo"),Arrays.asList("!"))));
  	System.out.println(toString(Arrays.asList(3, Arrays.asList("Hello", "World!"),Arrays.asList("!"))));
  	System.out.println(toString(Arrays.asList(3, Arrays.asList("Hello", "you", "cruel", "World!"),Arrays.asList("!", 24))));
    Map map = new HashMap() {{put("foo", "bar"); put("biz", "baz");}};
  	System.out.println(toString(Arrays.asList(3, Arrays.asList("Hello", "you", "cruel", "World!"),map,Arrays.asList("!", 24))));
    map = new HashMap() {{put("foo", "bar"); put("biz", "baz"); put("long key", "long value");}};
  	System.out.println(toString(Arrays.asList(3, Arrays.asList("Hello", "you", "cruel", "World!"),map,Arrays.asList("!", 24))));
    map = new HashMap() {{put("foo", "bar"); put("biz", "baz"); put("this is a super long key", "this is a super long value");}};
  	System.out.println(toString(Arrays.asList(3, Arrays.asList("Hello", "you", "cruel", "World!"),map,Arrays.asList("!", 24))));
    map = new HashMap() {{put(new HashMap(){{put("sk1", "sV1"); put("sK2", "sV2");}}, "v1"); put("k2", "v2");}};
  	System.out.println(toString(Arrays.asList(3, Arrays.asList("Hello", "you", "cruel", "World!"),map,Arrays.asList("!", 24))));
    map = new HashMap() {{put(new HashMap(){{put("subK1", "subV1"); put("subK2", "subV2");}}, "v1"); put("key2", "val2");}};
  	System.out.println(toString(Arrays.asList(3, Arrays.asList("Hello", "you", "cruel", "World!"),map,Arrays.asList("!", 24))));

  }
}
