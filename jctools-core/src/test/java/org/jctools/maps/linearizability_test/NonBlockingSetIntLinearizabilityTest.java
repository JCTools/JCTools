package org.jctools.maps.linearizability_test;

import org.jctools.maps.NonBlockingSetInt;

public class NonBlockingSetIntLinearizabilityTest extends LincheckSetTest
{
    public NonBlockingSetIntLinearizabilityTest()
    {
        super(new NonBlockingSetInt());
    }
}
