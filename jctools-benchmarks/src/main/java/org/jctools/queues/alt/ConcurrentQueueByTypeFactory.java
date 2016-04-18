package org.jctools.queues.alt;


public class ConcurrentQueueByTypeFactory {
    public static final int QUEUE_CAPACITY = 1 << Integer.getInteger("pow2.capacity", 17);
    public static final int QUEUE_TYPE = Integer.getInteger("q.type", 30);
    public static ConcurrentQueue<Integer> createQueue() {
        switch (QUEUE_TYPE) {
        case -1:
            return new ConcurrentQueueFactory.GenericQueue<Integer>();
//        case 3:
//            return new SpscArrayQueue<Integer>(QUEUE_CAPACITY);
        case 30:
            return new SpscArrayConcurrentQueue<Integer>(QUEUE_CAPACITY);
//        case 31:
//            return new SpscLinkedQueue<Integer>();
//        case 5:
//            return new SpmcArrayQueue<Integer>(QUEUE_CAPACITY);
//        case 6:
//            return new MpscArrayConcurrentQueue<Integer>(QUEUE_CAPACITY);
//        case 61:
//            return new MpscCompoundQueue<Integer>(QUEUE_CAPACITY);
//        case 62:
//            return new MpscOnSpscQueue<Integer>(QUEUE_CAPACITY);
//        case 7:
//            return new MpmcArrayQueue<Integer>(QUEUE_CAPACITY);
        case 70:
            return new MpmcArrayConcurrentQueue<Integer>(QUEUE_CAPACITY);

        }
        throw new IllegalArgumentException("Type: " + QUEUE_TYPE);
    }

}
