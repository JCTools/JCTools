package org.jctools.queues;

import java.lang.reflect.Constructor;
import java.util.Queue;

import static org.jctools.queues.QueueByTypeFactory.queueClass;

public class MessagePassingQueueByTypeFactory {
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T> MessagePassingQueue<T> createQueue(String queueType, final int queueCapacity) {
        Class qClass = queueClass(queueType);
        Constructor constructor;
        Exception ex;
        try {
            constructor = qClass.getConstructor(Integer.TYPE);
            return (MessagePassingQueue<T>) constructor.newInstance(queueCapacity);
        } catch (Exception e) {
            ex = e;
        }
        try {
            constructor = qClass.getConstructor();
            return (MessagePassingQueue<T>) constructor.newInstance();
        } catch (Exception e) {
            ex = e;
        }
        throw new IllegalArgumentException("Failed to construct queue:"+qClass.getName(), ex);
    }
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T> MessagePassingQueue<T> createQueue(String queueType, final int chunk, final int queueCapacity) {
        Class qClass = queueClass(queueType);
        Constructor constructor;
        Exception ex;
        try {
            constructor = qClass.getConstructor(Integer.TYPE, Integer.TYPE);
            return (MessagePassingQueue<T>) constructor.newInstance(chunk, queueCapacity);
        } catch (Exception e) {
            ex = e;
        }
        try {
            constructor = qClass.getConstructor();
            return (MessagePassingQueue<T>) constructor.newInstance();
        } catch (Exception e) {
            ex = e;
        }
        throw new IllegalArgumentException("Failed to construct queue:"+qClass.getName(), ex);
    }
    @SuppressWarnings("rawtypes")

    public static <T> MessagePassingQueue<T> buildQ(String qType, String qCapacity) {
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
