package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;

import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedTransferQueue;

public class QueueByTypeFactory {
    public static final int QUEUE_CAPACITY = 1 << Integer.getInteger("pow2.capacity", 17);
    public static final int QUEUE_TYPE = Integer.getInteger("q.type", 0);
    public static Queue<Integer> createQueue() {
        final int queueCapacity = QUEUE_CAPACITY;
        return createQueue(queueCapacity);
    }
    public static Queue<Integer> createQueue(final int queueCapacity) {
        int queueType = QUEUE_TYPE;
        return createQueue(queueType, queueCapacity);
    }
    public static Queue<Integer> createQueue(int queueType, final int queueCapacity) {
        switch (queueType) {
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
            case 308:
                return BlockingQueueFactory.newBlockingQueue(ConcurrentQueueSpec.createBoundedSpsc(queueCapacity));
        case 31:
            return new SpscLinkedQueue<Integer>();
        case 32:
            return new SpscGrowableArrayQueue<Integer>(queueCapacity);
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
            return new MpscLinkedQueue8<Integer>();
        case 7:
            return new MpmcArrayQueue<Integer>(queueCapacity);
            case 708:
                return BlockingQueueFactory.newBlockingQueue(ConcurrentQueueSpec.createBoundedMpmc(queueCapacity));
        case 71:
            return new MpmcConcurrentQueueStateMarkers<Integer>(queueCapacity);
        }
        throw new IllegalArgumentException("Type: " + queueType);
    }
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static Queue<Integer> createQueue(String queueType, final int queueCapacity) {
        Class qClass = queueClass(queueType);
        Constructor constructor;
        Exception ex;
        try {
            constructor = qClass.getConstructor(Integer.TYPE);
            return (Queue<Integer>) constructor.newInstance(queueCapacity);
        } catch (Exception e) {
            ex = e;
        }
        try {
            constructor = qClass.getConstructor();
            return (Queue<Integer>) constructor.newInstance();
        } catch (Exception e) {
            ex = e;
        }
        throw new IllegalArgumentException("Failed to construct queue:"+qClass.getName(), ex);
    }
    @SuppressWarnings("rawtypes")
    private static Class queueClass(String queueType) {
        try {
            return Class.forName("org.jctools.queues."+queueType);
        } catch (ClassNotFoundException e) {
        }
        
        try {
            return Class.forName("java.util."+queueType);
        } catch (ClassNotFoundException e) {
        }
        try {
            return Class.forName("java.util.concurrent."+queueType);
        } catch (ClassNotFoundException e) {
        }
        try {
            return Class.forName(queueType);
        } catch (ClassNotFoundException e) {
        }
        throw new IllegalArgumentException("class not found:");
    }
}
