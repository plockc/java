package plock.adt;

import java.util.*;

import junit.framework.TestCase;

public class MapOfListsTest extends TestCase
{
    MapOfLists<String,Integer> map = new MapOfLists<String,Integer>();
    
  public MapOfListsTest(String name) {
    super(name);
  }

  public void setUp() {
      map.clear();
      map.addValueToKey("a", 1);
      map.addValueToKey("a", 2);
      map.addValueToKey("b", 2);
      map.addValueToKey("a", 3);
      map.addValueToKey("a", 2);
      map.addValueToKey("c", 3);
      map.addValueToKey("b", 1);
      map.addValueToKey("d", 4);
  }
  
  public void testRemoveValue() {
      map.removeValue(2);
      assertEquals(Arrays.asList("a","b","c","d"), new LinkedList<String>(map.keySet()));
      assertEquals(4, map.size());
      assertEquals(5, map.getNumElements());
      assertEquals(Arrays.asList(1,3), map.get("a"));
      assertEquals(Arrays.asList(1), map.get("b"));
      assertEquals(Arrays.asList(3), map.get("c"));
      assertEquals(Arrays.asList(4), map.get("d"));
      assertEquals(Arrays.asList("a", "b"), map.getKeysForValue(1));
      assertEquals(Arrays.asList(), map.getKeysForValue(2));
      assertEquals(Arrays.asList("a", "c"), map.getKeysForValue(3));
      assertEquals(Arrays.asList("d"), map.getKeysForValue(4));
      map.removeValue(4);
      assertEquals(Arrays.asList("a","b","c"), new LinkedList<String>(map.keySet()));
      assertEquals(3, map.size());
      assertEquals(4, map.getNumElements());
      assertEquals(Arrays.asList(1,3), map.get("a"));
      assertEquals(Arrays.asList(1), map.get("b"));
      assertEquals(Arrays.asList(3), map.get("c"));
      assertTrue(!map.containsKey("d"));
      assertEquals(Arrays.asList("a", "b"), map.getKeysForValue(1));
      assertEquals(Arrays.asList(), map.getKeysForValue(2));
      assertEquals(Arrays.asList("a", "c"), map.getKeysForValue(3));
      assertTrue(map.getKeysForValue(4).isEmpty());
  }
  
  public void testRemoveKey() {
      map.remove("b");
      assertEquals(3, map.size());
      assertEquals(6, map.getNumElements());
      assertEquals(Arrays.asList(1,2,3,2), map.get("a"));
      assertTrue(!map.containsKey("b"));
      assertEquals(Arrays.asList(3), map.get("c"));
      assertEquals(Arrays.asList(4), map.get("d"));
      assertEquals(Arrays.asList("a"), map.getKeysForValue(1));
      assertEquals(Arrays.asList("a"), map.getKeysForValue(2));
      assertEquals(Arrays.asList("a", "c"), map.getKeysForValue(3));
      assertEquals(Arrays.asList("d"), map.getKeysForValue(4));
  }
  
  public void testRemoveFirstValueFromKey() {
      map.removeFirstValueFromKey("e", 4);
      assertEquals(4, map.size());
      assertEquals(8, map.getNumElements());
      assertEquals(Arrays.asList(4), map.get("d"));
      assertEquals(Arrays.asList("d"), map.getKeysForValue(4));
      map.removeFirstValueFromKey("a", 2);
      assertEquals(4, map.size());
      assertEquals(7, map.getNumElements());
      assertEquals(Arrays.asList(1,3,2), map.get("a"));
      assertEquals(Arrays.asList(2,1), map.get("b"));
      assertEquals(Arrays.asList("a", "b"), map.getKeysForValue(1));
      assertEquals(Arrays.asList("a", "b"), map.getKeysForValue(2));
      assertEquals(Arrays.asList("a", "c"), map.getKeysForValue(3));
      assertEquals(Arrays.asList("d"), map.getKeysForValue(4));
      map.removeFirstValueFromKey("d", 4);
      assertEquals(3, map.size());
      assertEquals(6, map.getNumElements());
      assertEquals(Arrays.asList(1,3,2), map.get("a"));
      assertEquals(Arrays.asList(2,1), map.get("b"));
      assertEquals(Arrays.asList("a", "b"), map.getKeysForValue(1));
      assertEquals(Arrays.asList("a", "b"), map.getKeysForValue(2));
      assertEquals(Arrays.asList("a", "c"), map.getKeysForValue(3));
      assertTrue(map.getKeysForValue(4).isEmpty());
      assertTrue(!map.containsKey("d"));
  }

  public void testRemoveValueFromKey() {
      map.removeValueFromKey("e", 4);
      assertEquals(4, map.size());
      assertEquals(8, map.getNumElements());
      assertEquals(Arrays.asList(4), map.get("d"));
      assertEquals(Arrays.asList("d"), map.getKeysForValue(4));
      map.removeValueFromKey("a", 2); // removes 2 items
      assertEquals(4, map.size());
      assertEquals(6, map.getNumElements());
      assertEquals(Arrays.asList(1,3), map.get("a"));
      assertEquals(Arrays.asList(2,1), map.get("b"));
      assertEquals(Arrays.asList("a", "b"), map.getKeysForValue(1));
      assertEquals(Arrays.asList("b"), map.getKeysForValue(2));
      assertEquals(Arrays.asList("a", "c"), map.getKeysForValue(3));
      assertEquals(Arrays.asList("d"), map.getKeysForValue(4));
      map.removeValueFromKey("d", 4);
      assertEquals(3, map.size());
      assertEquals(5, map.getNumElements());
      assertEquals(Arrays.asList(1,3), map.get("a"));
      assertEquals(Arrays.asList(2,1), map.get("b"));
      assertEquals(Arrays.asList("a", "b"), map.getKeysForValue(1));
      assertEquals(Arrays.asList("b"), map.getKeysForValue(2));
      assertEquals(Arrays.asList("a", "c"), map.getKeysForValue(3));
      assertTrue(map.getKeysForValue(4).isEmpty());
      assertTrue(!map.containsKey("d"));
  }

  public void testInitialState() {
      assertEquals(Arrays.asList("a","b","c","d"), new LinkedList<String>(map.keySet()));
      assertEquals(4, map.size());
      assertEquals(8, map.getNumElements());
      assertEquals(Arrays.asList(1,2,3,2), map.get("a"));
      assertEquals(Arrays.asList(2,1), map.get("b"));
      assertEquals(Arrays.asList(3), map.get("c"));
      assertEquals(Arrays.asList(4), map.get("d"));
      assertEquals(Arrays.asList("a", "b"), map.getKeysForValue(1));
      assertEquals(Arrays.asList("a", "b"), map.getKeysForValue(2));
      assertEquals(Arrays.asList("a", "c"), map.getKeysForValue(3));
      assertEquals(Arrays.asList("d"), map.getKeysForValue(4));
  }
  
  public static void main(String[] args) {
    junit.textui.TestRunner.run(MapOfListsTest.class);
  }
}
