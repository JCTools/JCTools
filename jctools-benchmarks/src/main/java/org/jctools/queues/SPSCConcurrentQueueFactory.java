package org.jctools.queues;

import org.jctools.queues.SpscConcurrentQueue;
import org.jctools.queues.MpmcConcurrentQueue;
import org.jctools.queues.MpmcConcurrentQueueStateMarkers;
import org.jctools.queues.MpscCompoundQueue;
import org.jctools.queues.MpscConcurrentQueue;
import org.jctools.queues.MpscOnSpscQueue;
import org.jctools.queues.SpmcConcurrentQueue;
import org.jctools.queues.SpscLinkedQueue;
import org.jctools.queues.alt.ConcurrentQueue;
import org.jctools.queues.alt.ConcurrentQueueFactory;
import org.jctools.queues.alt.FFBufferWithOfferBatchCq;
import org.jctools.queues.alt.MpmcConcurrentQueueCq;

public class SPSCConcurrentQueueFactory {
    public static final int QUEUE_CAPACITY = 1 << Integer.getInteger("pow2.capacity", 15);
    public static final int QUEUE_TYPE = Integer.getInteger("q.type", 0);
    public static ConcurrentQueue<Integer> createQueue() {
        switch (QUEUE_TYPE) {
        case -1:
            return new ConcurrentQueueFactory.GenericQueue<Integer>();
        case 3:
            return new SpscConcurrentQueue<Integer>(QUEUE_CAPACITY);
        case 30:
            return new FFBufferWithOfferBatchCq<Integer>(QUEUE_CAPACITY);
        case 31:
            return new SpscLinkedQueue<Integer>();
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
        case 71:
            return new MpmcConcurrentQueueStateMarkers<Integer>(QUEUE_CAPACITY);
        case 70:
            return new MpmcConcurrentQueueCq<Integer>(QUEUE_CAPACITY);
            
        }
        throw new IllegalArgumentException("Type: " + QUEUE_TYPE);
    }

}
