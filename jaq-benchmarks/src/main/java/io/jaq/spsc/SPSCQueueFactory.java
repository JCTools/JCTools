package io.jaq.spsc;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedTransferQueue;

public class SPSCQueueFactory {
    public static final int QUEUE_CAPACITY = 1 << Integer.getInteger("pow2.capacity", 15);
    public static final int QUEUE_TYPE = Integer.getInteger("q.type", 0);
    public static Queue<Integer> createQueue() {
        switch (QUEUE_TYPE) {
        case -2:
            return new LinkedTransferQueue<Integer>();
        case -1:
            return new ConcurrentLinkedQueue<Integer>();
        case 0:
            return new InlinedCountersSpscConcurrentArrayQueue<Integer>(QUEUE_CAPACITY);
        case 1:
            return new BQueue<Integer>(QUEUE_CAPACITY);
        case 2:
            return new FFBuffer<Integer>(QUEUE_CAPACITY);
        case 3:
            return new FFBufferWithOfferBatch<Integer>(QUEUE_CAPACITY);
        case 4:
            return new FloatingCountersSpscConcurrentArrayQueue<Integer>(QUEUE_CAPACITY);
        }
        throw new IllegalArgumentException("Type: " + QUEUE_TYPE);
    }

}
