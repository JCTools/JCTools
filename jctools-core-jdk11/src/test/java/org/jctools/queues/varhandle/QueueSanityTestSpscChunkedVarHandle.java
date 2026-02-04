package org.jctools.queues.varhandle;

import org.jctools.queues.QueueSanityTest;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.varhandle.unpadded.SpscChunkedVarHandleUnpaddedArrayQueue;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import static org.jctools.util.TestUtil.makeParams;

@RunWith(Parameterized.class)
public class QueueSanityTestSpscChunkedVarHandle extends QueueSanityTest
{
    public QueueSanityTestSpscChunkedVarHandle(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeParams(1, 1, 16, Ordering.FIFO, new SpscChunkedVarHandleArrayQueue<>(8, 16)));
        list.add(makeParams(1, 1, SIZE, Ordering.FIFO, new SpscChunkedVarHandleArrayQueue<>(8, SIZE)));
        list.add(makeParams(1, 1, 16, Ordering.FIFO, new SpscChunkedVarHandleUnpaddedArrayQueue<>(8, 16)));
        list.add(makeParams(1, 1, SIZE, Ordering.FIFO, new SpscChunkedVarHandleUnpaddedArrayQueue<>(8, SIZE)));
        return list;
    }
}
