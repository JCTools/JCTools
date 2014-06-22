package org.jctools.queues;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedTransferQueue;

public class TypeQueueFactory {
    public static final int QUEUE_CAPACITY = 1 << Integer.getInteger("pow2.capacity", 17);
    public static final int QUEUE_TYPE = Integer.getInteger("q.type", 0);
    public static Queue<Integer> createQueue() {
        final int queueCapacity = QUEUE_CAPACITY;
        return createQueue(queueCapacity);
    }
    public static Queue<Integer> createQueue(final int queueCapacity) {
        switch (QUEUE_TYPE) {
        case -99:
            return new ArrayDeque<Integer>(queueCapacity);
        case -3:
            return new ArrayBlockingQueue<Integer>(queueCapacity);
        case -2:
            return new LinkedTransferQueue<Integer>();
        case -1:
            return new ConcurrentLinkedQueue<Integer>();
        case 0:
            return new InlinedCountersSpscConcurrentArrayQueue<Integer>(queueCapacity);
        case 10:
            return new BQueue<Integer>(queueCapacity);
        case 20:
            return new FFBuffer<Integer>(queueCapacity);
        case 3:
            return new SpscArrayQueue<Integer>(queueCapacity);
        case 31:
            return new SpscLinkedQueue<Integer>();
        case 40:
            return new FloatingCountersSpscConcurrentArrayQueue<Integer>(queueCapacity);
        case 5:
            return new SpmcArrayQueue<Integer>(queueCapacity);
        case 6:
            return new MpscArrayQueue<Integer>(queueCapacity);
        case 61:
            return new MpscCompoundQueue<Integer>(queueCapacity);
        case 62:
            return new MpscOnSpscQueue<Integer>(queueCapacity);
        case 63:
            return new MpscLinkedQueue<Integer>();
        case 7:
            return new MpmcArrayQueue<Integer>(queueCapacity);
        case 71:
            return new MpmcConcurrentQueueStateMarkers<Integer>(queueCapacity);
        }
        throw new IllegalArgumentException("Type: " + QUEUE_TYPE);
    }

}
