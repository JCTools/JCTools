package org.jctools.maps;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.*;

public class NBHMLongCloneTest
{
    @Test(timeout = 5000)
    public void cloneDoesNotTreatKeyArrayPaddingAsZeroKey()
    {
        RemovingUnvisitedKeyMap probe = new RemovingUnvisitedKeyMap();
        // Exercise the real array allocation and iterator: deleting the second key
        // after prefetch leaves a trailing zero that is not a key in the map.
        assertArrayEquals(new long[] {probe.retainedKey, 0L}, probe.keySetLong());
        assertEquals(1, probe.removals);
        assertEquals(1, probe.size());
        assertFalse(probe.containsKey(0L));

        RemovingUnvisitedKeyMap source = new RemovingUnvisitedKeyMap();
        String retainedValue = source.get(source.retainedKey);
        NonBlockingHashMapLong<String> cloned = source.clone();
        assertSame(retainedValue, cloned.get(source.retainedKey));
        assertFalse(cloned.containsKey(0L));
        for (String value : cloned.values())
            assertNotNull(value);
    }

    private static class RemovingUnvisitedKeyMap extends NonBlockingHashMapLong<String>
    {
        private static final long serialVersionUID = 1L;
        private final long retainedKey;
        private final long removedKey;
        private boolean armed;
        private int removals;

        RemovingUnvisitedKeyMap()
        {
            put(1L, "one");
            put(2L, "two");
            // Discover the actual order rather than depending on the hash layout.
            retainedKey = super.keySet().iterator().next();
            removedKey = retainedKey == 1L ? 2L : 1L;
            armed = true;
        }

        public Set<Long> keySet()
        {
            final Set<Long> keys = super.keySet();
            return new AbstractSet<Long>()
            {
                public int size()
                {
                    return keys.size();
                }

                public Iterator<Long> iterator()
                {
                    Iterator<Long> iterator = keys.iterator();
                    if (armed)
                    {
                        armed = false;
                        assertNotNull(RemovingUnvisitedKeyMap.this.remove(removedKey));
                        removals++;
                    }
                    // keySetLong requires the original primitive IteratorLong.
                    return iterator;
                }
            };
        }
    }
}
