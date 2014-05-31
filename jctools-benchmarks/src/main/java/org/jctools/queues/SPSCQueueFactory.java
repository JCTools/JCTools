package org.jctools.queues;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedTransferQueue;

import org.jctools.queues.BQueue;
import org.jctools.queues.FFBuffer;
import org.jctools.queues.SpscConcurrentQueue;
import org.jctools.queues.FloatingCountersSpscConcurrentArrayQueue;
import org.jctools.queues.InlinedCountersSpscConcurrentArrayQueue;
import org.jctools.queues.MpmcConcurrentQueue;
import org.jctools.queues.MpmcConcurrentQueueStateMarkers;
import org.jctools.queues.MpscCompoundQueue;
import org.jctools.queues.MpscConcurrentQueue;
import org.jctools.queues.MpscOnSpscQueue;
import org.jctools.queues.SpmcConcurrentQueue;
import org.jctools.queues.SpscLinkedQueue;

public class SPSCQueueFactory {
    public static final int QUEUE_CAPACITY = 1 << Integer.getInteger("pow2.capacity", 15);
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
            return new SpscConcurrentQueue<Integer>(queueCapacity);
        case 31:
            return new SpscLinkedQueue<Integer>();
        case 40:
            return new FloatingCountersSpscConcurrentArrayQueue<Integer>(queueCapacity);
        case 5:
            return new SpmcConcurrentQueue<Integer>(queueCapacity);
        case 6:
            return new MpscConcurrentQueue<Integer>(queueCapacity);
        case 61:
            return new MpscCompoundQueue<Integer>(queueCapacity);
        case 62:
            return new MpscOnSpscQueue<Integer>(queueCapacity);
        case 7:
            return new MpmcConcurrentQueue<Integer>(queueCapacity);
        case 71:
            return new MpmcConcurrentQueueStateMarkers<Integer>(queueCapacity);
        }
        throw new IllegalArgumentException("Type: " + QUEUE_TYPE);
    }

}
