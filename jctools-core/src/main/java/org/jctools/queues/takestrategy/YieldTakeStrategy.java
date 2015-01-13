package org.jctools.queues.takestrategy;

public class YieldTakeStrategy<E> implements TakeStrategy<E>
{
    @Override
    public void signal()
    {
        // Nothing to do
    }

    @Override
    public E waitFor(SupplierJDK6<E> supplier)
    {
        while(true) // Should introduce safepoints
        {
            E e = supplier.get();
            if (e!=null)
            {
                return e;
            }

            Thread.yield();
        }
    }
}
