package org.jctools.queues;

import org.jctools.queues.takestrategy.SupplierJDK6;
import org.jctools.queues.takestrategy.TakeStrategy;
import org.jctools.queues.takestrategy.ParkTakeStrategy;

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class SpscArrayQueueBlocking<E> extends SpscArrayQueue<E> implements BlockingQueue<E>
{
    private TakeStrategy<E> takeStrategy;

    public SpscArrayQueueBlocking(int capacity)
    {
        this(capacity, new ParkTakeStrategy());
    }

    public SpscArrayQueueBlocking(int capacity, TakeStrategy<E> takeStrategy)
    {
        super(capacity);
        this.takeStrategy = takeStrategy;
    }

    @Override
    public void put(E e) throws InterruptedException
    {
        while(!offer(e))
        {
            Thread.yield();
        }
    }

    @Override
    public E take() throws InterruptedException
    {
        return takeStrategy.waitFor(new SupplierJDK6<E>() {
            @Override
            public E get() {
                return poll();
            }
        }); // For JVM8 => return takeStrategy.waitFor(this::poll);
    }

    @Override
    public boolean offer(E e)
    {
        boolean offered = super.offer(e);

        if (offered)
        {
            takeStrategy.signal();
        }

        return offered;
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int remainingCapacity()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int drainTo(Collection<? super E> c)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements)
    {
        throw new UnsupportedOperationException();
    }
}
