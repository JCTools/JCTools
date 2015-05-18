package org.jctools.queues.alt;

public interface ConsumerFunction<E> {
    /**
     * @param e not null
     */
    void consume(E e);
}
