package org.jctools.queues.blocking;
import org.jctools.queues.*;
import org.jctools.queues.blocking.*;

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class {{blockingQueueClassName}}<E> extends {{queueClassName}}<E> implements BlockingQueue<E>
{
    private final {{TakeStrategy}}<E> takeStrategy = new {{TakeStrategy}}<E>();
    private final {{PutStrategy}} putStrategy = new {{PutStrategy}}();

    public {{blockingQueueClassName}}(int capacity)
    {
        super(capacity);
    }

    @Override
    public void put(E e) throws InterruptedException
    {
        putStrategy.waitOffer(this, e);
    }

    @Override
    public E take() throws InterruptedException
    {
        return takeStrategy.waitPoll(this);
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
    public E poll()
        {
            E e = super.poll();

            if (e!=null)
            {
                putStrategy.signal();
            }

            return e;
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
        int count = 0;

        E e;
        while((e = poll()) != null)
        {
            c.add(e);
            count++;
        }

        return count;
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements)
    {
        int count = 0;

        E e;
        while(((e = poll()) != null) && count<maxElements)
        {
            c.add(e);
            count++;
        }

        return count;
    }
}
