package org.jctools.queues.takestrategy;

import org.jctools.queues.spec.ConcurrentQueueSpec;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public final class SCParkTakeStrategy<E> implements TakeStrategy<E>
{

    public volatile int storeFence = 0;

    private AtomicReference<Thread> t               = new AtomicReference<Thread>(null);

    @Override
    public void signal()
    {
        // Make sure the offer is visible before unpark
        storeFence = 1; // store barrier

        LockSupport.unpark(t.get()); // t.get() load barrier
    }

    @Override
    public E waitPoll(Queue<E> q) throws InterruptedException
    {
        E e = q.poll();
        if (e != null)
        {
            return e;
        }

        t.set(Thread.currentThread());

        while ((e = q.poll()) == null)
        {
            LockSupport.park();
        }

        t.lazySet(null);

        return e;
    }

    @Override
    public boolean supportsSpec(ConcurrentQueueSpec qs)
    {
        return qs.isMpsc() || qs.isSpsc();
    }


}
