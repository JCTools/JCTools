package org.jctools.queues.blocking;

import org.jctools.queues.spec.ConcurrentQueueSpec;

import java.util.Queue;

public interface PutStrategy<E>
{
    void waitOffer(Queue<E> q, E e) throws InterruptedException;
    void signal();
    boolean supportsSpec(ConcurrentQueueSpec qs);
}
