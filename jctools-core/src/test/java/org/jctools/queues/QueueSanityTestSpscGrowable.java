package org.jctools.queues;

import org.jctools.queues.atomic.SpscGrowableAtomicArrayQueue;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.unpadded.SpscGrowableUnpaddedArrayQueue;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import static org.hamcrest.Matchers.is;
import static org.jctools.util.TestUtil.makeParams;

@RunWith(Parameterized.class)
public class QueueSanityTestSpscGrowable extends QueueSanityTest
{

    public QueueSanityTestSpscGrowable(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeParams(1, 1, 16, Ordering.FIFO, new SpscGrowableArrayQueue<>(8, 16)));
        list.add(makeParams(1, 1, SIZE, Ordering.FIFO, new SpscGrowableArrayQueue<>(8, SIZE)));
        list.add(makeParams(1, 1, 16, Ordering.FIFO, new SpscGrowableAtomicArrayQueue<>(8, 16)));
        list.add(makeParams(1, 1, SIZE, Ordering.FIFO, new SpscGrowableAtomicArrayQueue<>(8, SIZE)));
        list.add(makeParams(1, 1, 16, Ordering.FIFO, new SpscGrowableUnpaddedArrayQueue<>(8, 16)));
        list.add(makeParams(1, 1, SIZE, Ordering.FIFO, new SpscGrowableUnpaddedArrayQueue<>(8, SIZE)));
        return list;
    }

    @Test
    public void testSizeNeverExceedCapacity()
    {
        final SpscGrowableArrayQueue<Integer> q = new SpscGrowableArrayQueue<>(8, 16);
        final Integer v = 0;
        final int capacity = q.capacity();
        for (int i = 0; i < capacity; i++)
        {
            Assert.assertTrue(q.offer(v));
        }
        Assert.assertFalse(q.offer(v));
        Assert.assertThat(q.size(), is(capacity));
        for (int i = 0; i < 6; i++)
        {
            Assert.assertEquals(v, q.poll());
        }
        //the consumer is left in the chunk previous the last and biggest one
        Assert.assertThat(q.size(), is(capacity - 6));
        for (int i = 0; i < 6; i++)
        {
            q.offer(v);
        }
        Assert.assertThat(q.size(), is(capacity));
        Assert.assertFalse(q.offer(v));
    }
}
