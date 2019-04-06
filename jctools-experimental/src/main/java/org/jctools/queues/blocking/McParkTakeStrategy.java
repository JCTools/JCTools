package org.jctools.queues.blocking;

import org.jctools.queues.spec.ConcurrentQueueSpec;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

public final class McParkTakeStrategy<E> implements TakeStrategy<E>
{
    private static final AtomicLongFieldUpdater<McParkTakeStrategy> WAITERS_UPDATER = AtomicLongFieldUpdater.newUpdater(McParkTakeStrategy.class, "waiters");
    private volatile long waiters = 0;
    private final Object obj = new Object();

    @Override
    public void signal()
    {
        if (waiters > 0)
        {
            synchronized (obj)
            {
                if (waiters > 0)
                {
                    obj.notify();
                }
            }
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

        WAITERS_UPDATER.incrementAndGet(this);
        synchronized (obj)
        {
            while ((e = q.poll()) == null)
            {
                obj.wait();
            }
            WAITERS_UPDATER.decrementAndGet(this);
        }

        return e;
    }

    @Override
    public boolean supportsSpec(ConcurrentQueueSpec qs)
    {
        return true;
    }

}
