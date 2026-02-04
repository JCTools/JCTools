package org.jctools.queues.varhandle;

import org.jctools.queues.QueueSanityTestMpscArray;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.varhandle.unpadded.MpscChunkedVarHandleUnpaddedArrayQueue;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import static org.jctools.util.TestUtil.makeParams;

@RunWith(Parameterized.class)
public class QueueSanityTestMpscChunkedVarHandle extends QueueSanityTestMpscArray
{
    public QueueSanityTestMpscChunkedVarHandle(ConcurrentQueueSpec spec, Queue<Integer> queue)
    {
        super(spec, queue);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeParams(0, 1, 4, Ordering.FIFO, new MpscChunkedVarHandleArrayQueue<>(2, 4)));// MPSC size 1
        list.add(makeParams(0, 1, SIZE, Ordering.FIFO, new MpscChunkedVarHandleArrayQueue<>(8, SIZE)));// MPSC size SIZE
        list.add(makeParams(0, 1, 4096, Ordering.FIFO, new MpscChunkedVarHandleArrayQueue<>(32, 4096)));// Netty recycler defaults
        list.add(makeParams(0, 1, 4, Ordering.FIFO, new MpscChunkedVarHandleUnpaddedArrayQueue<>(2, 4)));// MPSC size 1
        list.add(makeParams(0, 1, SIZE, Ordering.FIFO, new MpscChunkedVarHandleUnpaddedArrayQueue<>(8, SIZE)));// MPSC size SIZE
        list.add(makeParams(0, 1, 4096, Ordering.FIFO, new MpscChunkedVarHandleUnpaddedArrayQueue<>(32, 4096)));// Netty recycler defaults
        return list;
    }
}
