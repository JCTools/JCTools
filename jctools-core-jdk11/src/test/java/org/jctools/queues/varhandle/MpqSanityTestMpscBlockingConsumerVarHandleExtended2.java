package org.jctools.queues.varhandle;

import org.jctools.queues.MessagePassingBlockingQueue;
import org.jctools.queues.MessagePassingQueue.Supplier;
import org.jctools.queues.MpqSanityTestMpscBlockingConsumerExtended;
import org.jctools.queues.MpscBlockingConsumerArrayQueue;
import org.jctools.queues.atomic.MpscBlockingConsumerAtomicArrayQueue;
import org.jctools.queues.atomic.unpadded.MpscBlockingConsumerAtomicUnpaddedArrayQueue;
import org.jctools.queues.unpadded.MpscBlockingConsumerUnpaddedArrayQueue;
import org.jctools.queues.varhandle.unpadded.MpscBlockingConsumerVarHandleUnpaddedArrayQueue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MpqSanityTestMpscBlockingConsumerVarHandleExtended2 extends MpqSanityTestMpscBlockingConsumerExtended
{
    public MpqSanityTestMpscBlockingConsumerVarHandleExtended2(Supplier<MessagePassingBlockingQueue<Object>> factory)
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
