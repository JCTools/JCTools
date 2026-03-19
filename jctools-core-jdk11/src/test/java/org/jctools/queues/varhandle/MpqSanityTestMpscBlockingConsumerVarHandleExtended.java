package org.jctools.queues.varhandle;

import org.jctools.queues.MessagePassingBlockingQueue;
import org.jctools.queues.MessagePassingQueue.Supplier;
import org.jctools.queues.MpqSanityTestMpscBlockingConsumerExtended;
import org.jctools.queues.varhandle.unpadded.MpscBlockingConsumerVarHandleUnpaddedArrayQueue;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

public class MpqSanityTestMpscBlockingConsumerVarHandleExtended extends MpqSanityTestMpscBlockingConsumerExtended
{
    public MpqSanityTestMpscBlockingConsumerVarHandleExtended(Supplier<MessagePassingBlockingQueue<Object>> factory)
    {
        super(factory);
    }

    @Parameterized.Parameters
    public static Collection<Supplier<MessagePassingBlockingQueue<Object>>> parameters()
    {
        ArrayList<Supplier<MessagePassingBlockingQueue<Object>>> list = new ArrayList<>();
        list.add(() -> new MpscBlockingConsumerVarHandleArrayQueue<>(1024));
        list.add(() -> new MpscBlockingConsumerVarHandleUnpaddedArrayQueue<>(1024));
        return list;
    }
}
