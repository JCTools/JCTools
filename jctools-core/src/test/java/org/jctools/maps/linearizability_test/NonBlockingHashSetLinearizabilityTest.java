package org.jctools.maps.linearizability_test;

import org.jctools.maps.NonBlockingHashSet;

public class NonBlockingHashSetLinearizabilityTest extends LincheckSetTest
{
    public NonBlockingHashSetLinearizabilityTest()
    {
        super(new NonBlockingHashSet<>());
    }
}
