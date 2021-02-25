package org.jctools.maps;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NBHMReplaceTest {
    @Test
    public void replaceOnEmptyMap() {
        assertEquals(null, new NonBlockingHashMap<String,String>().replace("k", "v"));
    }
    @Test
    public void replaceOnEmptyIdentityMap() {
        assertEquals(null, new NonBlockingIdentityHashMap<String,String>().replace("k", "v"));
    }
    @Test
    public void replaceOnEmptyLongMap() {
        assertEquals(null, new NonBlockingHashMapLong<String>().replace(1, "v"));
    }
}
