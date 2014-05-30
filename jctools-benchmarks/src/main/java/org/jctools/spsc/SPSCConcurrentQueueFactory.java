package org.jctools.spsc;

import org.jctools.ConcurrentQueue;
import org.jctools.ConcurrentQueueFactory;
import org.jctools.mpmc.MpmcConcurrentQueue;
import org.jctools.mpmc.MpmcConcurrentQueueCq;
import org.jctools.mpmc.MpmcConcurrentQueueStateMarkers;
import org.jctools.mpsc.MpscCompoundQueue;
import org.jctools.mpsc.MpscConcurrentQueue;
import org.jctools.mpsc.MpscOnSpscQueue;
import org.jctools.spmc.SpmcConcurrentQueue;
import org.jctools.spsc.FFBufferWithOfferBatch;
import org.jctools.spsc.FFBufferWithOfferBatchCq;
import org.jctools.spsc.SpscLinkedQueue;

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
