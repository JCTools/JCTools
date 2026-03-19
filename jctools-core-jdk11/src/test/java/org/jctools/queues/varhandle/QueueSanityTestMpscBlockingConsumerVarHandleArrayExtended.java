package org.jctools.queues.varhandle;

import org.jctools.queues.MessagePassingBlockingQueue;
import org.jctools.queues.QueueSanityTestMpscBlockingConsumerArrayExtended;
import org.jctools.queues.varhandle.unpadded.MpscBlockingConsumerVarHandleUnpaddedArrayQueue;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.IntFunction;

public class QueueSanityTestMpscBlockingConsumerVarHandleArrayExtended
    extends QueueSanityTestMpscBlockingConsumerArrayExtended
{
    public QueueSanityTestMpscBlockingConsumerVarHandleArrayExtended(IntFunction<MessagePassingBlockingQueue<Object>> factory)
    {
        super(factory);
    }

    @Parameterized.Parameters
    public static Collection<IntFunction<MessagePassingBlockingQueue<Object>>> parameters()
    {
        ArrayList<IntFunction<MessagePassingBlockingQueue<Object>>> list = new ArrayList<>();
        list.add(MpscBlockingConsumerVarHandleArrayQueue::new);
        list.add(MpscBlockingConsumerVarHandleUnpaddedArrayQueue::new);
        return list;
    }
}
