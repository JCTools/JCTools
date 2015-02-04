package org.jctools.queues.putstrategy;

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
}
