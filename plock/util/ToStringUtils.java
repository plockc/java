import java.lang.reflect.Array;
import java.util.IdentityHashMap;
import java.util.*;

/**
* pretty print one dimensionaly arrays/collections/anything Iterable in color, and optionally label indices,
* very Useful for logging and troubleshooting sorts.
* 
* If you don't want colors, just use java.util.Arrays.deepToString()
*
* When printing, it will equally space elements
* One implementation trick is avoiding separate methods for each of the array primitive types
*
* TODO: recursive calling, multiline output for long array, option for depth to be done one per line
*/
/** use Arrays.toString() to do a normal array print */
public class ToStringUtils {
	private static final String ESC="\033[", PLAIN=ESC+"m", RED=ESC+"31m", CYAN=ESC+"36m";

  /**
   * @param array the obects to print, this can be an array, a collection, or an iterable
   */
	public static String toString(Object array) {
		return toString(array, "", new IdentityHashMap());
	}

  /**
   * @param array the obects to print, this can be an array, a collection, or an iterable
   * @param indices the index positions to label, labels start with 'a' and increment for each one of indices
   */
  public static String toString(Object array, int ... indices) {
  	return toString(array, "a", new IdentityHashMap(), indices);
  }
  /**
   * @param array the obects to print, this can be an array, a collection, or an iterable
   * @param labels the collection of letters to be the labels, in order to match the varargs of indices
   * @param indices the index positions to label, first index goes to the first letter in labels
   */
    public static String toString(Object array, CharSequence labels, int ... indices) {
    	return toString(array, labels, new IdentityHashMap(), indices);
    }
  /**
   * Pretty prints an array slice
   * @param array the obects to print, this can be an array, a collection, or an iterable
   * @param start within the array, where to start (inclusive)
   * @param end within the array, which end to end at (exclusive)
   * @param labels the collection of letters to be the labels, in order to match the varargs of indices
   * @param indices the index positions to label, first index goes to the first letter in labels
   */
  public static final String toString(Object array, int start, int end, CharSequence labels, int ... indices) {
  		IdentityHashMap seen = new IdentityHashMap();
		return toString(toStringArray(array, seen), start, end, labels, seen, indices);
  }
 
  //
  // BEGIN INTERNALS
  //

  /* called when we do not know if it is an array or not but we want to print the whole thing */
    private static String toString(Object array, CharSequence labels, IdentityHashMap seen, int ... indices) {
		if (seen.containsKey(array)) {return "[...]";}
  		array = toStringArray(array, seen);
        // looks like a primitive actually, so just return it
    	if (!(array instanceof String[])) {return array.toString();}
        // TODO: why does this not pass seen in??
		return toString(array, 0, ((String[])array).length, labels, seen, indices);
	}
    /** called after toStringArray to actually do the printing */
    private static final String toString(Object array, int start, int end, CharSequence labels, IdentityHashMap seen, int ... indices) 
  {
      //TODO: test for array/collection else just print the ... to indicate recursion
		if (seen.containsKey(array)) {return "[...]";}
      seen.put(array,array);
        // we fond a primitive value, so just return it
        // TODO: will this even occur?
    	if (!(array instanceof String[])) {return array.toString();}
  	StringBuilder arrayBuilder = new StringBuilder(PLAIN+"[");
    // this will ensure we have labels for all of the passed in indices
    // TODO: check for duplicates
    // TODO: shoudn't this move to the API?
	if (labels.length() < indices.length) {
		StringBuilder newLabels = new StringBuilder(labels);
		for (int i=0; i<indices.length-labels.length(); i++) {
			newLabels.append((char)(labels.charAt(labels.length()-1)+i+1));
		}
		labels = newLabels.toString();
	}

	  String[] toStrings = (String[])array;
	  int[] lengths = new int[toStrings.length];
	// find the max element length without the color decorations
	int maxElemLen = 0;
	for (int i=0; i<toStrings.length; i++) {
		int length = toStrings[i].replaceAll("\\033\\[[0-9;]*m","").length();
		maxElemLen = Math.max(maxElemLen, length);
		lengths[i] = length;
	}
    // allow space in case all of the indices end up on a single element
    int fieldLength = maxElemLen + indices.length;

	int buffIndex = 0;
    for (int e = 0; e<toStrings.length; e++) {
        // create the index labels
		StringBuilder indicesBuilder = new StringBuilder();
	  int numIndices=0;
      // would linked set be faster?
      for (int i=0; i<indices.length; i++) {
      	if (e==indices[i]) {
      		indicesBuilder.append(RED+labels.charAt(i)+PLAIN);
      		numIndices++;
      	}
      }
      // print the element (or blanks if not in start to end range), figuring out the number of leading spaces
      StringBuilder elementBuilder = new StringBuilder();
	  int numSpaces = fieldLength-lengths[e]-numIndices;
	  String spaces = (numSpaces == 0) ? "" : String.format("%"+numSpaces+"s", " ");
	  elementBuilder.append(spaces).append(indicesBuilder);
      if (e>=start && e<=end) {
	        elementBuilder.append(CYAN+toStrings[e]+PLAIN+',');
		} else {
		    elementBuilder.append(String.format("%"+toStrings[e].length()+"s,", " "));
		}
      arrayBuilder.append(elementBuilder);
    }
    if (toStrings.length > 0) {
	    arrayBuilder.deleteCharAt(arrayBuilder.length()-1);
	}
	arrayBuilder.append("]");
    return arrayBuilder.toString();
  }

  private static Object toStringArray(Object array, IdentityHashMap seen) {
      if (array instanceof String[]) { 
      System.out.println("tostring array "+Arrays.toString((String[]) array));
      Thread.dumpStack();
          String[] arrayAsStrings = (String[])array;
          String[] toStrings = new String[arrayAsStrings.length];
          for (int i=0; i<arrayAsStrings.length; i++) {
              toStrings[i] = "\""+arrayAsStrings[i]+"\"";
          }
          return toStrings; 
      } 
      if (array instanceof Iterable) {
		ArrayList<String> stringsList = new ArrayList<String>();
		for (Object elem : (Iterable)array) {
			stringsList.add(toString(elem, "", seen));
		}
        System.out.println("tostring list "+stringsList);
		return stringsList.toArray(new String[] {});
	} else if (array.getClass().isArray()) {
		String[] toStrings = new String[Array.getLength(array)];
		for (int i=0; i<toStrings.length; i++) {
			toStrings[i] = toString(Array.get(array,i), "", seen);;
		}
		return toStrings;
	} else if (array instanceof CharSequence) {
        System.out.println("tostring just return a String: \""+array+"\"");
		return "\""+array+"\"";
    } else {
        System.out.println("tostring just return: "+array);
		return array;
	}
  }
  public static void main(String[] args) {
  	System.out.println(toString(Arrays.asList(3, Arrays.asList("Hello", "World!"),Arrays.asList("!"))));
  }
}
