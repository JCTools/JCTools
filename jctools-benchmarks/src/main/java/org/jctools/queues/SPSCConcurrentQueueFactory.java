package org.jctools.queues;

import org.jctools.queues.alt.ConcurrentQueue;
import org.jctools.queues.alt.ConcurrentQueueFactory;
import org.jctools.queues.alt.MpmcArrayConcurrentQueue;
import org.jctools.queues.alt.SpscArrayConcurrentQueue;

public class SPSCConcurrentQueueFactory {
    public static final int QUEUE_CAPACITY = 1 << Integer.getInteger("pow2.capacity", 15);
    public static final int QUEUE_TYPE = Integer.getInteger("q.type", 0);
    public static ConcurrentQueue<Integer> createQueue() {
        switch (QUEUE_TYPE) {
        case -1:
            return new ConcurrentQueueFactory.GenericQueue<Integer>();
//        case 3:
//            return new SpscArrayQueue<Integer>(QUEUE_CAPACITY);
        case 30:
            return new SpscArrayConcurrentQueue<Integer>(QUEUE_CAPACITY);
        case 31:
            return new SpscLinkedQueue<Integer>();
//        case 5:
//            return new SpmcArrayQueue<Integer>(QUEUE_CAPACITY);
//        case 6:
//            return new MpscArrayQueue<Integer>(QUEUE_CAPACITY);
//        case 61:
//            return new MpscCompoundQueue<Integer>(QUEUE_CAPACITY);
//        case 62:
//            return new MpscOnSpscQueue<Integer>(QUEUE_CAPACITY);
//        case 7:
//            return new MpmcArrayQueue<Integer>(QUEUE_CAPACITY);
        case 71:
            return new MpmcConcurrentQueueStateMarkers<Integer>(QUEUE_CAPACITY);
        case 70:
            return new MpmcArrayConcurrentQueue<Integer>(QUEUE_CAPACITY);
            
        }
        throw new IllegalArgumentException("Type: " + QUEUE_TYPE);
    }

}
