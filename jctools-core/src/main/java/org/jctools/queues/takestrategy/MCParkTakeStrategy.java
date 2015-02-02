package org.jctools.queues.takestrategy;

import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class MCParkTakeStrategy<E> implements TakeStrategy<E>
{
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition cond = lock.newCondition();

    @Override
    public void signal()
    {
        ReentrantLock l = lock;
        l.lock();
        try
        {
            cond.signal();
        }
        finally
        {
            l.unlock();
        }
    }

    @Override
    public E waitFor(Queue<E> q) throws InterruptedException
    {
        E e = q.poll();
        if (e != null)
        {
            return e;
        }

        ReentrantLock l = lock;
        l.lock();
        try
        {
            while((e = q.poll())==null)
            {
                cond.await();
            }
        }
        finally
        {
            l.unlock();
        }

        return e;
    }
}
