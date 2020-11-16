package org.jctools.maps.linearizability_test;

import org.jctools.maps.NonBlockingHashMap;

public class NonBlockingHashMapLinearizabilityTest extends LincheckMapTest
{
    public NonBlockingHashMapLinearizabilityTest()
    {
        super(new NonBlockingHashMap<>());
    }
}
