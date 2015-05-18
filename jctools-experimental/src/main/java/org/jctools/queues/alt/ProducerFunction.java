package org.jctools.queues.alt;

public interface ProducerFunction<E> {
    /**
     * @return null values are not allowed
     */
    E produce();
}
