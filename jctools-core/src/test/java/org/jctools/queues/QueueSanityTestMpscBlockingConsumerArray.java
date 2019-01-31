package org.jctools.queues;

import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class QueueSanityTestMpscBlockingConsumerArray extends QueueSanityTest
{
    public QueueSanityTestMpscBlockingConsumerArray(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<>();
        list.add(makeQueue(0, 1, 2, Ordering.FIFO, new MpscBlockingConsumerArrayQueue<>(2)));
        list.add(makeQueue(0, 1, SIZE, Ordering.FIFO, new MpscBlockingConsumerArrayQueue<>(SIZE)));
        return list;
    }
}
