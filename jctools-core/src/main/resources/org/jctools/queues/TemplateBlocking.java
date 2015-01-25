import org.jctools.queues.*;

import org.jctools.queues.takestrategy.SCParkTakeStrategy;
import org.jctools.queues.takestrategy.SupplierJDK6;
import org.jctools.queues.takestrategy.TakeStrategy;

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class {{blockingQueueClassName}}<E> extends {{queueClassName}}<E> implements BlockingQueue<E>
{
    private final TakeStrategy<E> takeStrategy = new SCParkTakeStrategy<E>();
    private final SupplierJDK6<E> poller = new SupplierJDK6<E>() {
        @Override
        public E get() {
            return poll();
        }
    };

    public {{blockingQueueClassName}}()
    {
        super({{capacity}});
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
        return takeStrategy.waitFor(poller); // For JVM8 => return takeStrategy.waitFor(this::poll);
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
