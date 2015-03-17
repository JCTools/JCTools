package org.jctools.queues.blocking;

import org.jctools.queues.spec.ConcurrentQueueSpec;

import java.util.Queue;

public class YieldPutStrategy<E> implements PutStrategy<E>
{
    @Override
    public void backoffOffer(Queue<E> q, E e)
    {
        while(!q.offer(e))
        {
            Thread.yield();
        }
    }

    @Override
    public void signal()
    {
        // Nothing
    }

    @Override
    public boolean supportsSpec(ConcurrentQueueSpec qs)
    {
        return true;
    }
}
