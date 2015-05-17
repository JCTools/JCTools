package org.jctools.queues.alt;

public interface ConsumerFunction<E> {
    /**
     * @param e
     * @return true if can consume more, false otherwise
     */
    boolean consume(E e);
}
