package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import static org.jctools.util.TestUtil.makeParams;

@RunWith(Parameterized.class)
public class QueueSanityTestMpscOfferBelowThreshold extends QueueSanityTest
{
    public QueueSanityTestMpscOfferBelowThreshold(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        MpscArrayQueueOverride<Integer> q = new MpscArrayQueueOverride<Integer>(16);
        list.add(makeParams(0, 1, 8, Ordering.FIFO, q));
        q = new MpscArrayQueueOverride<Integer>(16);
        q.threshold = 12;
        list.add(makeParams(0, 1, 12, Ordering.FIFO, q));
        q = new MpscArrayQueueOverride<Integer>(16);
        q.threshold = 4;
        list.add(makeParams(0, 1, 4, Ordering.FIFO, q));
        return list;
    }

    @Ignore
    public void testPowerOf2Capacity()
    {
    }

    @Ignore
    public void testIterator()
    {
    }
    
    /**
     * This allows us to test the offersIfBelowThreshold through all the offer utilizing threads. The effect should be
     * as if the queue capacity is halved.
     */
    static class MpscArrayQueueOverride<E> extends MpscArrayQueue<E>
    {
        int threshold;

        public MpscArrayQueueOverride(int capacity)
        {
            super(capacity);
            threshold = capacity() / 2;
        }

        @Override
        public boolean offer(E e)
        {
            return super.offerIfBelowThreshold(e, threshold);
        }
    }
}
