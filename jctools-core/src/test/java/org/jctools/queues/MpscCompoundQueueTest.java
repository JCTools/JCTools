package org.jctools.queues;

import org.junit.Test;

public class MpscCompoundQueueTest
{
    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsZeroParallelism()
    {
        new MpscCompoundQueue<Object>(1024, 0);
    }
}
