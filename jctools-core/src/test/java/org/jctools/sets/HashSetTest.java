package org.jctools.sets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.Set;

import org.jctools.sets.OpenHashSet;
import org.junit.Test;

public class HashSetTest {

    Set<Integer> set = new OpenHashSet<Integer>(128);
    @Test
    public void testAddRemove() {
        Integer e = new Integer(1024);
        Integer j = new Integer(2048);
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
        int sum=0;
        for(int i=0;i<1024;i+=63) {
            assertTrue(set.add(i));
            sum+=i;
        }
        Iterator<Integer> iter = set.iterator();
        while(iter.hasNext()) {
            sum-=iter.next();
            iter.remove();
        }
        assertEquals(0, sum);
        assertEquals(0, set.size());
    }

}
