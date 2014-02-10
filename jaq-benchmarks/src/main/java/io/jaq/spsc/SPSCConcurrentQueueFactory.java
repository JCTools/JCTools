package io.jaq.spsc;

import io.jaq.ConcurrentQueue;

public class SPSCConcurrentQueueFactory {
    public static final int QUEUE_CAPACITY = 1 << Integer.getInteger("pow2.capacity", 15);
    public static final int QUEUE_TYPE = Integer.getInteger("q.type", 0);
    public static ConcurrentQueue<Integer> createQueue() {
        switch (QUEUE_TYPE) {
        case 3:
            return new FFBufferWithOfferBatch<Integer>(QUEUE_CAPACITY);
        case 30:
            return new FFBufferWithOfferBatchCq<Integer>(QUEUE_CAPACITY);
//        case 2:
//            return new SpmcConcurrentQueue<Integer>(QUEUE_CAPACITY);
//        case 3:
//            return new MpscConcurrentQueue<Integer>(QUEUE_CAPACITY);
        }
        throw new IllegalArgumentException("Type: " + QUEUE_TYPE);
    }

}
