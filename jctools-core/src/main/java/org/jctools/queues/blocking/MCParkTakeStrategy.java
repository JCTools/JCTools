package org.jctools.queues.blocking;

import org.jctools.queues.spec.ConcurrentQueueSpec;

import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class MCParkTakeStrategy<E> implements TakeStrategy<E>
{
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition cond = lock.newCondition();

    private int waiters = 0;

    @Override
    public void signal()
    {
        ReentrantLock l = lock;
        l.lock();
        try
        {
            if (waiters>0)
            {
                cond.signal();
            }
        }
        finally
        {
            l.unlock();
        }
    }

    @Override
    public E waitPoll(Queue<E> q) throws InterruptedException
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
                waiters++;
                cond.await();
                waiters--;
            }
        }
        finally
        {
            l.unlock();
        }

        return e;
    }

    @Override
    public boolean supportsSpec(ConcurrentQueueSpec qs)
    {
        return true;
    }

}
