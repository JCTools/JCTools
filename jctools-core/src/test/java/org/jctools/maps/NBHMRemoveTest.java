package org.jctools.maps;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class NBHMRemoveTest {

    public static final Long TEST_KEY_OTHER = 123777L;
    public static final Long TEST_KEY_0 = 0L;

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<>();
        // Verify the test assumptions against JDK reference implementations
        list.add(new Object[]{new HashMap<>(), TEST_KEY_0, "0", "1"});
        list.add(new Object[]{new ConcurrentHashMap<>(), TEST_KEY_0, "0", "1"});
        list.add(new Object[]{new Hashtable<>(), TEST_KEY_0, "0", "1"});

        // Test with special key
        list.add(new Object[]{new NonBlockingHashMap<>(), TEST_KEY_0, "0", "1"});
        list.add(new Object[]{new NonBlockingHashMapLong<>(), TEST_KEY_0, "0", "1"});
        list.add(new Object[]{new NonBlockingIdentityHashMap<>(), TEST_KEY_0, "0", "1"});

        // Test with some other key
        list.add(new Object[]{new NonBlockingHashMap<>(), TEST_KEY_OTHER, "0", "1"});
        list.add(new Object[]{new NonBlockingHashMapLong<>(), TEST_KEY_OTHER, "0", "1"});
        list.add(new Object[]{new NonBlockingIdentityHashMap<>(), TEST_KEY_OTHER, "0", "1"});

        return list;
    }

    final Map<Long, String> map;
    final Long key;
    final String v1;
    final String v2;

    public NBHMRemoveTest(Map<Long, String> map, Long key, String v1, String v2) {
        this.map = map;
        this.key = key;
        this.v1 = v1;
        this.v2 = v2;
    }

    @Test
    public void directRemoveKey() {
        installValue(map, key, v1);
        assertEquals(v1, map.remove(key));
        postRemoveAsserts(map, key);
        assertFalse(map.containsValue(v1));
    }

    @Test
    public void keySetIteratorRemoveKey() {
        installValue(map, key, v1);
        Iterator<Long> iterator = map.keySet().iterator();
        while (iterator.hasNext()) {
            if (key.equals(iterator.next())) {
                iterator.remove();
                break;
            }
        }
        postRemoveAsserts(map, key);
        assertFalse(map.containsValue(v1));
    }

    @Test
    public void keySetIteratorRemoveKeyAfterValChange() {
        installValue(map, key, v1);
        Iterator<Long> iterator = map.keySet().iterator();
        map.put(key, v2);
        while (iterator.hasNext()) {
            if (key.equals(iterator.next())) {
                iterator.remove();
                break;
            }
        }
        postRemoveAsserts(map, key);
        assertFalse(map.containsValue(v1));
        assertFalse(map.containsValue(v2));
    }

    @Test
    public void entriesIteratorRemoveKey() {
        installValue(map, key, v1);
        Iterator<Map.Entry<Long,String>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, String> entry = iterator.next();
            if (key.equals(entry.getKey())) {
                iterator.remove();
                break;
            }
        }
        postRemoveAsserts(map, key);
        assertFalse(map.containsValue(v1));
    }

    @Test
    public void entriesIteratorRemoveKeyAfterValChange() {
        installValue(map, key, v1);
        Iterator<Map.Entry<Long,String>> iterator = map.entrySet().iterator();
        map.put(key, v2);
        while (iterator.hasNext()) {
            Map.Entry<Long, String> entry = iterator.next();
            if (key.equals(entry.getKey())) {
                iterator.remove();
                break;
            }
        }
        // This is weird, since the entry has infact changed, so should not be removed, but JDK refernce implementations
        // all remove based on the key.
        postRemoveAsserts(map, key);
        assertFalse(map.containsValue(v1));
        assertFalse(map.containsValue(v2));
    }

    @Test
    public void valuesIteratorRemove() {
        installValue(map, key, v1);
        Iterator<String> iterator = map.values().iterator();
        while (iterator.hasNext()) {
            if (v1.equals(iterator.next())) {
                iterator.remove();
                break;
            }
        }
        postRemoveAsserts(map, key);
        assertFalse(map.containsValue(v1));
    }

    @Test
    public void valuesIteratorRemoveAfterValChange() {
        installValue(map, key, v1);
        Iterator<String> iterator = map.values().iterator();
        map.put(key, v2);
        while (iterator.hasNext()) {
            if (v1.equals(iterator.next())) {
                iterator.remove();
                break;
            }
        }
        assertFalse(map.containsValue(v1));
        singleValueInMapAsserts(map, key, v2);
    }

    private void installValue(Map<Long, String> map, Long testKey, String value) {
        map.put(testKey, value);
        singleValueInMapAsserts(map, testKey, value);
    }

    private void singleValueInMapAsserts(Map<Long, String> map, Long testKey, String value) {
        assertEquals(value, map.get(testKey));
        assertEquals(1, map.size());
        assertFalse(map.isEmpty());
        assertTrue(map.containsKey(testKey));
        assertTrue(map.containsValue(value));
    }

    private void postRemoveAsserts(Map<Long, String> map, Long testKey) {
        assertNull(map.get(testKey));
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
        assertFalse(map.containsKey(testKey));
    }
}
