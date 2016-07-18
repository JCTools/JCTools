package org.jctools.sets;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.*;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class HashSetTest {

    static class Key {
        @Override
        public String toString() {
            return "Key [hash=" + hash + "]";
        }

        final int hash;

        Key(int i) {
            hash = i;
        }

        public int hashCode() {
            return hash;
        }

        public boolean equals(Object obj) {
            return this == obj;
        }
    }

    @Parameterized.Parameters
    public static Collection sets() {
        return Arrays.asList(a(new OpenHashSet<Key>(128)),
                a(new SingleWriterHashSet<Key>(128)),
                a(new IdentityOpenHashSet<Key>(128)));
    }

    private static Object[] a(Set<Key> set) {
        return new Object[] { set };
    }

    final Set<Key> set;

    public HashSetTest(Set<Key> set) {
        super();
        this.set = set;
    }

    @After
    public void clear() {
        Iterator it = set.iterator();
        int i = set.size();
        while (it.hasNext() && i-- > 0) {
            it.next();
            it.remove();
        }
        assertEquals(set.toString(),0, set.size());
    }
    @Test
    public void testAddRemove() {
        Key e = new Key(1024);
        Key j = new Key(2048);
        assertTrue(set.add(e));
        assertTrue(set.contains(e));
        assertFalse(set.contains(j));
        assertFalse(set.add(e));
        assertTrue(set.remove(e));
        assertFalse(set.contains(e));
        assertFalse(set.remove(e));
    }

    @Test
    public void testIterator() {
        int sum = 0;
        for (int i = 0; i < 1024; i += 63) {
            assertTrue(set.add(new Key(i)));
            sum += i;
        }
        Iterator<Key> iter = set.iterator();
        while (iter.hasNext()) {
            sum -= iter.next().hashCode();
            iter.remove();
        }
        assertEquals(0, set.size());
        assertEquals(0, sum);
    }

    @Test
    public void testRandom() {
        Random r = new Random();
        final long seed = r.nextLong();
        r.setSeed(seed);

        Key[] keys = new Key[1024];
        for (int i = 0; i < keys.length; i++) {
            final int hash = r.nextInt(keys.length);
            keys[i] = new Key(hash);
        }

        HashSet<Key> setRef = new HashSet<>();
        long until = System.currentTimeMillis() + 1000;
        while (System.currentTimeMillis() < until) {
            Key e = keys[r.nextInt(keys.length)];
            assertEquals(setRef.add(e), set.add(e));
            e = keys[r.nextInt(keys.length)];
            assertEquals(setRef.remove(e), set.remove(e));
        }
        assertEquals(setRef.size(), set.size());
        assertTrue(setRef.containsAll(set));
        assertTrue(set.containsAll(setRef));
    }
}
