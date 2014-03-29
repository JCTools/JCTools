package io.jaq.spsc;

import io.jaq.ConcurrentQueue;
import io.jaq.ConcurrentQueueFactory;
import io.jaq.mpmc.MpmcConcurrentQueue;
import io.jaq.mpmc.MpmcConcurrentQueueCq;
import io.jaq.mpsc.MpscCompoundQueue;
import io.jaq.mpsc.MpscConcurrentQueue;
import io.jaq.mpsc.MpscOnSpscQueue;
import io.jaq.spmc.SpmcConcurrentQueue;

public class SPSCConcurrentQueueFactory {
    public static final int QUEUE_CAPACITY = 1 << Integer.getInteger("pow2.capacity", 15);
    public static final int QUEUE_TYPE = Integer.getInteger("q.type", 0);
    public static ConcurrentQueue<Integer> createQueue() {
        switch (QUEUE_TYPE) {
        case -1:
            return new ConcurrentQueueFactory.GenericQueue<Integer>();
        case 3:
            return new FFBufferWithOfferBatch<Integer>(QUEUE_CAPACITY);
        case 30:
            return new FFBufferWithOfferBatchCq<Integer>(QUEUE_CAPACITY);
        case 5:
            return new SpmcConcurrentQueue<Integer>(QUEUE_CAPACITY);
        case 6:
            return new MpscConcurrentQueue<Integer>(QUEUE_CAPACITY);
        case 61:
            return new MpscCompoundQueue<Integer>(QUEUE_CAPACITY);
        case 62:
            return new MpscOnSpscQueue<Integer>(QUEUE_CAPACITY);
        case 7:
            return new MpmcConcurrentQueue<Integer>(QUEUE_CAPACITY);
        case 70:
            return new MpmcConcurrentQueueCq<Integer>(QUEUE_CAPACITY);
        }
        throw new IllegalArgumentException("Type: " + QUEUE_TYPE);
    }

}
