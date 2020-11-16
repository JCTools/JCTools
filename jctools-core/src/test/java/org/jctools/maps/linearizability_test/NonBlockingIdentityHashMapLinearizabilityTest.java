package org.jctools.maps.linearizability_test;

import org.jctools.maps.NonBlockingIdentityHashMap;

public class NonBlockingIdentityHashMapLinearizabilityTest extends LincheckMapTest
{
    public NonBlockingIdentityHashMapLinearizabilityTest()
    {
        // For NonBlockingIdentityHashMap operations with long keys may seem strange,
        // but as small Longs are typically cached in the jvm,
        // map.put(1L, value); map.containsKey(1L);
        // returns true, not false.
        super(new NonBlockingIdentityHashMap<>());
    }
}
