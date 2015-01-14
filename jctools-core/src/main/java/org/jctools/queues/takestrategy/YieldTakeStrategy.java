package org.jctools.queues.takestrategy;

public final class YieldTakeStrategy<E> implements TakeStrategy<E>
{
    @Override
    public void signal()
    {
        // Nothing to do
    }

    @Override
    public E waitFor(SupplierJDK6<E> supplier)
    {
        E e;
        while((e = supplier.get()) == null)
        {
            Thread.yield();
        }
        return e;
    }
}
