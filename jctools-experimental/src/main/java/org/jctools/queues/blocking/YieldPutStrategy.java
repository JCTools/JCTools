package org.jctools.queues.blocking;

import org.jctools.queues.spec.ConcurrentQueueSpec;

import java.util.Queue;

public class YieldPutStrategy<E> implements PutStrategy<E>
{
    @Override
    public void waitOffer(Queue<E> q, E e) throws InterruptedException
    {
        while(!q.offer(e))
        {
            Thread.yield();
            if (Thread.currentThread().isInterrupted())
            {
                throw new InterruptedException("Interrupted while waiting for the queue to put in queue");
            }
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
