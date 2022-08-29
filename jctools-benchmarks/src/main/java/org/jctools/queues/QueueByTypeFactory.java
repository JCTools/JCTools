package org.jctools.queues;

import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedTransferQueue;

import org.jctools.queues.blocking.BlockingQueueFactory;
import org.jctools.queues.spec.ConcurrentQueueSpec;

public class QueueByTypeFactory {
    public static final int QUEUE_CAPACITY = 1 << Integer.getInteger("pow2.capacity", 17);
    public static final int QUEUE_TYPE = Integer.getInteger("q.type", 0);
    public static <T> Queue<T> createQueue() {
        final int queueCapacity = QUEUE_CAPACITY;
        return createQueue(queueCapacity);
    }
    public static <T> Queue<T> createQueue(final int queueCapacity) {
        int queueType = QUEUE_TYPE;
        return createQueue(queueType, queueCapacity);
    }
    public static <T> Queue<T> createQueue(int queueType, final int queueCapacity) {
        switch (queueType) {
        case -99:
            return new ArrayDeque<T>(queueCapacity);
        case -3:
            return new ArrayBlockingQueue<T>(queueCapacity);
        case -2:
            return new LinkedTransferQueue<T>();
        case -1:
            return new ConcurrentLinkedQueue<T>();
        case 0:
            return new InlinedCountersSpscConcurrentArrayQueue<T>(queueCapacity);
        case 10:
            return new BQueue<T>(queueCapacity);
        case 20:
            return new FFBuffer<T>(queueCapacity);
        case 3:
            return new SpscArrayQueue<T>(queueCapacity);
        case 308:
            return BlockingQueueFactory.newBlockingQueue(ConcurrentQueueSpec.createBoundedSpsc(queueCapacity));
        case 31:
            return new SpscLinkedQueue<T>();
        case 32:
            return new SpscGrowableArrayQueue<T>(queueCapacity);
        case 40:
            return new FloatingCountersSpscConcurrentArrayQueue<T>(queueCapacity);
        case 5:
            return new SpmcArrayQueue<T>(queueCapacity);
        case 508:
            return BlockingQueueFactory.newBlockingQueue(ConcurrentQueueSpec.createBoundedSpmc(queueCapacity));
        case 6:
            return new MpscArrayQueue<T>(queueCapacity);
        case 608:
            return BlockingQueueFactory.newBlockingQueue(ConcurrentQueueSpec.createBoundedMpsc(queueCapacity));
        case 61:
            return new MpscCompoundQueue<T>(queueCapacity);
        case 62:
            return new MpscOnSpscQueue<T>(queueCapacity);
        case 63:
            return new MpscLinkedQueue<T>();
        case 7:
            return new MpmcArrayQueue<T>(queueCapacity);
        case 708:
            return BlockingQueueFactory.newBlockingQueue(ConcurrentQueueSpec.createBoundedMpmc(queueCapacity));
        case 71:
            return new MpmcConcurrentQueueStateMarkers<T>(queueCapacity);
        }
        throw new IllegalArgumentException("Type: " + queueType);
    }
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T> Queue<T> createQueue(String queueType, final int queueCapacity) {
        try {
            int qType = Integer.valueOf(queueType);
            return createQueue(qType, queueCapacity);
        }
        catch (NumberFormatException e) {
        }
        Class qClass = queueClass(queueType);
        Constructor constructor;
        Exception ex;
        try {
            constructor = qClass.getConstructor(Integer.TYPE);
            return (Queue<T>) constructor.newInstance(queueCapacity);
        } catch (Exception e) {
            ex = e;
        }
        try {
            constructor = qClass.getConstructor();
            return (Queue<T>) constructor.newInstance();
        } catch (Exception e) {
            ex = e;
        }
        throw new IllegalArgumentException("Failed to construct queue:"+qClass.getName(), ex);
    }
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T> Queue<T> createQueue(String queueType, final int chunkSize, final int queueCapacity) {
        Class qClass = queueClass(queueType);
        if (!(BaseMpscLinkedArrayQueue.class.isAssignableFrom(qClass) ||
              BaseSpscLinkedArrayQueue.class.isAssignableFrom(qClass) ||
              MpUnboundedXaddArrayQueue.class.isAssignableFrom(qClass)) )
            throw new IllegalArgumentException("Failed to construct queue, does not expect dual param constructor: " + qClass.getName());
        Constructor constructor;
        Exception ex;
        try {
            constructor = qClass.getConstructor(Integer.TYPE, Integer.TYPE);
            return (Queue<T>) constructor.newInstance(chunkSize, queueCapacity);
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
            return Class.forName("org.jctools.queues.atomic."+queueType);
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

    public static <T> Queue<T> buildQ(String qType, String qCapacity) {
        try {
            int capacity = Integer.valueOf(qCapacity);
            return createQueue(qType, capacity);
        }
        catch (Exception e) {}

        try {
            String[] args = qCapacity.split("\\.");
            int chunk = Integer.valueOf(args[0]);
            int capacity = Integer.valueOf(args[1]);
            return createQueue(qType, chunk, capacity);
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse qCapacity",e);
        }
    }
}
