package org.jctools.maps.linearizability_test;

import org.jctools.maps.NonBlockingHashMapLong;

public class NonBlockingHashMapLongLinearizabilityTest extends LincheckMapTest
{
    public NonBlockingHashMapLongLinearizabilityTest()
    {
        super(new NonBlockingHashMapLong<>());
    }
}
