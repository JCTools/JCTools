package org.jctools.queues.takestrategy;

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
    public E waitFor(SupplierJDK6<E> supplier)
    {
        E e = supplier.get();
        if (e != null)
        {
            return e;
        }

        t.set(Thread.currentThread());

        while ((e = supplier.get()) == null)
        {
            LockSupport.park();
        }

        t.lazySet(null);

        return e;
    }
}
