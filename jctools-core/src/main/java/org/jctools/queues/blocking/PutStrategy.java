package org.jctools.queues.blocking;

import org.jctools.queues.spec.ConcurrentQueueSpec;

import java.util.Queue;

public interface PutStrategy<E>
{
    void backoffOffer(Queue<E> q, E e);
    void signal();
    boolean supportsSpec(ConcurrentQueueSpec qs);
}
