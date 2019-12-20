package org.jctools.queues;

import org.junit.Assert;
import org.junit.Test;

import static org.hamcrest.Matchers.is;

public class QueueSanityTestSpscGrowableExtended
{
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
