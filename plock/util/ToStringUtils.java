import java.lang.reflect.Array;
import java.util.IdentityHashMap;
import java.util.*;

/**
* pretty print arrays/collections/anything Iterable in color, and optionally label indices,
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
   * @param indices the index positions to label, first index goes to the first letter in labels
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
    private static String toString(Object array, CharSequence labels, IdentityHashMap seen, int ... indices) {
		if (seen.containsKey(array)) {return "[...]";}
  		array = toStringArray(array, seen);
    	if (!(array instanceof String[])) {return array.toString();}
		return toString(array, 0, ((String[])array).length, labels, indices);
	}
    private static final String toString(Object array, int start, int end, CharSequence labels, IdentityHashMap seen, int ... indices) 
  {
		if (seen.containsKey(array)) {return "[...]";}
    	if (!(array instanceof String[])) {return array.toString();}
  	StringBuilder arrayBuilder = new StringBuilder(PLAIN+"[");
	if (labels.length() < indices.length) {
		StringBuilder newLabels = new StringBuilder(labels);
		for (int i=0; i<indices.length-labels.length(); i++) {
			newLabels.append((char)(labels.charAt(labels.length()-1)+i+1));
		}
		labels = newLabels.toString();
	}

	  String[] toStrings = (String[])array;
	  int[] lengths = new int[toStrings.length];
	// find the max element length and convert to array of strings while you are at it
	int maxElemLen = 0;
	for (int i=0; i<toStrings.length; i++) {
		int length = toStrings[i].replaceAll("\\033\\[[0-9;]*m","").length();
		maxElemLen = Math.max(maxElemLen, length);
		lengths[i] = length;
	}
    int fieldLength = maxElemLen + indices.length;

	int buffIndex = 0;
    for (int e = 0; e<toStrings.length; e++) {
		StringBuilder indicesBuilder = new StringBuilder();
	  int numIndices=0;
      for (int i=0; i<indices.length; i++) {
      	if (e==indices[i]) {
      		indicesBuilder.append(RED+labels.charAt(i)+PLAIN);
      		numIndices++;
      	}
      }
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
	if (array instanceof String[]) { return array; }
	if (array instanceof Iterable) {
		ArrayList<String> stringsList = new ArrayList<String>();
		for (Object elem : (Iterable)array) {
			stringsList.add(toString(elem, "", seen));
		}
		return stringsList.toArray(new String[] {});
	} else if (array.getClass().isArray()) {
		String[] toStrings = new String[Array.getLength(array)];
		for (int i=0; i<toStrings.length; i++) {
			toStrings[i] = Array.get(array,i).toString();
		}
		return toStrings;
	} else {
		return array;
	}
  }
  public static void main(String[] args) {
  	System.out.println(toString(Arrays.asList(3, Arrays.asList("Hello", "World!"),Arrays.asList("!"))));
  }
}
