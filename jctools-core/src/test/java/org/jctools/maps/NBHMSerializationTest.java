package org.jctools.maps;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
                new Object[] {"ConcurrentHashMap", new ConcurrentHashMap<Long, Object>()},
                new Object[] {"NonBlockingHashMap", new NonBlockingHashMap<Long, Object>()},
                new Object[] {"NonBlockingHashMapLong", new NonBlockingHashMapLong<Object>()},
                new Object[] {"NonBlockingIdentityHashMap", new NonBlockingIdentityHashMap<Long, Object>()});
    }

    private final Map<Long, Object> map;

    public NBHMSerializationTest(String name, Map<Long, Object> map)
    {
        this.map = map;
    }

    @Test(timeout = 5000)
    public void serializeWithRemoval() throws Exception
    {
        map.put(0L, "zero");
        map.put(1L, "one");
        map.put(2L, "two");
        Iterator<Long> keys = map.keySet().iterator();
        Long first = keys.next();
        Long removed = keys.next();
        Long retained = keys.next();
        Object retainedValue = map.get(retained);
        // Serializing the first value removes the key prefetched by the map's iterator.
        map.put(first, new RemovingValue(map, removed));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes))
        {
            out.writeObject(map);
        }
        assertFalse(map.containsKey(removed));
        Map<?, ?> restored;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
        {
            restored = (Map<?, ?>) in.readObject();
        }

        boolean firstFound = false;
        boolean retainedFound = false;
        for (Map.Entry<?, ?> entry : restored.entrySet())
        {
            assertNotNull(entry.getKey());
            assertNotNull(entry.getValue());
            if (first.equals(entry.getKey()))
            {
                assertTrue(entry.getValue() instanceof RemovingValue);
                firstFound = true;
            }
            if (retained.equals(entry.getKey()))
            {
                assertEquals(retainedValue, entry.getValue());
                retainedFound = true;
            }
        }
        assertTrue(firstFound);
        assertTrue(retainedFound);
        // Weak consistency permits either retaining or omitting the removed mapping.
        assertTrue(restored.size() >= 2 && restored.size() <= 3);
    }

    private static class RemovingValue implements Serializable
    {
        private static final long serialVersionUID = 1L;

        private final transient Map<Long, Object> owner;
        private final transient Long key;

        RemovingValue(Map<Long, Object> owner, Long key)
        {
            this.owner = owner;
            this.key = key;
        }

        private void writeObject(ObjectOutputStream out) throws IOException
        {
            if (owner != null)
            {
                owner.remove(key);
            }
            out.defaultWriteObject();
        }
    }
}
