package org.jctools.queues.putstrategy;

public class YieldPutStrategy implements PutStrategy
{
    @Override
    public void backOff()
    {
        Thread.yield();
    }

    @Override
    public void signal()
    {
        // Nothing
    }
}
