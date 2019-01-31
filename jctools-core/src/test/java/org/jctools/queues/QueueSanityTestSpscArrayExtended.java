package org.jctools.queues;

import org.junit.Test;

import static org.hamcrest.Matchers.*;
import static org.jctools.queues.matchers.Matchers.emptyAndZeroSize;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class QueueSanityTestSpscArrayExtended
{
    @Test
    public void shouldWorkAfterWrap()
    {
        // Arrange
        final SpscArrayQueue<Object> q = new SpscArrayQueue<Object>(1024);
        // starting point for empty queue at max long, next offer will wrap the producerIndex
        q.soConsumerIndex(Long.MAX_VALUE);
        q.soProducerIndex(Long.MAX_VALUE);
        q.producerLimit = Long.MAX_VALUE;
        // valid starting point
        assertThat(q, emptyAndZeroSize());

        // Act
        // assert offer is successful
        final Object e = new Object();
        assertTrue(q.offer(e));
        // size is computed correctly after wrap
        assertThat(q, not(emptyAndZeroSize()));
        assertThat(q, hasSize(1));

        // now consumer index wraps
        final Object poll = q.poll();
        assertThat(poll, sameInstance(e));
        assertThat(q, emptyAndZeroSize());

        // let's go again
        assertTrue(q.offer(e));
        assertThat(q, not(emptyAndZeroSize()));

        final Object poll2 = q.poll();
        assertThat(poll2, sameInstance(e));
        assertThat(q, emptyAndZeroSize());
    }
}
