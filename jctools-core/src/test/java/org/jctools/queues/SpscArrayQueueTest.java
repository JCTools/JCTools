package org.jctools.queues;

import static org.junit.Assert.*;

import org.junit.Test;

public class SpscArrayQueueTest {
    @Test
    public void shouldWorkAfterWrap(){
        SpscArrayQueue<Object> q = new SpscArrayQueue<Object>(1024);
        // starting point for empty queue at max long, next offer will wrap the producerIndex
        q.consumerIndex = Long.MAX_VALUE;
        q.producerIndex = Long.MAX_VALUE;
        q.producerLookAhead = Long.MAX_VALUE;
        // valid starting point
        assertEquals(0, q.size());
        assertTrue(q.isEmpty());
        // assert offer is successful
        Object e = new Object();
        assertTrue(q.offer(e));
        // size is computed correctly after wrap
        assertEquals(1, q.size());
        assertTrue(!q.isEmpty());
        
        // now consumer index wraps
        assertEquals(e, q.poll());
        assertEquals(0, q.size());
        assertTrue(q.isEmpty());
        
        // let's go again
        assertTrue(q.offer(e));
        assertEquals(1, q.size());
        assertTrue(!q.isEmpty());
        assertEquals(e, q.poll());
        assertEquals(0, q.size());
        assertTrue(q.isEmpty());
    }
}
