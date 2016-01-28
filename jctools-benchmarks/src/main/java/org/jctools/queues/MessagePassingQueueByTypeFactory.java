package org.jctools.queues;

import java.lang.reflect.Constructor;

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
    @SuppressWarnings("rawtypes")
    private static Class queueClass(String queueType) {
        try {
            return Class.forName("org.jctools.queues."+queueType);
        } catch (ClassNotFoundException e) {
        }
        
        try {
            return Class.forName(queueType);
        } catch (ClassNotFoundException e) {
        }
        throw new IllegalArgumentException("class not found:");
    }
}
