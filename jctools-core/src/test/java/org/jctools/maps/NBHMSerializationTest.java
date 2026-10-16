package org.jctools.maps;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class NBHMSerializationTest
{
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> parameters()
    {
        return Arrays
            .asList(
                new Object[] {"NonBlockingHashMap", 0, 1L},
                new Object[] {"NonBlockingIdentityHashMap", 1, 1L},
                new Object[] {"NonBlockingHashMapLong", 2, 1L},
                new Object[] {"NonBlockingHashMapLong-zero", 2, 0L});
    }

    private final Map<Long, Object> map;
    private final Map<Long, Object> expected = new LinkedHashMap<Long, Object>();
    private final Removal removal;

    public NBHMSerializationTest(String name, int type, long removedKey)
    {
        removal = new Removal(removedKey);
        switch (type)
        {
            case 0:
                map = new RemovingHashMap(removal);
                break;
            case 1:
                map = new RemovingIdentityHashMap(removal);
                break;
            default:
                map = new RemovingHashMapLong(removal);
                break;
        }
        expected.put(removedKey, new String("prefetched"));
        if (removedKey == 0)
        {
            // SnapshotV always prefetches zero first. These entries follow it,
            // and must not be mistaken for data after an end-of-stream sentinel.
            expected.put(1L, new String("one"));
            expected.put(2L, new String("two"));
        }
        map.putAll(expected);
    }

    @Test(timeout = 5000)
    public void serializeWithPrefetchedRemoval() throws Exception
    {
        removal.armed = true;
        Map<?, ?> restored = roundTrip(map);
        assertRemoval();
        assertMappings(restored, false);
    }

    @Test(timeout = 5000)
    public void serializeWithoutRemoval() throws Exception
    {
        assertMappings(roundTrip(map), false);
        assertEquals(0, removal.count);
        assertEquals(expected.size(), map.size());
    }

    @Test(timeout = 5000)
    public void cloneWithPrefetchedRemoval()
    {
        removal.armed = true;
        Map<?, ?> cloned = cloneMap();
        assertRemoval();
        assertMappings(cloned, true);
    }

    @Test(timeout = 5000)
    public void cloneWithoutRemoval()
    {
        Map<?, ?> cloned = cloneMap();
        assertMappings(cloned, true);
        assertEquals(0, removal.count);
        assertEquals(expected.size(), map.size());
    }

    private Map<?, ?> cloneMap()
    {
        int boxedPuts = map instanceof RemovingHashMapLong ? ((RemovingHashMapLong) map).boxedPuts : 0;
        Map<Long, Object> cloned;
        if (map instanceof NonBlockingHashMap)
            cloned = (Map<Long, Object>) ((NonBlockingHashMap<?, ?>) map).clone();
        else if (map instanceof NonBlockingIdentityHashMap)
            cloned = (Map<Long, Object>) ((NonBlockingIdentityHashMap<?, ?>) map).clone();
        else
            cloned = ((NonBlockingHashMapLong<Object>) map).clone();
        assertEquals(map.getClass(), cloned.getClass());
        if (map instanceof RemovingHashMapLong)
            assertEquals("Cloning must retain the primitive put overload",
                boxedPuts,
                ((RemovingHashMapLong) cloned).boxedPuts);
        cloned.put(99L, "clone only");
        assertFalse(map.containsKey(99L));
        cloned.remove(99L);
        return cloned;
    }

    private void assertRemoval()
    {
        assertEquals("Removal must run once, after the real iterator prefetched a live value", 1, removal.count);
        assertFalse(map.containsKey(removal.key));
        assertEquals(expected.size() - 1, map.size());
    }

    private void assertMappings(Map<?, ?> actual, boolean shallowClone)
    {
        assertEquals(expected.size(), actual.size());
        for (Map.Entry<Long, Object> wanted : expected.entrySet())
        {
            boolean found = false;
            // Identity-map deserialization creates new key objects.
            for (Map.Entry<?, ?> entry : actual.entrySet())
            {
                assertNotNull(entry.getKey());
                assertNotNull(entry.getValue());
                if (wanted.getKey().equals(entry.getKey()))
                {
                    assertEquals(wanted.getValue(), entry.getValue());
                    if (shallowClone)
                    {
                        if (!(map instanceof NonBlockingHashMapLong))
                            assertSame(wanted.getKey(), entry.getKey());
                        assertSame(wanted.getValue(), entry.getValue());
                    }
                    found = true;
                }
            }
            assertTrue("Missing mapping " + wanted.getKey(), found);
        }
    }

    private static Map<?, ?> roundTrip(Map<?, ?> map) throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes))
        {
            out.writeObject(map);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
        {
            return (Map<?, ?>) in.readObject();
        }
    }

    private static class Removal
    {
        final Long key;
        boolean armed;
        int count;

        Removal(long key)
        {
            this.key = key;
        }

        void afterPrefetch(Map<Long, Object> owner, Iterator<?> iterator)
        {
            if (armed)
            {
                armed = false;
                assertTrue(iterator.hasNext());
                assertNotNull(owner.remove(key));
                count++;
            }
        }
    }

    private static <T> Set<T> removeAfterPrefetch(
        final Set<T> entries,
        final Map<Long, Object> owner,
        final Removal removal
    )
    {
        if (removal == null) return entries;
        return new AbstractSet<T>()
        {
            public int size()
            {
                return entries.size();
            }

            public Iterator<T> iterator()
            {
                // Use the real map iterator, whose constructor prefetches a value.
                // A single mapping (or key zero, always first) makes the window
                // independent of hashing, table size, scheduling and iteration order.
                Iterator<T> iterator = entries.iterator();
                removal.afterPrefetch(owner, iterator);
                // In particular, preserve IteratorLong for the old keySetLong path.
                return iterator;
            }
        };
    }

    private static class RemovingHashMap extends NonBlockingHashMap<Long, Object>
    {
        private static final long serialVersionUID = 1L;
        private final transient Removal removal;

        RemovingHashMap(Removal removal)
        {
            this.removal = removal;
        }

        public Set<Long> keySet()
        {
            return removeAfterPrefetch(super.keySet(), this, removal);
        }

        public Set<Map.Entry<Long, Object>> entrySet()
        {
            return removeAfterPrefetch(super.entrySet(), this, removal);
        }
    }

    private static class RemovingIdentityHashMap extends NonBlockingIdentityHashMap<Long, Object>
    {
        private static final long serialVersionUID = 1L;
        private final transient Removal removal;

        RemovingIdentityHashMap(Removal removal)
        {
            this.removal = removal;
        }

        public Set<Long> keySet()
        {
            return removeAfterPrefetch(super.keySet(), this, removal);
        }

        public Set<Map.Entry<Long, Object>> entrySet()
        {
            return removeAfterPrefetch(super.entrySet(), this, removal);
        }
    }

    private static class RemovingHashMapLong extends NonBlockingHashMapLong<Object>
    {
        private static final long serialVersionUID = 1L;
        private final transient Removal removal;
        private transient int boxedPuts;

        RemovingHashMapLong(Removal removal)
        {
            this.removal = removal;
        }

        public Object put(Long key, Object value)
        {
            boxedPuts++;
            return super.put(key, value);
        }

        public Set<Long> keySet()
        {
            return removeAfterPrefetch(super.keySet(), this, removal);
        }

        public Set<Map.Entry<Long, Object>> entrySet()
        {
            return removeAfterPrefetch(super.entrySet(), this, removal);
        }
    }
}
