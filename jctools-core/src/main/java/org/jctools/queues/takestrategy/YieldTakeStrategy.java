package org.jctools.queues.takestrategy;

import java.util.Queue;

public final class YieldTakeStrategy<E> implements TakeStrategy<E>
{
    @Override
    public void signal()
    {
        // Nothing to do
    }

    @Override
    public E waitFor(Queue<E> q) throws InterruptedException
    {
        E e;
        while((e = q.poll()) == null)
        {
            Thread.yield();
        }
        return e;
    }
}
