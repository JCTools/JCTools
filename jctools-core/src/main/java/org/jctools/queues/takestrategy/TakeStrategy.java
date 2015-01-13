package org.jctools.queues.takestrategy;

public interface TakeStrategy<E>
{
    void signal();

    E waitFor(SupplierJDK6<E> supplier);
}
