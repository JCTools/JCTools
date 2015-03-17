package org.jctools.queues.blocking;

import org.jctools.queues.spec.ConcurrentQueueSpec;

import java.util.Queue;

public final class YieldTakeStrategy<E> implements TakeStrategy<E>
{
    @Override
    public void signal()
    {
        // Nothing to do
    }

    @Override
    public E waitPoll(Queue<E> q) throws InterruptedException
    {
        E e;
        while((e = q.poll()) == null)
        {
            Thread.yield();
        }
        return e;
    }

    @Override
    public boolean supportsSpec(ConcurrentQueueSpec qs)
    {
        return true;
    }
}
