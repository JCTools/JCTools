package org.jctools.queues.varhandle;

import org.jctools.queues.varhandle.MpscBlockingConsumerVarHandleArrayQueue;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jctools.queues.varhandle.utils.TestUtils.makeSpec;
import static org.junit.Assert.*;

public class MpqSanityTestMpscBlockingConsumerVarHandleExtended
{
    private MpscBlockingConsumerVarHandleArrayQueue<Integer> buffy;

    @Before
    public void setUp() throws Exception
    {
        buffy = new MpscBlockingConsumerVarHandleArrayQueue<>(16);
    }

    @Test(timeout = 1000)
    public void testPoll() throws InterruptedException
    {
        buffy.offer(1);
        assertEquals(1, buffy.poll().intValue());
    }

    @Test(timeout = 1000)
    public void testTimedPoll() throws InterruptedException
    {
        buffy.offer(1);
        assertEquals(1, buffy.poll(100, TimeUnit.MILLISECONDS).intValue());
    }

    @Test(timeout = 1000)
    public void testRelaxedPoll() throws InterruptedException
    {
        buffy.offer(1);
        assertEquals(1, buffy.relaxedPoll().intValue());
    }

    @Test(timeout = 1000)
    public void testTake() throws InterruptedException
    {
        buffy.offer(1);
        assertEquals(1, buffy.take().intValue());
    }

    @Test(timeout = 1000)
    public void testRelaxedPeek() throws InterruptedException
    {
        assertNull(buffy.relaxedPeek());
        buffy.offer(1);
        assertEquals(1, buffy.relaxedPeek().intValue());
    }

    @Test(timeout = 1000)
    public void testDrain() throws InterruptedException
    {
        for(int i=0;i<10;i++) {
            buffy.offer(i);
        }
        assertEquals(10, buffy.drain(e -> {}));
    }

    @Test(timeout = 1000)
    public void testDrainWithLimit() throws InterruptedException
    {
        for(int i=0;i<10;i++) {
            buffy.offer(i);
        }
        assertEquals(3, buffy.drain(e -> {}, 3));
    }

    @Test(timeout = 1000)
    public void testFill() throws InterruptedException
    {
        AtomicBoolean stopCondition = new AtomicBoolean();
        buffy.fill(() -> stopCondition.get() ? null : 1);
        assertEquals(16, buffy.size());
    }

    @Test(timeout = 1000)
    public void testFillWithLimit() throws InterruptedException
    {
        AtomicBoolean stopCondition = new AtomicBoolean();
        buffy.fill(() -> stopCondition.get() ? null : 1, 3);
        assertEquals(3, buffy.size());
    }
}
