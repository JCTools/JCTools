package org.jctools.queues.takestrategy;

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
    public E waitFor(SupplierJDK6<E> supplier) throws InterruptedException
    {
        E e = supplier.get();
        if (e != null)
        {
            return e;
        }

        ReentrantLock l = lock;
        l.lock();
        try
        {
            while((e = supplier.get())==null)
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
